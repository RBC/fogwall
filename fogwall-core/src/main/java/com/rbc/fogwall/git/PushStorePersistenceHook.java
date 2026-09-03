package com.rbc.fogwall.git;

import static com.rbc.fogwall.git.GitClientUtils.AnsiColor.*;
import static com.rbc.fogwall.git.GitClientUtils.SymbolCodes.*;
import static com.rbc.fogwall.git.GitClientUtils.color;
import static com.rbc.fogwall.git.GitClientUtils.sym;

import com.rbc.fogwall.db.PushRecordMapper;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.model.PushCommit;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import com.rbc.fogwall.db.model.PushStep;
import com.rbc.fogwall.db.model.StepStatus;
import com.rbc.fogwall.provider.FogwallProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.*;

/**
 * Persists the single lifecycle record for a store-and-forward push to the {@link PushStore}, keyed by the push id —
 * the correlation identifier minted in {@link StoreAndForwardReceivePackFactory} and reused for the on-disk quarantine
 * directory.
 *
 * <p>One record per submission, transitioned in place: the same model transparent proxy uses, and the one the
 * git-proxy-spec push-lifecycle state machine requires (exactly one canonical state per submission; audit history
 * derived from evidence embedded in the record, not from separate per-transition rows).
 *
 * <ul>
 *   <li>{@link #validationResultHook} creates the record at its first decision — REJECTED (hard policy violation) or
 *       PENDING (awaiting review) — and publishes its id as the validation record id the approval gate acts on.
 *   <li>{@link #postReceiveHook} transitions that same record to FORWARDED or ERROR once forwarding completes.
 * </ul>
 */
@Slf4j
public class PushStorePersistenceHook {

    /**
     * Maps S&F hook names to canonical step orders matching the equivalent proxy filter. Used so REJECTED push records
     * sort validation steps in the same order as proxy mode.
     */
    private static final Map<String, Integer> HOOK_STEP_ORDER = Map.of(
            "checkUrlRules", 100,
            "checkAuthorEmails", 2100,
            "checkCommitMessages", 2200,
            "scanDiff", 2300,
            "scanSecrets", 2500);

    private final PushStore pushStore;
    private final FogwallProvider provider;
    private PushContext pushContext;
    private String serviceUrl;
    private boolean autoApproval;

    public PushStorePersistenceHook(PushStore pushStore, FogwallProvider provider) {
        this.pushStore = pushStore;
        this.provider = provider;
    }

    /** Set the shared push context for accumulating steps from other hooks (e.g., diff generation). */
    public void setPushContext(PushContext pushContext) {
        this.pushContext = pushContext;
    }

    /** Set the dashboard service URL so the rejection message can include a direct link to the push record. */
    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    /** When {@code true}, suppresses dashboard links in user-facing output (auto-approval mode). */
    public void setAutoApproval(boolean autoApproval) {
        this.autoApproval = autoApproval;
    }

