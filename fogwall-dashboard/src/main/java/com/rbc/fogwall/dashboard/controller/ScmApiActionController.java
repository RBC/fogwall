package com.rbc.fogwall.dashboard.controller;

import com.rbc.fogwall.dashboard.export.ExportFields;
import com.rbc.fogwall.dashboard.export.ExportFormat;
import com.rbc.fogwall.db.ScmApiActionStore;
import com.rbc.fogwall.db.ScmApiEntityStore;
import com.rbc.fogwall.db.model.RecordCursor;
import com.rbc.fogwall.db.model.ScmApiActionOrigin;
import com.rbc.fogwall.db.model.ScmApiActionQuery;
import com.rbc.fogwall.db.model.ScmApiActionRecord;
import com.rbc.fogwall.db.model.ScmApiActionStatus;
import com.rbc.fogwall.db.model.ScmApiEntityRecord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only view of SCM API proxy audit records — same auditability bar as push records, no workflow actions. */
@Tag(name = "SCM API", description = "SCM API proxy audit records")
@RestController
@RequestMapping("/api/scm-api-actions")
@RequiredArgsConstructor
public class ScmApiActionController {

    private final ScmApiActionStore scmApiActionStore;
    private final ScmApiEntityStore scmApiEntityStore;

    @Operation(
            operationId = "listScmApiActions",
            summary = "List SCM API proxy audit records",
            description =
                    "Returns SCM API proxy mutation records ordered by most recent first, each with the pull/merge request or issue it created or touched when the upstream response named one. Filter by status (FORWARDED, DENIED, REJECTED, ERROR), origin (DASHBOARD, SCM_API), provider, resolved user, owner (formerly repoOwner), repo (formerly repoName), free-text search, or a timestamp window (from inclusive, to exclusive, ISO-8601). Paginate with limit/offset, or pass the X-Next-Cursor response header back as after to read the next page.")
    @GetMapping
    public ResponseEntity<List<ScmApiActionView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String repoOwner,
            @RequestParam(required = false) String repo,
            @RequestParam(required = false) String repoName,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String after,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "true") boolean newestFirst) {

        ListingParams.checkPage(limit, offset);
        ScmApiActionQuery.ScmApiActionQueryBuilder query = query(
                        status, origin, provider, user, owner, repoOwner, repo, repoName, search, from, to, newestFirst)
                .limit(limit)
                .offset(offset);
        ListingParams.cursor(after).ifPresent(query::after);

        List<ScmApiActionRecord> records = scmApiActionStore.find(query.build());
        Map<String, ScmApiEntityRecord> entities = scmApiEntityStore
                .findByIds(records.stream()
                        .map(ScmApiActionRecord::getEntityId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(ScmApiEntityRecord::getId, Function.identity()));
        List<ScmApiActionView> rows = records.stream()
                .map(r -> new ScmApiActionView(r, r.getEntityId() == null ? null : entities.get(r.getEntityId())))
                .toList();
        return ListingParams.page(
                rows,
                limit,
                v -> new RecordCursor(
                        v.getAction().getTimestamp(), v.getAction().getId()));
    }

    @Operation(
            operationId = "exportScmApiActions",
            summary = "Export SCM API proxy audit records",
            description =
                    "Every SCM API proxy audit record matching the same filters listScmApiActions takes, as a zip of parts of at most 100,000 rows and a manifest naming the filters, the generation time, and each part's row count and SHA-256. Each row names the pull/merge request or issue it touched by entityId. format is CSV (default) or JSON (JSON Lines). columns chooses and orders CSV columns. Requires ROLE_AUDITOR or ROLE_ADMIN.")
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public void export(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String repoOwner,
            @RequestParam(required = false) String repo,
            @RequestParam(required = false) String repoName,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "true") boolean newestFirst,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String columns,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        ScmApiActionQuery.ScmApiActionQueryBuilder query =
                query(status, origin, provider, user, owner, repoOwner, repo, repoName, search, from, to, newestFirst);
        ExportFormat exportFormat = ExportResponses.format(format);
        List<String> chosen = ExportFields.columns(columns, ExportFields.SCM_API_ACTIONS, "SCM API action");

        ExportResponses.stream(
                request,
                response,
                ExportSources.SCM_API_ACTIONS,
                List.of(ExportSources.scmApiActions(scmApiActionStore, query, chosen)),
                exportFormat);
    }

    /** The filters the listing and the export share. */
    static ScmApiActionQuery.ScmApiActionQueryBuilder query(
            String status,
            String origin,
            String provider,
            String user,
            String owner,
            String repoOwner,
            String repo,
            String repoName,
            String search,
            String from,
            String to,
            boolean newestFirst) {
        ScmApiActionQuery.ScmApiActionQueryBuilder query =
                ScmApiActionQuery.builder().newestFirst(newestFirst);
        ListingParams.enumValue("status", status, ScmApiActionStatus.class).ifPresent(query::status);
        ListingParams.enumValue("origin", origin, ScmApiActionOrigin.class).ifPresent(query::origin);
        ListingParams.text(provider).ifPresent(query::provider);
        ListingParams.text(user).ifPresent(query::user);
        ListingParams.aliased("owner", owner, "repoOwner", repoOwner).ifPresent(query::repoOwner);
        ListingParams.aliased("repo", repo, "repoName", repoName).ifPresent(query::repoName);
        ListingParams.text(search).ifPresent(query::search);
        ListingParams.instant("from", from).ifPresent(query::from);
        ListingParams.instant("to", to).ifPresent(query::to);
        return query;
    }

    @Operation(operationId = "getScmApiAction", summary = "Get a single SCM API proxy audit record")
    @GetMapping("/{id}")
    public ResponseEntity<ScmApiActionView> get(@PathVariable String id) {
        return scmApiActionStore
                .findById(id)
                .map(r -> new ScmApiActionView(
                        r,
                        r.getEntityId() == null
                                ? null
                                : scmApiEntityStore.findById(r.getEntityId()).orElse(null)))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
