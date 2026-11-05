package com.rbc.fogwall.dashboard.controller;

import com.rbc.fogwall.dashboard.export.ExportFields;
import com.rbc.fogwall.dashboard.export.ExportFormat;
import com.rbc.fogwall.dashboard.export.ExportSource;
import com.rbc.fogwall.dashboard.export.GrantSnapshot;
import com.rbc.fogwall.db.FetchStore;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.ScmApiActionStore;
import com.rbc.fogwall.db.model.FetchActivityQuery;
import com.rbc.fogwall.db.model.PushQuery;
import com.rbc.fogwall.db.model.RecordCursor;
import com.rbc.fogwall.db.model.ScmApiActionQuery;
import com.rbc.fogwall.permission.AccessChangeService;
import com.rbc.fogwall.permission.ChangeOrigin;
import com.rbc.fogwall.permission.MembershipChangeEvent;
import com.rbc.fogwall.permission.MembershipChangeQuery;
import com.rbc.fogwall.permission.PermissionChangeEvent;
import com.rbc.fogwall.permission.PermissionChangeQuery;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.permission.RoleChangeEvent;
import com.rbc.fogwall.permission.RoleChangeQuery;
import com.rbc.fogwall.user.ReadOnlyUserStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The access change record — grants and revocations, group membership, and roles — and the current grants, for auditors
 * and admins; and an export of every record type at once. The shared filters mean what they mean on every other record
 * listing — {@code user} is who made the change — and {@code subject} is whose access it is.
 */
@Tag(name = "Audit", description = "Access change records and evidence export — require ROLE_AUDITOR or ROLE_ADMIN")
@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
@RequiredArgsConstructor
public class AuditController {

    /** Rows read per store call while filtering permission changes by repository. */
    static final int REPO_SCAN_BATCH = 500;

    /** On a snapshot listing, the number of rows the filters select. */
    static final String TOTAL_COUNT_HEADER = "X-Total-Count";

    /** Every record type and its exported fields, in the order an export of everything writes them. */
    private static final Map<String, List<String>> FIELDS = fields();

    static final List<String> RECORD_TYPES = List.copyOf(FIELDS.keySet());

    private static Map<String, List<String>> fields() {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        fields.put(ExportSources.PUSHES, ExportFields.PUSHES);
        fields.put(ExportSources.SCM_API_ACTIONS, ExportFields.SCM_API_ACTIONS);
        fields.put(ExportSources.FETCH_ACTIVITY, ExportFields.FETCH_ACTIVITY);
        fields.put(ExportSources.PERMISSION_CHANGES, ExportFields.PERMISSION_CHANGES);
        fields.put(ExportSources.MEMBERSHIP_CHANGES, ExportFields.MEMBERSHIP_CHANGES);
        fields.put(ExportSources.ROLE_CHANGES, ExportFields.ROLE_CHANGES);
        fields.put(ExportSources.GRANTS, ExportFields.GRANTS);
        fields.put(ExportSources.GROUP_MEMBERS, ExportFields.GROUP_MEMBERS);
        return Collections.unmodifiableMap(fields);
    }

    /** One record type and the columns its CSV export can choose from, in their default order. */
    public record RecordType(String name, List<String> columns) {}

    private final AccessChangeService accessChanges;
    private final RepoPermissionService permissions;
    private final ReadOnlyUserStore users;
    private final PushStore pushStore;
    private final ScmApiActionStore scmApiActionStore;
    private final FetchStore fetchStore;

    // ---- permission changes ----