    /**
     * Returns a {@link PreReceiveHook} that persists the validation outcome as the push's single lifecycle record. Runs
     * after all validation hooks and before the approval gate.
     *
     * <p>The record is keyed by the push id and created here (not at receive time), matching transparent proxy: a hard
     * policy violation is saved as REJECTED, a clean push as PENDING. The saved id is published as the validation
     * record id so {@link ApprovalPreReceiveHook} — and, after it, {@link #postReceiveHook} — act on this same row.
     */
    public PreReceiveHook validationResultHook(ValidationContext validationContext) {
        return (ReceivePack rp, Collection<ReceiveCommand> commands) -> {
            String pushId = pushContext != null ? pushContext.getPushId() : null;
            if (pushId == null) {
                // Without a push id there is nothing to key the lifecycle record on, so no record is created and
                // validationRecordId stays unset. ApprovalPreReceiveHook rejects the push on that basis —
                // see its fail-closed guard — so this is logged loudly rather than passed over in silence.
                log.error("No pushId in push context - validation result cannot be recorded; approval gate will"
                        + " reject this push");
                return;
            }

            // Read resolvedUser and scmUsername from pushContext — both are set by CheckUserPushPermissionHook
            // (order 150) during the validation hook chain, so they are available here: validationResultHook fires
            // after all validation hooks complete.
            String resolvedUserLate = pushContext.getResolvedUser();
            String scmUsernameLate = pushContext.getScmUsername();

            try {
                // Build the record fresh, keyed by the push id — this is the single insert for the submission
                // (there is no earlier RECEIVED row). buildInitialRecord defaults the status to RECEIVED; the
                // branches below override it to the actual decision (REJECTED or PENDING) before saving.
                PushRecord record = buildInitialRecord(pushId, rp, commands);
                if (resolvedUserLate != null) {
                    record.setResolvedUser(resolvedUserLate);
                }
                if (scmUsernameLate != null) {
                    record.setScmUsername(scmUsernameLate);
                }

                // If PriorPushEnrichmentHook detected a re-push with cached-but-not-forwarded commits,
                // rebuild the commit list using the effective upstream base so the PENDING/REJECTED
                // record shows the complete commit history relative to upstream, not just the local delta.
                enrichCommitsIfNeeded(record, rp, commands);

                // Collect all steps: validation issues + push context (diffs, etc.)
                List<PushStep> steps = new ArrayList<>();
                String recordId = record.getId();

                // Validation issues → reject outright (no human review queue)
                if (validationContext.hasIssues()) {
                    // Build merged step list: passing steps first, then failing steps
                    List<PushStep> allSteps = new ArrayList<>();
                    if (pushContext != null) {
                        for (PushStep step : pushContext.getSteps()) {
                            step.setPushId(recordId);
                            allSteps.add(step);
                        }
                    }
                    int fallbackOrder = 0;
                    for (var issue : validationContext.getIssues()) {
                        int stepOrder = HOOK_STEP_ORDER.getOrDefault(issue.hookName(), fallbackOrder);
                        allSteps.add(PushStep.builder()
                                .pushId(recordId)
                                .stepName(issue.hookName())
                                .stepOrder(stepOrder)
                                .status(StepStatus.FAIL)
                                .content(GitClientUtils.stripColors(issue.detail()))
                                .errorMessage(issue.summary())
                                .build());
                        fallbackOrder++;
                    }

                    allSteps.sort(Comparator.comparingInt(PushStep::getStepOrder));

                    record.setStatus(PushStatus.REJECTED);
                    record.setAutoRejected(true);
                    record.setBlockedMessage(validationContext.getIssues().size() + " validation issue(s) found");
                    record.setSteps(allSteps);
                    if (pushContext != null) {
                        SecretRedactor.redact(record, pushContext.getSecretsToRedact());
                    }
                    pushStore.save(record);
                    if (pushContext != null) pushContext.setValidationRecordId(record.getId());
                    log.debug("Saved validation result record: id={}, status=REJECTED (auto-rejected)", record.getId());

                    // Emit validation summary (passing steps + failing steps, sorted by order)
                    String summary = GitClientUtils.buildValidationSummary(allSteps);
                    if (!summary.isBlank()) {
                        rp.sendMessage(summary);
                    }
                    // Compact rejection block. Policy violations and internal check errors are surfaced
                    // separately so a developer can tell "my commit broke a rule" apart from "a control
                    // could not run and an operator needs to look".
                    var allIssues = validationContext.getIssues();
                    var violations = allIssues.stream().filter(i -> !i.error()).toList();
                    var errors = allIssues.stream()
                            .filter(ValidationContext.ValidationIssue::error)
                            .toList();

                    rp.sendMessage("────────────────────────────────────────");
                    if (!violations.isEmpty()) {
                        rp.sendMessage(color(
                                RED,
                                "" + sym(NO_ENTRY) + "  Push Blocked - " + violations.size() + " validation issue(s)"));
                        for (var issue : violations) {
                            rp.sendMessage("  " + issue.detail());
                        }
                    }
                    if (!errors.isEmpty()) {
                        rp.sendMessage(color(
                                YELLOW,
                                "" + sym(WARNING) + "  Push Blocked - " + errors.size()
                                        + " validation check(s) could not complete (operator attention needed)"));
                        for (var issue : errors) {
                            rp.sendMessage("  " + issue.detail());
                        }
                    }
                    rp.sendMessage("────────────────────────────────────────");

                    if (serviceUrl != null && !autoApproval) {
                        rp.sendMessage(color(
                                CYAN,
                                "" + sym(LINK) + "  View push record: " + serviceUrl + "/dashboard/push/"
                                        + record.getId()));
                    }

                    // Reject all commands immediately - no approval wait
                    String rejectMsg = validationContext.getIssues().size() + " validation issue(s) - see above";
                    for (ReceiveCommand cmd : commands) {
                        if (cmd.getResult() == ReceiveCommand.Result.NOT_ATTEMPTED) {
                            cmd.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, rejectMsg);
                        }
                    }
                    return;
                }

                // No validation issues → PENDING human review
                record.setStatus(PushStatus.PENDING);

                // Steps from push context (diffs, scans, etc.)
                if (pushContext != null) {
                    for (PushStep step : pushContext.getSteps()) {
                        step.setPushId(recordId);
                        steps.add(step);
                    }
                }
                steps.sort(Comparator.comparingInt(PushStep::getStepOrder));
                record.setSteps(steps);

                // Show validation summary before the approval wait message
                String summary = GitClientUtils.buildValidationSummary(steps);
                if (!summary.isBlank()) {
                    rp.sendMessage(summary);
                }
                rp.sendMessage("────────────────────────────────────────");
                if (serviceUrl != null && !autoApproval) {
                    rp.sendMessage(color(
                            CYAN,
                            "" + sym(LINK) + "  View push record: " + serviceUrl + "/dashboard/push/"
                                    + record.getId()));
                }

                pushStore.save(record);
                if (pushContext != null) pushContext.setValidationRecordId(record.getId());
                log.debug("Saved validation result record: id={}, status=PENDING (awaiting review)", record.getId());
            } catch (Exception e) {
                // Swallowed deliberately: this hook must not abort the chain mid-way. The security
                // consequence is handled downstream — the failure leaves validationRecordId unset, and
                // ApprovalPreReceiveHook fails closed on that, so the push is rejected rather than
                // forwarded unapproved. Logged at error because the operator needs to see it: the record
                // that failed to write is also the audit evidence of what happened.
                log.error("Failed to save validation result record - approval gate will reject this push", e);
            }
        };
    }

    /**
     * Returns a {@link PostReceiveHook} that transitions the push's lifecycle record to its terminal forwarding state —
     * FORWARDED on success, ERROR on an upstream failure (spec LC-9(b)). Placed after the forwarding hook.
     *
     * <p>Updates the same record {@link #validationResultHook} created, in place, via
     * {@link PushStore#updateForwardStatus} — no new row is written.
     */
    public PostReceiveHook postReceiveHook() {
        return (ReceivePack rp, Collection<ReceiveCommand> commands) -> {
            String pushId = pushContext != null ? pushContext.getPushId() : null;
            if (pushId == null) return;

            // JGit only passes Result.OK commands to post-receive. An empty list means every command was rejected
            // in pre-receive — nothing was forwarded, so the record keeps the rejected/canceled state it already has.
            if (commands.isEmpty()) {
                log.debug("Skipping forward-status update: no OK commands (push was rejected)");
                return;
            }

            // Command results stay OK in post-receive even when the upstream push fails, because JGit already
            // accepted the objects locally. ForwardingPostReceiveHook records the real outcome in the push context.
            boolean forwardFailed = pushContext != null
                    && pushContext.getSteps().stream()
                            .anyMatch(step ->
                                    "forward".equals(step.getStepName()) && step.getStatus() == StepStatus.FAIL);

            try {
                if (forwardFailed) {
                    String errorMessage = pushContext.getSteps().stream()
                            .filter(step -> "forward".equals(step.getStepName()) && step.getStatus() == StepStatus.FAIL)
                            .map(PushStep::getErrorMessage)
                            .findFirst()
                            .orElse("Forwarding to upstream failed");
                    pushStore.updateForwardStatus(pushId, PushStatus.ERROR, errorMessage);
                    log.info("Push record {} transitioned to ERROR: upstream forwarding failed", pushId);
                } else {
                    pushStore.updateForwardStatus(pushId, PushStatus.FORWARDED, null);
                    log.info("Push record {} transitioned to FORWARDED", pushId);
                }
            } catch (Exception e) {
                log.error("Failed to update forward status for push record {}", pushId, e);
            }
        };
    }

    /**
     * If {@link PriorPushEnrichmentHook} stored an effective upstream base for any ref in the push context, rebuild the
     * commit list on {@code record} using the full range from that base to the new tip. No-op when no enrichment was
     * detected.
     */
    private void enrichCommitsIfNeeded(PushRecord record, ReceivePack rp, Collection<ReceiveCommand> commands) {
        if (pushContext == null) return;
        boolean anyEnriched = false;
        List<PushCommit> enrichedCommits = new ArrayList<>();

        for (ReceiveCommand cmd : commands) {
            if (cmd.getType() == ReceiveCommand.Type.DELETE) continue;
            String effectiveFrom = pushContext.getEffectiveFromId(cmd.getRefName());
            if (effectiveFrom == null) continue;

            anyEnriched = true;
            try {
                List<Commit> range;
                if (effectiveFrom.matches("^0+$")) {
                    range = CommitInspectionService.getCommitRangeUpTo(
                            rp.getRepository(), cmd.getNewId().name());
                } else {
                    range = CommitInspectionService.getCommitRange(
                            rp.getRepository(), effectiveFrom, cmd.getNewId().name());
                }
                for (Commit c : range) {
                    enrichedCommits.add(PushRecordMapper.mapCommit(record.getId(), c));
                }
                if (!range.isEmpty()) {
                    Commit head = range.get(0);
                    if (head.getAuthor() != null) {
                        record.setAuthor(head.getAuthor().getName());
                        record.setAuthorEmail(head.getAuthor().getEmail());
                    }
                    if (head.getCommitter() != null) {
                        record.setCommitter(head.getCommitter().getName());
                        record.setCommitterEmail(head.getCommitter().getEmail());
                    }
                    if (head.getMessage() != null) {
                        record.setMessage(head.getMessage().lines().findFirst().orElse(null));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to enrich commits for {} during re-push", cmd.getRefName(), e);
            }
        }

        if (anyEnriched) {
            record.setCommits(enrichedCommits);
        }
    }

    private PushRecord buildInitialRecord(String pushId, ReceivePack rp, Collection<ReceiveCommand> commands) {
        String providerUri = provider.getUri().toString();
        Repository repo = rp.getRepository();
        PushRecord.PushRecordBuilder builder = PushRecord.builder()
                .id(pushId)
                .status(PushStatus.RECEIVED)
                .provider(provider.getProviderId())
                .url(providerUri)
                .project(provider.getUri().getHost());

        // push_user: always the raw credential username (HTTP Basic or SSH username) — audit artefact.
        // resolved_user: set only when identity resolution succeeded (FK → proxy_users.username).
        String resolvedUser = repo.getConfig().getString("fogwall", null, "resolvedUser");
        String pushUser = repo.getConfig().getString("fogwall", null, "pushUser");
        if (pushUser != null) {
            builder.user(pushUser);
        }
        if (resolvedUser != null) {
            builder.resolvedUser(resolvedUser);
        }
        if (pushContext != null) {
            pushContext.getTransport().auditMethod().ifPresent(builder::method);
            // Back-date the record to the handoff receipt time captured by the factory, rather than letting the
            // timestamp default to now (when this record is first built). Mirrors transparent proxy, which carries
            // GitRequestDetails' parse-time timestamp onto the record.
            if (pushContext.getReceivedAt() != null) {
                builder.timestamp(pushContext.getReceivedAt());
            }
        }

        // Upstream URL and repo name, taken from this request's context and falling back to the shared mirror config.
        String upstreamUrl = pushContext != null ? pushContext.getUpstreamUrl() : null;
        if (upstreamUrl == null) {
            upstreamUrl = repo.getConfig().getString("fogwall", null, "upstreamUrl");
        }
        if (upstreamUrl != null) {
            builder.upstreamUrl(upstreamUrl);
            // Parse repo name from upstream URL (e.g., "https://github.com/owner/repo.git" -> "repo")
            String path = upstreamUrl.replaceAll("\\.git$", "");
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash >= 0) {
                builder.repoName(path.substring(lastSlash + 1));
                // Try to extract owner/slug — strip any scheme + authority (handles http, https, ssh)
                String withoutScheme = path.replaceFirst("\\w+://[^/]+/", "");
                if (withoutScheme.contains("/")) {
                    builder.project(withoutScheme.substring(0, withoutScheme.indexOf('/')));
                    builder.url("/" + withoutScheme);
                }
            }
        }

        // Extract ref info from the first command
        commands.stream().findFirst().ifPresent(cmd -> {
            builder.branch(cmd.getRefName());
            builder.commitFrom(cmd.getOldId().name());
            builder.commitTo(cmd.getNewId().name());
        });

        // Try to extract commit details from the repository
        List<PushCommit> commits = new ArrayList<>();
        for (ReceiveCommand cmd : commands) {
            if (cmd.getType() == ReceiveCommand.Type.DELETE) continue;
            try {
                String toCommit = cmd.getNewId().name();
                if (ObjectId.zeroId().equals(cmd.getOldId())) {
                    // New branch - just get tip commit
                    Commit tip = CommitInspectionService.getCommitDetails(repo, toCommit);
                    commits.add(PushRecordMapper.mapCommit(pushId, tip));
                    if (tip.getAuthor() != null) {
                        builder.author(tip.getAuthor().getName());
                        builder.authorEmail(tip.getAuthor().getEmail());
                    }
                    if (tip.getCommitter() != null) {
                        builder.committer(tip.getCommitter().getName());
                        builder.committerEmail(tip.getCommitter().getEmail());
                    }
                    if (tip.getMessage() != null) {
                        builder.message(tip.getMessage().lines().findFirst().orElse(null));
                    }
                } else {
                    List<Commit> range = CommitInspectionService.getCommitRange(
                            repo, cmd.getOldId().name(), toCommit);
                    for (Commit c : range) {
                        commits.add(PushRecordMapper.mapCommit(pushId, c));
                    }
                    // Use the latest commit's author, committer, and headline message
                    if (!range.isEmpty()) {
                        Commit head = range.get(0);
                        if (head.getAuthor() != null) {
                            builder.author(head.getAuthor().getName());
                            builder.authorEmail(head.getAuthor().getEmail());
                        }
                        if (head.getCommitter() != null) {
                            builder.committer(head.getCommitter().getName());
                            builder.committerEmail(head.getCommitter().getEmail());
                        }
                        if (head.getMessage() != null) {
                            builder.message(
                                    head.getMessage().lines().findFirst().orElse(null));
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to extract commit details for {}", cmd.getRefName(), e);
            }
        }
        builder.commits(commits);

        return builder.build();
    }
}
