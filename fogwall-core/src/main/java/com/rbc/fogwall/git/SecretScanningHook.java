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
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

/**
 * S&amp;F-mode secret scanning hook. Runs {@code gitleaks git} directly against the JGit bare repository so that
 * path-based allowlists and per-file context in gitleaks rules are applied correctly.
 *
 * <p>The commit range is derived from each {@link ReceiveCommand}: new-branch pushes use {@code commitTo --not --all}
 * to limit scanning to commits not already reachable from any existing ref; branch updates use
 * {@code commitFrom..commitTo}.
 *
 * <p>If the scanner is unavailable and secret scanning is enabled, the push is blocked (fail-closed). If scanning is
 * disabled the hook records a SKIPPED step.
 */
@Slf4j
@RequiredArgsConstructor
public class SecretScanningHook implements FogwallHook {

    private static final int ORDER = 340;
    private static final String STEP_NAME = "scanSecrets";
    private static final String REMEDIATION_HINT =
            "→ Rotate any exposed credentials and remove the secret from your commit history before pushing.";

    private final SecretScanConfig config;
    private final ValidationContext validationContext;
    private final PushContext pushContext;
    private final GitleaksRunner runner;

    public SecretScanningHook(SecretScanConfig config, ValidationContext validationContext, PushContext pushContext) {
        this(config, validationContext, pushContext, new GitleaksRunner());
    }

    @Override
    public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
        Path repoDir = rp.getRepository().getDirectory().toPath();

        List<Violation> allViolations = new ArrayList<>();
        boolean scannerUnavailable = false;

        for (ReceiveCommand cmd : commands) {
            if (cmd.getType() == ReceiveCommand.Type.DELETE) {
                continue;
            }

            String commitFrom = cmd.getOldId().name();
            String commitTo = cmd.getNewId().name();

            Optional<List<GitleaksRunner.Finding>> result = runner.scanGit(repoDir, commitFrom, commitTo, config);

            if (result.isEmpty()) {
                scannerUnavailable = true;
                continue;
            }

            for (GitleaksRunner.Finding f : result.get()) {
                String msg = f.toMessage();
                allViolations.add(new Violation(msg, msg, sym(CROSS_MARK) + "  " + msg + "\n" + REMEDIATION_HINT));
            }
        }

        if (scannerUnavailable) {
            if (config.isEnabled()) {
                String msg = "Secret scanning failed — scanner error or unavailable. "
                        + "Push blocked because secret-scan is enabled. Check server logs for details.";
                allViolations.add(new Violation(msg, msg, sym(CROSS_MARK) + "  " + msg + "\n" + REMEDIATION_HINT));
            } else {
                pushContext.addStep(PushStep.builder()
                        .stepName(STEP_NAME)
                        .stepOrder(ORDER)
                        .status(StepStatus.SKIPPED)
                        .build());
                return;
            }
        }

        if (allViolations.isEmpty()) {
            pushContext.addStep(PushStep.builder()
                    .stepName(STEP_NAME)
                    .stepOrder(ORDER)
                    .status(StepStatus.PASS)
                    .build());
            return;
        }

        for (Violation v : allViolations) {
            validationContext.addIssue(STEP_NAME, v.reason(), v.formattedDetail());
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public String getName() {
        return "SecretScanningHook";
    }
}
