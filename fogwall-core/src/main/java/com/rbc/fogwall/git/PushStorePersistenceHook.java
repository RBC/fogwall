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
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.*;

/**
 * Combined pre-receive and post-receive hook that persists push records to the {@link PushStore} during
 * store-and-forward processing.
 *
 * <p>The pre-receive hook creates the initial record with status RECEIVED. It should be placed at the beginning of the
 * hook chain. It also stores the push ID in the ReceivePack so downstream hooks and the post-receive hook can update
 * it.
 *
 * <p>The post-receive hook updates the record based on the final command results (FORWARDED, PENDING, or ERROR).
 */
@Slf4j
public class PushStorePersistenceHook {

    /** ReceivePack message key used to pass the push ID between pre/post hooks. */
    private static final String PUSH_ID_KEY = "fogwall.pushId";

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

    /** Returns a {@link PreReceiveHook} that creates the initial push record. Should be the first hook in the chain. */
    public PreReceiveHook preReceiveHook() {
        return (ReceivePack rp, Collection<ReceiveCommand> commands) -> {
            String pushId = UUID.randomUUID().toString();
            pushContext.setPushId(pushId);

            try {
                PushRecord record = buildInitialRecord(pushId, rp, commands);
                pushStore.save(record);
                log.info("Created push record: id={}, repo={}", pushId, record.getUrl());
            } catch (Exception e) {
                log.error("Failed to create push record", e);
            }
        };
    }

    /**
     * Returns a {@link PreReceiveHook} that captures the validation results after all validation hooks have run. Should
     * be placed after all validation hooks but before the forwarding post-receive hook.
     *
     * <p>Creates a new event-log record for the validation outcome, linked to the original push via the same upstream
     * URL and commit range.
     */
    public PreReceiveHook validationResultHook(ValidationContext validationContext) {
        return (ReceivePack rp, Collection<ReceiveCommand> commands) -> {
            String pushId = pushContext != null ? pushContext.getPushId() : null;
            if (pushId == null) return;

            // Read resolvedUser and scmUsername from pushContext — both are set by CheckUserPushPermissionHook
            // (order 150) after preReceiveHook() ran, so they were not available when the RECEIVED record
            // was written. validationResultHook fires after all validation hooks complete.
            String resolvedUserLate = pushContext.getResolvedUser();
            String scmUsernameLate = pushContext.getScmUsername();

            try {
                pushStore.findById(pushId).ifPresent(initial -> {
                    PushRecord record = copyBase(initial);
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
                        log.debug(
                                "Saved validation result record: id={}, status=REJECTED (auto-rejected)",
                                record.getId());

                        // Emit validation summary (passing steps + failing steps, sorted by order)
                        String summary = GitClientUtils.buildValidationSummary(allSteps);
                        if (!summary.isBlank()) {
                            rp.sendMessage(summary);
                        }
                        // Compact rejection block. Policy violations and internal check errors are surfaced
                        // separately so a developer can tell "my commit broke a rule" apart from "a control
                        // could not run and an operator needs to look".
                        var allIssues = validationContext.getIssues();
                        var violations =
                                allIssues.stream().filter(i -> !i.error()).toList();
                        var errors = allIssues.stream()
                                .filter(ValidationContext.ValidationIssue::error)
                                .toList();

                        rp.sendMessage("────────────────────────────────────────");
                        if (!violations.isEmpty()) {
                            rp.sendMessage(color(
                                    RED,
                                    "" + sym(NO_ENTRY) + "  Push Blocked - " + violations.size()
                                            + " validation issue(s)"));
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
                                    "" + sym(LINK) + "  View push record: " + serviceUrl + "/push/" + record.getId()));
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
                                "" + sym(LINK) + "  View push record: " + serviceUrl + "/push/" + record.getId()));
                    }

                    pushStore.save(record);
                    if (pushContext != null) pushContext.setValidationRecordId(record.getId());
                    log.debug(
                            "Saved validation result record: id={}, status=PENDING (awaiting review)", record.getId());
                });
            } catch (Exception e) {
                log.error("Failed to save validation result record", e);
            }
        };
    }

    /**
     * Returns a {@link PostReceiveHook} that records the forwarding outcome. Should be placed after the forwarding
     * hook.
     *
     * <p>Creates a new event-log record for the forwarding result.
     */
    public PostReceiveHook postReceiveHook() {
        return (ReceivePack rp, Collection<ReceiveCommand> commands) -> {
            String pushId = pushContext != null ? pushContext.getPushId() : null;
            if (pushId == null) return;

            try {
                pushStore.findById(pushId).ifPresent(initial -> {
                    // JGit only passes Result.OK commands to post-receive.
                    // An empty list means all commands were rejected by pre-receive - nothing to record.
                    if (commands.isEmpty()) {
                        log.debug("Skipping post-receive record: no OK commands (push was rejected)");
                        return;
                    }

                    // Check if the forwarding step failed (upstream rejected the push).
                    // Command results stay OK in post-receive even when the upstream push fails,
                    // because JGit already accepted the objects locally. The forwarding outcome
                    // is recorded in pushContext by ForwardingPostReceiveHook.
                    boolean forwardFailed = pushContext != null
                            && pushContext.getSteps().stream()
                                    .anyMatch(step -> "forward".equals(step.getStepName())
                                            && step.getStatus() == StepStatus.FAIL);

                    PushRecord record = copyBase(initial);
                    if (forwardFailed) {
                        record.setStatus(PushStatus.ERROR);
                        pushContext.getSteps().stream()
                                .filter(step ->
                                        "forward".equals(step.getStepName()) && step.getStatus() == StepStatus.FAIL)
                                .findFirst()
                                .ifPresent(step -> record.setErrorMessage(step.getErrorMessage()));
                    } else {
                        record.setStatus(PushStatus.FORWARDED);
                    }

                    pushStore.save(record);
                    log.info("Saved forwarding result record: id={}, status={}", record.getId(), record.getStatus());
                });
            } catch (Exception e) {
                log.error("Failed to save forwarding result record", e);
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

    /**
     * Create a new record that copies the base fields (repo, branch, commits, author) from an existing record but with
     * a fresh ID and timestamp. Used for event-log style persistence where each state transition is a separate row.
     */
    private PushRecord copyBase(PushRecord source) {
        return PushRecord.builder()
                .url(source.getUrl())
                .upstreamUrl(source.getUpstreamUrl())
                .provider(source.getProvider())
                .project(source.getProject())
                .repoName(source.getRepoName())
                .branch(source.getBranch())
                .commitFrom(source.getCommitFrom())
                .commitTo(source.getCommitTo())
                .message(source.getMessage())
                .author(source.getAuthor())
                .authorEmail(source.getAuthorEmail())
                .committer(source.getCommitter())
                .committerEmail(source.getCommitterEmail())
                .user(source.getUser())
                .userEmail(source.getUserEmail())
                .resolvedUser(source.getResolvedUser())
                .scmUsername(source.getScmUsername())
                .method(source.getMethod())
                .commits(source.getCommits())
                .build();
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
        }

        // Extract upstream URL and repo name from repo config (set by StoreAndForwardRepositoryResolver)
        String upstreamUrl = repo.getConfig().getString("fogwall", null, "upstreamUrl");
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
