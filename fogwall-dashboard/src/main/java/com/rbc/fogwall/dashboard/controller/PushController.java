package com.rbc.fogwall.dashboard.controller;

import com.rbc.fogwall.config.AttestationQuestion;
import com.rbc.fogwall.config.FogwallConfig;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.model.Attestation;
import com.rbc.fogwall.db.model.PushQuery;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import com.rbc.fogwall.db.model.PushStep;
import com.rbc.fogwall.db.model.PushSummary;
import com.rbc.fogwall.jetty.reload.ConfigHolder;
import com.rbc.fogwall.permission.RepoPermission;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.provider.ProviderRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Push", description = "Push records and the approval workflow")
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    private final PushStore pushStore;

    private final RepoPermissionService repoPermissionService;

    private final FogwallConfig fogwallConfig;

    private final ConfigHolder configHolder;

    private final ProviderRegistry providerRegistry;

    /** Returns the authenticated username, falling back to {@code body.reviewerUsername}, then {@code "system"}. */
    private static String resolveReviewer(Map<String, String> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return body != null ? body.getOrDefault("reviewerUsername", "system") : "system";
    }

    /**
     * List push records. Optional query params: status, project, repo, user, search (matches project OR repo name),
     * limit (default 50).
     */
    @Operation(
            operationId = "listPushes",
            summary = "List push records",
            description =
                    "Returns push records ordered by most recent first. Filter by status (PENDING, APPROVED, REJECTED, FORWARDED, BLOCKED, CANCELED), project slug, repo, provider, branch, author email, username, or a timestamp window (from/to, ISO-8601). Paginate with limit/offset.")
    @GetMapping
    public List<PushSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String project,
            @RequestParam(required = false) String repo,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String authorEmail,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "true") boolean newestFirst) {

        PushQuery.PushQueryBuilder query =
                PushQuery.builder().limit(limit).offset(offset).newestFirst(newestFirst);

        if (status != null && !status.isBlank()) {
            try {
                query.status(PushStatus.valueOf(status.toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unknown status: " + status + ". Valid values: " + List.of(PushStatus.values()),
                        e);
            }
        }
        applyFilters(query, project, repo, provider, branch, authorEmail, user, search, from, to);

        return pushStore.findSummaries(query.build()).stream()
                .map(this::enrichUrls)
                .toList();
    }

    /**
     * Applies the shared filter params to {@code query}. Blank values are ignored; {@code from}/{@code to} are ISO-8601
     * instants bounding the push timestamp ({@code from} inclusive, {@code to} exclusive) and a malformed one is a 400.
     */
    private void applyFilters(
            PushQuery.PushQueryBuilder query,
            String project,
            String repo,
            String provider,
            String branch,
            String authorEmail,
            String user,
            String search,
            String from,
            String to) {
        if (project != null && !project.isBlank()) query.project(project);
        if (repo != null && !repo.isBlank()) query.repoName(repo);
        if (provider != null && !provider.isBlank()) query.provider(provider);
        if (branch != null && !branch.isBlank()) query.branch(branch);
        if (authorEmail != null && !authorEmail.isBlank()) query.authorEmail(authorEmail);
        if (user != null && !user.isBlank()) query.user(user);
        if (search != null && !search.isBlank()) query.search(search);
        Instant newerThan = parseInstant(from, "from");
        if (newerThan != null) query.newerThan(newerThan);
        Instant olderThan = parseInstant(to, "to");
        if (olderThan != null) query.olderThan(olderThan);
    }

    /** Parses an ISO-8601 instant param, returning null when blank and a 400 when malformed. */
    private static Instant parseInstant(String value, String param) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid " + param + " timestamp (expected ISO-8601): " + value, e);
        }
    }

    /**
     * Populates {@code repoUrl} and (when {@code commitTo} is set) {@code commitUrl} from the push's provider. Absent
     * for generic providers with no stable public repo URL shape, or when the provider can no longer be resolved (e.g.
     * removed from config since the push was recorded).
     */
    private PushSummary enrichUrls(PushSummary summary) {
        if (summary.getProvider() == null || summary.getProject() == null || summary.getRepoName() == null) {
            return summary;
        }
        return providerRegistry
                .getProvider(summary.getProvider())
                .map(p -> summary.toBuilder()
                        .repoUrl(p.buildRepoUrl(summary.getProject(), summary.getRepoName())
                                .orElse(null))
                        .commitUrl(
                                summary.getCommitTo() != null
                                        ? p.buildCommitUrl(
                                                        summary.getProject(),
                                                        summary.getRepoName(),
                                                        summary.getCommitTo())
                                                .orElse(null)
                                        : null)
                        .build())
                .orElse(summary);
    }

    /** Record-level counterpart of {@link #enrichUrls(PushSummary)}; mutates {@code record} in place. */
    private void enrichUrls(PushRecord record) {
        if (record.getProvider() == null || record.getProject() == null || record.getRepoName() == null) {
            return;
        }
        providerRegistry.getProvider(record.getProvider()).ifPresent(p -> {
            record.setRepoUrl(
                    p.buildRepoUrl(record.getProject(), record.getRepoName()).orElse(null));
            if (record.getCommitTo() != null) {
                record.setCommitUrl(p.buildCommitUrl(record.getProject(), record.getRepoName(), record.getCommitTo())
                        .orElse(null));
            }
        });
    }

    /**
     * Count push records grouped by status. Accepts the same filter params as {@link #list} except {@code status} —
     * returns counts for all statuses so the caller can populate all filter tabs in one round trip.
     */
    @Operation(
            operationId = "countPushes",
            summary = "Count push records by status",
            description =
                    "Returns a map of status → count. Supports the same project/repo/provider/branch/author-email/user/search/date-window filters as listPushes but excludes the status filter.")
    @GetMapping("/counts")
    public Map<String, Long> counts(
            @RequestParam(required = false) String project,
            @RequestParam(required = false) String repo,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String authorEmail,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        PushQuery.PushQueryBuilder query = PushQuery.builder();
        applyFilters(query, project, repo, provider, branch, authorEmail, user, search, from, to);

        return pushStore.countByStatus(query.build());
    }

    /**
     * Look up a push record by its commit reference ({commitFrom}_{commitTo}). Used by the transparent proxy flow where
     * we link to a push before it has been saved with a UUID.
     */
    @Operation(operationId = "getPushByRef", summary = "Get a push record by commit reference")
    @GetMapping("/by-ref/{ref}")
    public ResponseEntity<PushRecord> getByRef(@PathVariable String ref) {
        // ref format: {commitFrom}_{commitTo} (may be short 8-char SHAs)
        String[] parts = ref.split("_", 2);
        if (parts.length != 2) {
            return ResponseEntity.badRequest().build();
        }
        String commitTo = parts[1];

        // Find by commitTo (most selective) - pick the most recent PENDING or APPROVED record
        List<PushRecord> records = pushStore.find(PushQuery.builder()
                .commitTo(commitTo)
                .newestFirst(true)
                .limit(1)
                .build());

        // Fall back to full SHA lookup if short SHA was used
        if (records.isEmpty()) {
            records = pushStore.find(
                    PushQuery.builder().newestFirst(true).limit(50).build());
            records = records.stream()
                    .filter(r -> r.getCommitTo() != null && r.getCommitTo().startsWith(commitTo))
                    .limit(1)
                    .collect(Collectors.toList());
        }

        if (records.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PushRecord record = records.get(0);
        enrichUrls(record);
        return ResponseEntity.ok(record);
    }

    /**
     * Get a single push record by ID. The {@code diff} step's {@code content} is stripped from this response — diff
     * content can be large (tens of thousands of lines) and is served separately via {@code GET /{id}/diff}. All other
     * step content is included (typically small validation output).
     */
    @Operation(operationId = "getPush", summary = "Get a push record")
    @GetMapping("/{id}")
    public ResponseEntity<PushRecord> getById(@PathVariable String id) {
        return pushStore
                .findById(id)
                .map(record -> {
                    stripDiffContent(record);
                    record.setCanCurrentUserSelfCertify(computeCanCurrentUserSelfCertify(record));
                    enrichUrls(record);
                    return ResponseEntity.ok(record);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Computes whether the currently authenticated user is permitted to self-approve this specific push via the
     * self-certify path. The user must be the resolved pusher, hold the {@code ROLE_SELF_CERTIFY} authority (capability
     * gate), and have a matching {@code SELF_CERTIFY} repo permission row (per-repo entitlement). Applies to both
     * regular users and admins — admins who hold self-certify permissions follow this path rather than the admin
     * override path.
     */
    private boolean computeCanCurrentUserSelfCertify(PushRecord record) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        String reviewer = auth.getName();
        String pusher = record.getResolvedUser();
        if (reviewer == null || pusher == null || !pusher.equals(reviewer)) return false;
        boolean hasRole = auth.getAuthorities().stream().anyMatch(a -> "ROLE_SELF_CERTIFY".equals(a.getAuthority()));
        if (!hasRole) return false;
        return record.getProvider() != null
                && record.getUrl() != null
                && repoPermissionService.isBypassReviewAllowed(reviewer, record.getProvider(), record.getUrl());
    }

    /**
     * Returns the raw unified diff for a push. Separated from the main push record response so that large diffs do not
     * block page load — the dashboard fetches this lazily and decides whether to render inline or link to the
     * standalone diff page based on size.
     */
    @Operation(operationId = "getPushDiff", summary = "Get the unified diff for a push")
    @GetMapping("/{id}/diff")
    public ResponseEntity<Map<String, Object>> getDiff(@PathVariable String id) {
        return pushStore
                .findById(id)
                .map(record -> {
                    String content = record.getSteps().stream()
                            .filter(s -> "diff".equals(s.getStepName()) && s.getContent() != null)
                            .map(PushStep::getContent)
                            .findFirst()
                            .orElse(null);
                    Map<String, Object> body = new java.util.HashMap<>();
                    body.put("content", content);
                    return ResponseEntity.ok(body);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Nulls out the content of the diff step in-place. Safe because findById returns fresh objects per call. */
    private static void stripDiffContent(PushRecord record) {
        record.getSteps().stream().filter(s -> "diff".equals(s.getStepName())).forEach(s -> s.setContent(null));
    }

    /**
     * Request body for the approve endpoint. Extends the basic reviewer fields with an optional map of attestation
     * question answers keyed by question ID.
     */
    public record ApproveBody(
            String reviewerUsername,
            String reviewerEmail,
            String reason,
            Map<String, String> attestations,
            Boolean adminOverride) {}

    /**
     * Approve a push. Body: { "reviewerUsername": "...", "reviewerEmail": "...", "reason": "...", "attestations": {
     * "question-id": "answer", ... } }
     */
    @Operation(operationId = "approvePush", summary = "Approve a push")
    @PostMapping("/{id}/authorise")
    public ResponseEntity<?> approve(@PathVariable String id, @RequestBody ApproveBody body) {
        return pushStore
                .findById(id)
                .map(record -> {
                    if (record.getStatus() != PushStatus.PENDING) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "Push is not in PENDING status: " + record.getStatus()));
                    }
                    boolean adminOverride = Boolean.TRUE.equals(body.adminOverride());
                    ResponseEntity<?> identityError = checkReviewerIdentity(record, adminOverride);
                    if (identityError != null) return identityError;

                    // Validate required attestation questions are answered
                    ResponseEntity<?> attestationError = checkAttestationAnswers(body.attestations());
                    if (attestationError != null) return attestationError;

                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    var attestation = Attestation.builder()
                            .pushId(id)
                            .type(Attestation.Type.APPROVAL)
                            .reviewerUsername(resolveReviewerFromApproveBody(body, auth))
                            .reviewerEmail(body.reviewerEmail())
                            .reason(body.reason())
                            .selfApproval(isAdminOverride(record, auth, adminOverride))
                            .answers(body.attestations())
                            .build();
                    var updated = pushStore.approve(id, attestation);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Checks that all required attestation questions for the push's provider have been answered.
     *
     * @return a 400 response if a required question is missing, {@code null} if all required questions are answered
     */
    private ResponseEntity<?> checkAttestationAnswers(Map<String, String> answers) {
        List<AttestationQuestion> questions = configHolder.getAttestations();
        if (questions == null || questions.isEmpty()) return null;

        Map<String, String> submitted = answers != null ? answers : Map.of();
        for (AttestationQuestion question : questions) {
            if (!question.isRequired()) continue;
            String answer = submitted.get(question.getId());
            if (answer == null || answer.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Required attestation question not answered: " + question.getId()));
            }
            // For checkboxes, "true" is the only accepted value for a required question
            if ("checkbox".equals(question.getType()) && !"true".equals(answer)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Required attestation checkbox must be checked: " + question.getId()));
            }
        }
        return null;
    }

    private static String resolveReviewerFromApproveBody(ApproveBody body, Authentication auth) {
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return body != null && body.reviewerUsername() != null ? body.reviewerUsername() : "system";
    }

    /** Reject a push. Body: { "reviewerUsername": "...", "reviewerEmail": "...", "reason": "..." } (reason required) */
    @Operation(operationId = "rejectPush", summary = "Reject a push")
    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable String id, @RequestBody Map<String, String> body) {
        String reason = body.get("reason");
        if (reason == null || reason.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Rejection reason is required"));
        }
        return pushStore
                .findById(id)
                .map(record -> {
                    if (record.getStatus() != PushStatus.PENDING) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "Push is not in PENDING status: " + record.getStatus()));
                    }
                    // Rejecting another's push is the safe direction, so an admin may always reject via break-glass
                    // (adminOverride=true). A self-reject — admin or not — still goes through the self-certify gate
                    // like any self-review; the pusher withdraws their own push with cancel, not reject.
                    ResponseEntity<?> identityError = checkReviewerIdentity(record, true);
                    if (identityError != null) return identityError;
                    var attestation = Attestation.builder()
                            .pushId(id)
                            .type(Attestation.Type.REJECTION)
                            .reviewerUsername(resolveReviewer(body))
                            .reviewerEmail(body.get("reviewerEmail"))
                            .reason(reason)
                            .build();
                    var updated = pushStore.reject(id, attestation);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Validates that the current session user may review the given push record:
     *
     * <ol>
     *   <li>Self-review (admin or otherwise): allowed only when the reviewer has both {@code ROLE_SELF_CERTIFY} (the
     *       capability, attested by the org's IdP/IAM via {@code auth.role-mappings} or the local {@code users[].roles}
     *       block) and a {@link RepoPermission.Operation#SELF_CERTIFY} repo permission entry for this specific
     *       repository. Both must be present. Holding ROLE_ADMIN does not bypass this — an admin approving their own
     *       push is treated exactly like any other user, and {@code adminOverride} does not apply to one's own push.
     *   <li>Reviewing someone else's push: the pusher must have been resolved to a proxy user, or we cannot guarantee
     *       identity.
     *   <li>ROLE_ADMIN reviewing someone else's push with {@code adminOverride=true}: break-glass — permitted on admin
     *       authority, bypassing the review-permission check (for when the designated reviewer is unavailable).
     *   <li>Any other reviewer of someone else's push (including an admin <em>without</em> override): by default any
     *       authenticated user may review. When {@code server.require-review-permission: true}, the user must have a
     *       REVIEW (or PUSH_AND_REVIEW) permission for the repo.
     * </ol>
     *
     * @param adminOverride {@code true} when an admin has explicitly activated the break-glass override; only applies
     *     to another user's push (never one's own), and ignored for non-admins
     * @return a 403 response if the check fails, {@code null} if the reviewer is permitted to proceed
     */
    private ResponseEntity<?> checkReviewerIdentity(PushRecord record, boolean adminOverride) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String reviewer = auth != null ? auth.getName() : null;
        String pusherProxyUser = record.getResolvedUser();
        boolean isSelfReview = pusherProxyUser != null && pusherProxyUser.equals(reviewer);

        if (isSelfReview) {
            // Self-review requires two independent checks — no admin or override bypass:
            // 1. ROLE_SELF_CERTIFY — the capability, granted via auth.role-mappings or users[].roles in config.
            // 2. A SELF_CERTIFY repo permission entry for this specific repo — the per-repo entitlement.
            boolean hasSelfCertifyRole = auth != null
                    && auth.getAuthorities().stream().anyMatch(a -> "ROLE_SELF_CERTIFY".equals(a.getAuthority()));
            if (!hasSelfCertifyRole) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Self-approval is not permitted: SELF_CERTIFY role not granted"));
            }
            boolean hasSelfCertifyPerm = record.getProvider() != null
                    && record.getUrl() != null
                    && repoPermissionService.isBypassReviewAllowed(reviewer, record.getProvider(), record.getUrl());
            if (hasSelfCertifyPerm) {
                return null;
            }
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "error", "Self-approval is not permitted: no SELF_CERTIFY permission for this repository"));
        }

        // Break-glass: an admin approving someone else's push on admin authority, bypassing the review-permission
        // check. Deliberately checked before the identity gate below so it works even for an unresolved pusher.
        if (isAdmin(auth) && adminOverride) return null;

        if (pusherProxyUser == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(
                            Map.of(
                                    "error",
                                    "Pusher identity has not been resolved to a proxy user; approval requires verified identity"));
        }

        // Everyone else (including an admin not using override): check REVIEW permission only when
        // require-review-permission is enabled. Default (false) allows any authenticated user to review any push they
        // did not push themselves.
        boolean requirePerm =
                fogwallConfig.getServer() != null && fogwallConfig.getServer().isRequireReviewPermission();
        if (requirePerm && record.getProvider() != null && record.getUrl() != null) {
            if (reviewer != null
                    && !repoPermissionService.isAllowedToReview(reviewer, record.getProvider(), record.getUrl())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You do not have permission to approve pushes for this repository"));
            }
        }

        return null;
    }

    /** Cancel a push. Only the pusher or an admin may cancel. Body: { "reviewerUsername": "..." } */
    @Operation(operationId = "cancelPush", summary = "Cancel a push")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        return pushStore
                .findById(id)
                .map(record -> {
                    if (record.getStatus() != PushStatus.PENDING) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "Push is not in PENDING status: " + record.getStatus()));
                    }
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    if (!isAdmin(auth)) {
                        String pusherProxyUser = record.getResolvedUser();
                        String reviewer = auth != null ? auth.getName() : null;
                        if (pusherProxyUser == null || !pusherProxyUser.equals(reviewer)) {
                            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                    .body(Map.of("error", "Only the pusher or an admin can cancel a push"));
                        }
                    }
                    var attestation = Attestation.builder()
                            .pushId(id)
                            .type(Attestation.Type.CANCELLATION)
                            .reviewerUsername(resolveReviewer(body))
                            .build();
                    var updated = pushStore.cancel(id, attestation);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private static boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /**
     * Flags an approval made via the admin break-glass override. Only ever true for an admin approving <em>another</em>
     * user's push on admin authority — an own-push override is refused by {@link #checkReviewerIdentity} before an
     * attestation is built, so this never marks a self-approval. Recorded on the attestation for the audit trail (the
     * attestation's {@code selfApproval} field predates the rename and carries this bit).
     */
    private static boolean isAdminOverride(PushRecord record, Authentication auth, boolean adminOverride) {
        if (!isAdmin(auth) || !adminOverride) return false;
        String pusher = record.getResolvedUser();
        String reviewer = auth != null ? auth.getName() : null;
        return pusher == null || !pusher.equals(reviewer);
    }
}
