package com.rbc.fogwall.git;

import static com.rbc.fogwall.git.GitClientUtils.SymbolCodes.*;
import static com.rbc.fogwall.git.GitClientUtils.sym;

import com.rbc.fogwall.config.SecretScanConfig;
import com.rbc.fogwall.db.model.PushStep;
import com.rbc.fogwall.db.model.StepStatus;
import com.rbc.fogwall.validation.Violation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

/**
 * server-mode secret scanning hook. Runs {@code gitleaks git} directly against the JGit bare repository so that
 * path-based allowlists and per-file context in gitleaks rules are applied correctly.
 *
 * <p>The commit range is derived from each {@link ReceiveCommand}: new-branch pushes use {@code commitTo --not --all}
 * to limit scanning to commits not already reachable from any existing ref; branch updates use
 * {@code commitFrom..commitTo}.
 *
 * <p>With {@code include-metadata}, the messages of the commits in those ranges and of any pushed annotated tag are
 * also scanned, in one {@link GitleaksRunner#scanMessages} run per push.
 *
 * <p>If the scanner is unavailable and secret scanning is enabled, the push is blocked (fail-closed). If scanning is
 * disabled the hook records a SKIPPED step.
 */
@Slf4j
@RequiredArgsConstructor
public final class SecretScanningHook implements MandatoryFogwallHook {

    private static final String PATCH_REMEDIATION_HINT =
            "→ Rotate any exposed credentials and remove the secret from your commit history before pushing.";
    private static final String MESSAGE_REMEDIATION_HINT =
            "→ Rotate any exposed credentials, then reword the commit or re-create the tag before pushing.";

    private final SecretScanConfig config;
    private final ValidationContext validationContext;
    private final PushContext pushContext;
    private final GitleaksRunner runner;

    public SecretScanningHook(SecretScanConfig config, ValidationContext validationContext, PushContext pushContext) {
        this(config, validationContext, pushContext, new GitleaksRunner());
    }

    @Override
    public boolean enabled() {
        return config.isEnabled();
    }

    @Override
    public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
        Path repoDir = rp.getRepository().getDirectory().toPath();
        // In server mode the push is received into a quarantine whose objects live outside the mirror's own object
        // dir. gitleaks shells out to git against the mirror (repoDir), so without exposing the quarantine as an
        // alternate the pushed commits are invisible and the scan silently passes. Null when no quarantine is active
        // (objects already in the mirror), where repoDir alone resolves them.
        Path scanObjectDir = pushContext.getScanObjectDirectory();

        List<Violation> allViolations = new ArrayList<>();
        boolean scannerUnavailable = false;
        // Keyed by full commit SHA or tag ref name, so a commit reachable from two pushed refs is scanned once.
        Map<String, GitleaksRunner.ScannedMessage> messages = new LinkedHashMap<>();

        for (ReceiveCommand cmd : commands) {
            if (cmd.getType() == ReceiveCommand.Type.DELETE) {
                continue;
            }

            String commitTo = cmd.getNewId().name();

            // Use the effective upstream base when PriorPushEnrichmentHook detected cached-but-not-forwarded
            // commits, so the secret scan covers the full range relative to upstream.
            String effectiveFrom = pushContext.getEffectiveFromId(cmd.getRefName());
            boolean hasNonZeroEffectiveBase = effectiveFrom != null && !effectiveFrom.matches("^0+$");
            String commitFrom =
                    hasNonZeroEffectiveBase ? effectiveFrom : cmd.getOldId().name();

            Optional<List<GitleaksRunner.Finding>> result =
                    runner.scanGit(repoDir, scanObjectDir, commitFrom, commitTo, config);

            if (result.isEmpty()) {
                scannerUnavailable = true;
            } else {
                addFindings(result.get(), allViolations, PATCH_REMEDIATION_HINT);
            }

            if (config.isIncludeMetadata()) {
                try {
                    collectMessages(rp.getRepository(), cmd, commitFrom, messages);
                } catch (Exception e) {
                    log.error("Failed to read commit and tag messages for secret scanning on {}", cmd.getRefName(), e);
                    scannerUnavailable = true;
                }
            }
        }

        if (!messages.isEmpty()) {
            Optional<List<GitleaksRunner.Finding>> result = runner.scanMessages(List.copyOf(messages.values()), config);
            if (result.isEmpty()) {
                scannerUnavailable = true;
            } else {
                addFindings(result.get(), allViolations, MESSAGE_REMEDIATION_HINT);
            }
        }

        if (scannerUnavailable) {
            String msg = "Secret scanning failed — scanner error or unavailable. "
                    + "Push blocked because secret-scan is enabled. Check server logs for details.";
            allViolations.add(new Violation(msg, msg, sym(CROSS_MARK) + "  " + msg + "\n" + PATCH_REMEDIATION_HINT));
        }

        if (allViolations.isEmpty()) {
            pushContext.addStep(PushStep.builder()
                    .stepName(getStepName())
                    .stepOrder(displayOrder())
                    .status(StepStatus.PASS)
                    .build());
            return;
        }

        for (Violation v : allViolations) {
            validationContext.addIssue(PushStepKind.SECRET_SCAN, v.reason(), v.formattedDetail());
        }
    }

    private void addFindings(List<GitleaksRunner.Finding> findings, List<Violation> violations, String hint) {
        for (GitleaksRunner.Finding f : findings) {
            String msg = f.toMessage();
            violations.add(new Violation(msg, msg, sym(CROSS_MARK) + "  " + msg + "\n" + hint));
            // Raw secret value, for redacting the stored record only - never used in a message. See SecretRedactor.
            if (f.getSecret() != null && !f.getSecret().isBlank()) {
                pushContext.addSecretsToRedact(List.of(f.getSecret()));
            }
        }
    }

    /**
     * Adds the message of every commit {@code cmd} introduces (the same range the patch scan covers) and, for an
     * annotated tag, the tag's own message.
     */
    private static void collectMessages(
            Repository repo, ReceiveCommand cmd, String commitFrom, Map<String, GitleaksRunner.ScannedMessage> messages)
            throws Exception {
        for (Commit commit : CommitInspectionService.getCommitRange(
                repo, commitFrom, cmd.getNewId().name())) {
            messages.putIfAbsent(
                    commit.getSha(), GitleaksRunner.ScannedMessage.ofCommit(commit.getSha(), commit.getMessage()));
        }
        CommitInspectionService.getAnnotatedTagMessage(repo, cmd.getNewId())
                .ifPresent(text -> messages.putIfAbsent(
                        cmd.getRefName(), GitleaksRunner.ScannedMessage.ofTag(cmd.getRefName(), text)));
    }

    @Override
    public LifecycleStage stage() {
        return LifecycleStage.MANDATORY_PROCESSING;
    }

    @Override
    public String getName() {
        return "SecretScanningHook";
    }

    @Override
    public Optional<PushStepKind> stepKind() {
        return Optional.of(PushStepKind.SECRET_SCAN);
    }
}