    @Operation(
            operationId = "listPermissionChanges",
            summary = "List permission change events",
            description =
                    "Grants and revocations of user and group repository permissions, most recent first. Filter by action (GRANTED, REVOKED), origin (DASHBOARD, API, CONFIG, IDP), subjectType (USER, GROUP), subject (the user or group whose access changed), user (who made the change), provider, owner and repo together (grants whose pattern applies to that repository), free-text search over subject and pattern, or a time window (from inclusive, to exclusive, ISO-8601). Paginate with limit/offset, or pass the X-Next-Cursor response header back as after to read the next page.")
    @GetMapping("/permission-changes")
    public ResponseEntity<List<PermissionChangeEvent>> permissionChanges(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String repo,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String after,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "true") boolean newestFirst) {

        ListingParams.checkPage(limit, offset);
        Optional<String> repoPath = repoPath(owner, repo);
        PermissionChangeQuery.PermissionChangeQueryBuilder query = permissionChangeQuery(
                        action, origin, subjectType, subject, user, provider, search, from, to)
                .newestFirst(newestFirst);
        Optional<RecordCursor> start = ListingParams.cursor(after);

        List<PermissionChangeEvent> rows = repoPath.isPresent()
                ? scanForRepo(query, start, repoPath.get(), limit, offset)
                : accessChanges.findPermissionChanges(query.after(start.orElse(null))
                        .limit(limit)
                        .offset(offset)
                        .build());
        return ListingParams.page(rows, limit, PermissionChangeEvent::cursor);
    }

    @Operation(
            operationId = "exportPermissionChanges",
            summary = "Export permission change events",
            description =
                    "Every permission change event matching the same filters listPermissionChanges takes, as a zip of parts of at most 100,000 rows and a manifest naming the filters, the generation time, and each part's row count and SHA-256. format is CSV (default) or JSON (JSON Lines). columns chooses and orders CSV columns.")
    @GetMapping("/permission-changes/export")
    public void exportPermissionChanges(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String repo,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "true") boolean newestFirst,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String columns,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        Optional<String> repoPath = repoPath(owner, repo);
        PermissionChangeQuery.PermissionChangeQueryBuilder query = permissionChangeQuery(
                        action, origin, subjectType, subject, user, provider, search, from, to)
                .newestFirst(newestFirst);
        ExportFormat exportFormat = ExportResponses.format(format);
        List<String> chosen = ExportFields.columns(columns, ExportFields.PERMISSION_CHANGES, "permission change");

        ExportResponses.stream(
                request,
                response,
                ExportSources.PERMISSION_CHANGES,
                List.of(ExportSources.permissionChanges(accessChanges, query, repoPath, chosen)),
                exportFormat);
    }

    private static PermissionChangeQuery.PermissionChangeQueryBuilder permissionChangeQuery(
            String action,
            String origin,
            String subjectType,
            String subject,
            String user,
            String provider,
            String search,
            String from,
            String to) {
        PermissionChangeQuery.PermissionChangeQueryBuilder query = PermissionChangeQuery.builder();
        ListingParams.enumValue("action", action, PermissionChangeEvent.Action.class)
                .ifPresent(query::action);
        ListingParams.enumValue("origin", origin, ChangeOrigin.class).ifPresent(query::origin);
        ListingParams.enumValue("subjectType", subjectType, PermissionChangeEvent.SubjectType.class)
                .ifPresent(query::subjectType);
        ListingParams.text(subject).ifPresent(query::subject);
        ListingParams.text(user).ifPresent(query::actor);
        ListingParams.text(provider).ifPresent(query::provider);
        ListingParams.text(search).ifPresent(query::search);
        ListingParams.instant("from", from).ifPresent(query::from);
        ListingParams.instant("to", to).ifPresent(query::to);
        return query;
    }

    /**
     * {@code /owner/repo} when both are given. A grant names a pattern, not a repository, so it can only be matched
     * against a whole repository path; one without the other is a 400.
     */
    private static Optional<String> repoPath(String owner, String repo) {
        Optional<String> o = ListingParams.text(owner);
        Optional<String> r = ListingParams.text(repo);
        if (o.isPresent() != r.isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "owner and repo filter grants together: a grant's pattern is matched against the whole"
                            + " repository");
        }
        return o.map(ow -> "/" + ow + "/" + r.get());
    }

    /**
     * Walks the store in cursor order, keeping the events whose grant applies to {@code path}, until a page is full or
     * the store is exhausted. Pattern matching cannot run in the store, so the filter runs here; reading by cursor
     * keeps the page and its next cursor exact.
     */
    private List<PermissionChangeEvent> scanForRepo(
            PermissionChangeQuery.PermissionChangeQueryBuilder query,
            Optional<RecordCursor> start,
            String path,
            int limit,
            int offset) {
        List<PermissionChangeEvent> matched = new ArrayList<>();
        int toSkip = offset;
        RecordCursor position = start.orElse(null);
        while (true) {
            List<PermissionChangeEvent> batch = accessChanges.findPermissionChanges(
                    query.after(position).limit(REPO_SCAN_BATCH).offset(0).build());
            for (PermissionChangeEvent e : batch) {
                if (!accessChanges.appliesTo(e, path)) {
                    continue;
                }
                if (toSkip > 0) {
                    toSkip--;
                    continue;
                }
                matched.add(e);
                if (matched.size() == limit) {
                    return matched;
                }
            }
            if (batch.size() < REPO_SCAN_BATCH) {
                return matched;
            }
            position = batch.getLast().cursor();
        }
    }

    // ---- membership changes ----

    @Operation(
            operationId = "listMembershipChanges",
            summary = "List group membership change events",
            description =
                    "Additions to and removals from permission groups, most recent first. Filter by action (ADDED, REMOVED), origin (DASHBOARD, API, CONFIG, IDP), group (its name when the change was made), subject (the member), user (who made the change), free-text search over group and member, or a time window (from inclusive, to exclusive, ISO-8601). A membership change names no repository, so provider, owner and repo are not filters here. Paginate with limit/offset, or pass the X-Next-Cursor response header back as after to read the next page.")
    @GetMapping("/membership-changes")
    public ResponseEntity<List<MembershipChangeEvent>> membershipChanges(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String group,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String repo,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String after,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "true") boolean newestFirst) {

        ListingParams.checkPage(limit, offset);
        MembershipChangeQuery.MembershipChangeQueryBuilder query = membershipChangeQuery(
                        action, origin, group, subject, user, provider, owner, repo, search, from, to)
                .limit(limit)
                .offset(offset)
                .newestFirst(newestFirst);
        ListingParams.cursor(after).ifPresent(query::after);

        return ListingParams.page(
                accessChanges.findMembershipChanges(query.build()), limit, MembershipChangeEvent::cursor);
    }

    @Operation(
            operationId = "exportMembershipChanges",
            summary = "Export group membership change events",
            description =
                    "Every membership change event matching the same filters listMembershipChanges takes, as a zip of parts of at most 100,000 rows and a manifest naming the filters, the generation time, and each part's row count and SHA-256. format is CSV (default) or JSON (JSON Lines). columns chooses and orders CSV columns.")
    @GetMapping("/membership-changes/export")
    public void exportMembershipChanges(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String group,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String repo,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "true") boolean newestFirst,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String columns,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        MembershipChangeQuery.MembershipChangeQueryBuilder query = membershipChangeQuery(
                        action, origin, group, subject, user, provider, owner, repo, search, from, to)
                .newestFirst(newestFirst);
        ExportFormat exportFormat = ExportResponses.format(format);
        List<String> chosen = ExportFields.columns(columns, ExportFields.MEMBERSHIP_CHANGES, "membership change");

        ExportResponses.stream(
                request,
                response,
                ExportSources.MEMBERSHIP_CHANGES,
                List.of(ExportSources.membershipChanges(accessChanges, query, chosen)),
                exportFormat);
    }

    private static MembershipChangeQuery.MembershipChangeQueryBuilder membershipChangeQuery(
            String action,
            String origin,
            String group,
            String subject,
            String user,
            String provider,
            String owner,
            String repo,
            String search,
            String from,
            String to) {
        String recordType = "membership changes";
        ListingParams.unsupported("provider", provider, recordType);
        ListingParams.unsupported("owner", owner, recordType);
        ListingParams.unsupported("repo", repo, recordType);
        MembershipChangeQuery.MembershipChangeQueryBuilder query = MembershipChangeQuery.builder();
        ListingParams.enumValue("action", action, MembershipChangeEvent.Action.class)
                .ifPresent(query::action);
        ListingParams.enumValue("origin", origin, ChangeOrigin.class).ifPresent(query::origin);
        ListingParams.text(group).ifPresent(query::groupName);
        ListingParams.text(subject).ifPresent(query::member);
        ListingParams.text(user).ifPresent(query::actor);
        ListingParams.text(search).ifPresent(query::search);
        ListingParams.instant("from", from).ifPresent(query::from);
        ListingParams.instant("to", to).ifPresent(query::to);
        return query;
    }

    // ---- role changes ----

    @Operation(
            operationId = "listRoleChanges",
            summary = "List role change events",
            description =
                    "Changes to a user's roles, most recent first: at creation and deletion of a local user, and whenever an identity provider asserts different roles at sign-in. Filter by origin (DASHBOARD, API, CONFIG, IDP), subject (the user whose roles changed), user (who made the change; for IDP, the identity provider), free-text search over the username, or a time window (from inclusive, to exclusive, ISO-8601). A role change names no repository, so provider, owner and repo are not filters here. Paginate with limit/offset, or pass the X-Next-Cursor response header back as after to read the next page.")
    @GetMapping("/role-changes")
    public ResponseEntity<List<RoleChangeEvent>> roleChanges(
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String repo,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String after,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "true") boolean newestFirst) {

        ListingParams.checkPage(limit, offset);
        RoleChangeQuery.RoleChangeQueryBuilder query = roleChangeQuery(
                        origin, subject, user, provider, owner, repo, search, from, to)
                .limit(limit)
                .offset(offset)
                .newestFirst(newestFirst);
        ListingParams.cursor(after).ifPresent(query::after);

        return ListingParams.page(accessChanges.findRoleChanges(query.build()), limit, RoleChangeEvent::cursor);
    }

    @Operation(
            operationId = "exportRoleChanges",
            summary = "Export role change events",
            description =
                    "Every role change event matching the same filters listRoleChanges takes, as a zip of parts of at most 100,000 rows and a manifest naming the filters, the generation time, and each part's row count and SHA-256. format is CSV (default) or JSON (JSON Lines). columns chooses and orders CSV columns.")
    @GetMapping("/role-changes/export")
    public void exportRoleChanges(
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String repo,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "true") boolean newestFirst,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String columns,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        RoleChangeQuery.RoleChangeQueryBuilder query = roleChangeQuery(
                        origin, subject, user, provider, owner, repo, search, from, to)
                .newestFirst(newestFirst);
        ExportFormat exportFormat = ExportResponses.format(format);
        List<String> chosen = ExportFields.columns(columns, ExportFields.ROLE_CHANGES, "role change");

        ExportResponses.stream(
                request,
                response,
                ExportSources.ROLE_CHANGES,
                List.of(ExportSources.roleChanges(accessChanges, query, chosen)),
                exportFormat);
    }

    private static RoleChangeQuery.RoleChangeQueryBuilder roleChangeQuery(
            String origin,
            String subject,
            String user,
            String provider,
            String owner,
            String repo,
            String search,
            String from,
            String to) {
        String recordType = "role changes";
        ListingParams.unsupported("provider", provider, recordType);
        ListingParams.unsupported("owner", owner, recordType);
        ListingParams.unsupported("repo", repo, recordType);
        RoleChangeQuery.RoleChangeQueryBuilder query = RoleChangeQuery.builder();
        ListingParams.enumValue("origin", origin, ChangeOrigin.class).ifPresent(query::origin);
        ListingParams.text(subject).ifPresent(query::username);
        ListingParams.text(user).ifPresent(query::actor);
        ListingParams.text(search).ifPresent(query::search);
        ListingParams.instant("from", from).ifPresent(query::from);
        ListingParams.instant("to", to).ifPresent(query::to);
        return query;
    }

    // ---- current grants and membership ----

    @Operation(
            operationId = "exportGrants",
            summary = "Export the current grants",
            description =
                    "A snapshot of who holds which capability on which repositories right now: every user permission, and every group rule once per member of the group, with where it is defined, who granted it and when, and whether the holder's roles let it take effect. Filter by subject (the holder), user (who granted it), via (DIRECT, GROUP), group, provider, or owner and repo together (grants whose pattern applies to that repository). Written as a zip with a manifest, like every export. format is CSV (default) or JSON (JSON Lines). columns chooses and orders CSV columns.")
    @GetMapping("/grants/export")
    public void exportGrants(
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String via,
            @RequestParam(required = false) String group,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String repo,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String columns,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        Predicate<GrantSnapshot.Holding> keep = grantFilter(subject, user, via, group, provider, owner, repo);
        ExportFormat exportFormat = ExportResponses.format(format);
        List<String> chosen = ExportFields.columns(columns, ExportFields.GRANTS, "grant");

        List<GrantSnapshot.Holding> rows =
                GrantSnapshot.holdings(permissions, users).stream().filter(keep).toList();
        ExportResponses.stream(
                request, response, ExportSources.GRANTS, List.of(ExportSources.grants(rows, chosen)), exportFormat);
    }

    private Predicate<GrantSnapshot.Holding> grantFilter(
            String subject, String user, String via, String group, String provider, String owner, String repo) {
        Optional<String> repoPath = repoPath(owner, repo);
        Optional<GrantSnapshot.Via> viaValue = ListingParams.enumValue("via", via, GrantSnapshot.Via.class);
        Optional<String> s = ListingParams.text(subject);
        Optional<String> u = ListingParams.text(user);
        Optional<String> g = ListingParams.text(group);
        Optional<String> p = ListingParams.text(provider);
        return h -> s.map(v -> v.equals(h.holder())).orElse(true)
                && u.map(v -> v.equals(h.grantedBy())).orElse(true)
                && viaValue.map(v -> v == h.via()).orElse(true)
                && g.map(v -> v.equals(h.group())).orElse(true)
                && p.map(v -> v.equals(h.provider())).orElse(true)
                && repoPath.map(path -> permissions.patternMatches(h.target(), h.value(), h.matchType(), path))
                        .orElse(true);
    }

    @Operation(
            operationId = "exportGroupMembers",
            summary = "Export the current group membership",
            description =
                    "A snapshot of who is in each permission group right now. Filter by group or subject (the member). Written as a zip with a manifest, like every export. format is CSV (default) or JSON (JSON Lines). columns chooses and orders CSV columns.")
    @GetMapping("/group-members/export")
    public void exportGroupMembers(
            @RequestParam(required = false) String group,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String columns,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        Predicate<GrantSnapshot.Membership> keep = membershipFilter(group, subject);
        ExportFormat exportFormat = ExportResponses.format(format);
        List<String> chosen = ExportFields.columns(columns, ExportFields.GROUP_MEMBERS, "group member");

        List<GrantSnapshot.Membership> rows =
                GrantSnapshot.memberships(permissions).stream().filter(keep).toList();
        ExportResponses.stream(
                request,
                response,
                ExportSources.GROUP_MEMBERS,
                List.of(ExportSources.groupMembers(rows, chosen)),
                exportFormat);
    }

    private static Predicate<GrantSnapshot.Membership> membershipFilter(String group, String subject) {
        Optional<String> g = ListingParams.text(group);
        Optional<String> s = ListingParams.text(subject);
        return m -> g.map(v -> v.equals(m.group())).orElse(true)
                && s.map(v -> v.equals(m.member())).orElse(true);
    }

    @Operation(
            operationId = "listGrants",
            summary = "List the current grants",
            description =
                    "The current grants snapshot the grants export writes, one page at a time, ordered by holder: every user permission, and every group rule once per member of the group. Takes the grants export's filters. Paginate with limit/offset; X-Total-Count is the number of rows the filters select.")
    @GetMapping("/grants")
    public ResponseEntity<List<GrantSnapshot.Holding>> grants(
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String via,
            @RequestParam(required = false) String group,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String repo,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        ListingParams.checkPage(limit, offset);
        Predicate<GrantSnapshot.Holding> keep = grantFilter(subject, user, via, group, provider, owner, repo);
        return snapshotPage(
                GrantSnapshot.holdings(permissions, users).stream().filter(keep).toList(), limit, offset);
    }

    @Operation(
            operationId = "listGroupMembers",
            summary = "List the current group membership",
            description =
                    "The current group membership snapshot the group-members export writes, one page at a time, ordered by group and member. Filter by group or subject (the member). Paginate with limit/offset; X-Total-Count is the number of rows the filters select.")
    @GetMapping("/group-members")
    public ResponseEntity<List<GrantSnapshot.Membership>> groupMembers(
            @RequestParam(required = false) String group,
            @RequestParam(required = false) String subject,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        ListingParams.checkPage(limit, offset);
        Predicate<GrantSnapshot.Membership> keep = membershipFilter(group, subject);
        return snapshotPage(
                GrantSnapshot.memberships(permissions).stream().filter(keep).toList(), limit, offset);
    }

    /** One page of a current-state snapshot, with the number of rows the filters selected. */
    private static <T> ResponseEntity<List<T>> snapshotPage(List<T> rows, int limit, int offset) {
        List<T> page = rows.stream().skip(offset).limit(limit).toList();
        return ResponseEntity.ok()
                .header(TOTAL_COUNT_HEADER, String.valueOf(rows.size()))
                .body(page);
    }

    // ---- everything ----

    @Operation(
            operationId = "listRecordTypes",
            summary = "List the record types and their export columns",
            description =
                    "Every record type an export can write, in the order an export of everything writes them, with the columns its CSV export can choose from in their default order.")
    @GetMapping("/record-types")
    public List<RecordType> listRecordTypes() {
        return FIELDS.entrySet().stream()
                .map(e -> new RecordType(e.getKey(), e.getValue()))
                .toList();
    }

    @Operation(
            operationId = "exportEvidence",
            summary = "Export several record types at once",
            description =
                    "Every record of each requested type in one zip: a folder of parts per type and one manifest. types is a comma-separated list of pushes, scm-api-actions, fetch-activity, permission-changes, membership-changes, role-changes, grants and group-members; none means all of them. from (inclusive) and to (exclusive) bound each type's time; grants and group-members are current state and take no window. format is CSV (default) or JSON (JSON Lines). A CSV writes every field of each type unless <type>.columns (for example pushes.columns=id,timestamp,status) chooses and orders that type's columns; a type named this way must be one being exported.")
    @GetMapping("/export")
    public void exportEvidence(
            @RequestParam(required = false) String types,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String format,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        Set<String> requested = recordTypes(types);
        Optional<Instant> start = ListingParams.instant("from", from);
        Optional<Instant> end = ListingParams.instant("to", to);
        ExportFormat exportFormat = ExportResponses.format(format);
        Map<String, List<String>> columns = columnsByType(request, requested);

        List<ExportSource<?>> sources = new ArrayList<>();
        for (String type : RECORD_TYPES) {
            if (requested.contains(type)) {
                sources.add(everything(type, start, end, columns.get(type)));
            }
        }
        ExportResponses.stream(request, response, "evidence", sources, exportFormat);
    }

    /** Each exported type's columns: every field, unless a {@code <type>.columns} parameter chooses them. */
    private static Map<String, List<String>> columnsByType(HttpServletRequest request, Set<String> requested) {
        Map<String, List<String>> columns = new LinkedHashMap<>();
        for (String type : requested) {
            columns.put(type, FIELDS.get(type));
        }
        for (Map.Entry<String, String[]> param : request.getParameterMap().entrySet()) {
            String key = param.getKey();
            if (!key.endsWith(ExportResponses.COLUMNS_SUFFIX)) {
                continue;
            }
            String type = key.substring(0, key.length() - ExportResponses.COLUMNS_SUFFIX.length());
            if (!FIELDS.containsKey(type)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Unknown record type in " + key + ". Valid types: " + RECORD_TYPES);
            }
            if (!requested.contains(type)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, key + " names a type that is not being exported");
            }
            String value = param.getValue().length == 0 ? null : String.join(",", param.getValue());
            columns.put(type, ExportFields.columns(value, FIELDS.get(type), type));
        }
        return columns;
    }

    /** The requested types, each one known; none means all. */
    private static Set<String> recordTypes(String types) {
        if (types == null || types.isBlank()) {
            return new LinkedHashSet<>(RECORD_TYPES);
        }
        Set<String> requested = new LinkedHashSet<>();
        for (String raw : types.split(",")) {
            String type = raw.trim();
            if (type.isEmpty()) {
                continue;
            }
            if (!RECORD_TYPES.contains(type)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Unknown record type: " + type + ". Valid types: " + RECORD_TYPES);
            }
            requested.add(type);
        }
        return requested;
    }

    /** One record type, oldest first, bounded only by the time window, writing {@code columns}. */
    private ExportSource<?> everything(
            String type, Optional<Instant> from, Optional<Instant> to, List<String> columns) {
        return switch (type) {
            case ExportSources.PUSHES -> {
                PushQuery.PushQueryBuilder q = PushQuery.builder().newestFirst(false);
                from.ifPresent(q::newerThan);
                to.ifPresent(q::olderThan);
                yield ExportSources.pushes(pushStore, q, columns);
            }
            case ExportSources.SCM_API_ACTIONS -> {
                ScmApiActionQuery.ScmApiActionQueryBuilder q =
                        ScmApiActionQuery.builder().newestFirst(false);
                from.ifPresent(q::from);
                to.ifPresent(q::to);
                yield ExportSources.scmApiActions(scmApiActionStore, q, columns);
            }
            case ExportSources.FETCH_ACTIVITY -> {
                FetchActivityQuery.FetchActivityQueryBuilder q =
                        FetchActivityQuery.builder().newestFirst(false);
                from.ifPresent(q::from);
                to.ifPresent(q::to);
                yield ExportSources.fetchActivity(fetchStore, q, columns);
            }
            case ExportSources.PERMISSION_CHANGES -> {
                PermissionChangeQuery.PermissionChangeQueryBuilder q =
                        PermissionChangeQuery.builder().newestFirst(false);
                from.ifPresent(q::from);
                to.ifPresent(q::to);
                yield ExportSources.permissionChanges(accessChanges, q, Optional.empty(), columns);
            }
            case ExportSources.MEMBERSHIP_CHANGES -> {
                MembershipChangeQuery.MembershipChangeQueryBuilder q =
                        MembershipChangeQuery.builder().newestFirst(false);
                from.ifPresent(q::from);
                to.ifPresent(q::to);
                yield ExportSources.membershipChanges(accessChanges, q, columns);
            }
            case ExportSources.ROLE_CHANGES -> {
                RoleChangeQuery.RoleChangeQueryBuilder q =
                        RoleChangeQuery.builder().newestFirst(false);
                from.ifPresent(q::from);
                to.ifPresent(q::to);
                yield ExportSources.roleChanges(accessChanges, q, columns);
            }
            case ExportSources.GRANTS -> ExportSources.grants(GrantSnapshot.holdings(permissions, users), columns);
            case ExportSources.GROUP_MEMBERS ->
                ExportSources.groupMembers(GrantSnapshot.memberships(permissions), columns);
            default -> throw new IllegalArgumentException("not a record type: " + type);
        };
    }
}
