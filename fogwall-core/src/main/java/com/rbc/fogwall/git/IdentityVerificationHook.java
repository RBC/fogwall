package com.rbc.fogwall.git;

import static com.rbc.fogwall.git.GitClientUtils.AnsiColor.*;
import static com.rbc.fogwall.git.GitClientUtils.SymbolCodes.*;
import static com.rbc.fogwall.git.GitClientUtils.sym;

import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.db.model.PushStep;
import com.rbc.fogwall.db.model.StepStatus;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.service.PushIdentityResolver;
import com.rbc.fogwall.user.UserEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

/**
 * Pre-receive hook that verifies commit author and committer emails match a registered email of the authenticated push
 * user. Runs in store-and-forward mode at order 160 — after {@link CheckUserPushPermissionHook} (150) has confirmed the
 * user exists and is authorised, but before content-validation hooks (200+).
 *
 * <p>Behaviour is controlled by {@link CommitConfig#getIdentityVerification()}:
 *
 * <ul>
 *   <li>{@code STRICT} — blocks the push and reports all mismatching commits.
 *   <li>{@code WARN} — sends yellow sideband warnings but allows the push through (default).
 *   <li>{@code OFF} — skips the check entirely.
 * </ul>
 *
 * <p>When no {@link PushIdentityResolver} is configured (open/permissive mode) the hook is a no-op.
 */
@Slf4j
public class IdentityVerificationHook implements FogwallHook {

    static final int ORDER = 160;
    static final String STEP_NAME = "identityVerification";

    private final PushIdentityResolver identityResolver;
    private final CommitConfig.IdentityVerificationConfig config;
    private final ValidationContext validationContext;
    private final PushContext pushContext;
    private final FogwallProvider provider;

    public IdentityVerificationHook(
            PushIdentityResolver identityResolver,
            CommitConfig.IdentityVerificationConfig config,
            ValidationContext validationContext,
            PushContext pushContext,
            FogwallProvider provider) {
        this.identityResolver = identityResolver;
        this.config = config != null
                ? config
                : CommitConfig.IdentityVerificationConfig.builder().build();
        this.validationContext = validationContext;
        this.pushContext = pushContext;
        this.provider = provider;
    }

    @Override
    public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
        if (config.isEffectivelyOff()) {
            log.debug("Identity verification disabled (committer=off, author=off)");
            recordPass();
            return;
        }

        if (identityResolver == null) {
            log.debug("No identity resolver configured — skipping identity verification (open mode)");
            recordPass();
            return;
        }

        // Transport pre-authenticated the user (SSH public key) — no token available for SCM verification.
        if (pushContext.getTransport().preAuthenticatedUser().isPresent()) {
            log.debug(
                    "Pre-authenticated push ({}) — skipping token identity verification",
                    pushContext.getTransport().name());
            recordPass();
            return;
        }

        String pushUser = pushContext.getPushUser();
        String pushToken = pushContext.getPushToken();

        if (pushUser == null || pushUser.isEmpty()) {
            log.debug("No push user in repo config — skipping identity verification");
            recordPass();
            return;
        }

        Optional<UserEntry> resolved = identityResolver.resolve(provider, pushUser, pushToken);
        if (resolved.isEmpty()) {
            log.debug("Push user '{}' could not be resolved — skipping identity verification", pushUser);
            return;
        }

        UserEntry user = resolved.get();
        List<String> registeredEmails = user.getEmails() != null ? user.getEmails() : List.of();
        Repository repo = rp.getRepository();
        List<String> blockingViolations = new ArrayList<>();
        List<String> warnViolations = new ArrayList<>();

        for (ReceiveCommand cmd : commands) {
            if (cmd.getType() == ReceiveCommand.Type.DELETE) continue;
            try {
                for (Commit commit : getCommits(repo, cmd)) {
                    String sha = abbrev(commit.getSha());

                    if (config.getCommitter() != CommitConfig.IdentityVerificationMode.OFF
                            && commit.getCommitter() != null) {
                        String email = commit.getCommitter().getEmail();
                        if (email != null && !registeredEmails.contains(email)) {
                            String msg = "Unrecognised committer email: <" + email + "> (commit " + sha
                                    + ") — not in proxy user registry";
                            if (config.getCommitter() == CommitConfig.IdentityVerificationMode.STRICT) {
                                blockingViolations.add(msg);
                            } else {
                                warnViolations.add(msg);
                            }
                        }
                    }

                    if (config.getAuthor() != CommitConfig.IdentityVerificationMode.OFF && commit.getAuthor() != null) {
                        String email = commit.getAuthor().getEmail();
                        if (email != null && !registeredEmails.contains(email)) {
                            String msg = "Unrecognised author email: <" + email + "> (commit " + sha
                                    + ") — not in proxy user registry";
                            if (config.getAuthor() == CommitConfig.IdentityVerificationMode.STRICT) {
                                blockingViolations.add(msg);
                            } else {
                                warnViolations.add(msg);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to verify identity for {}", cmd.getRefName(), e);
            }
        }

        if (blockingViolations.isEmpty() && warnViolations.isEmpty()) {
            log.debug("Identity verification passed for push user '{}'", user.getUsername());
            recordPass();
            return;
        }

        if (!blockingViolations.isEmpty()) {
            List<String> allViolations = new ArrayList<>(blockingViolations);
            allViolations.addAll(warnViolations);
            log.warn(
                    "Identity verification failed for push user '{}': {} violation(s)",
                    user.getUsername(),
                    allViolations.size());
            String detail = GitClientUtils.format(
                    sym(NO_ENTRY) + "  Push Blocked — Commit Identity Mismatch",
                    String.join("\n", allViolations),
                    RED,
                    null);
            validationContext.addIssue(
                    STEP_NAME, "Commit identity does not match push user " + user.getUsername(), detail);
        } else {
            log.warn(
                    "Identity verification warnings for push user '{}': {} mismatch(es)",
                    user.getUsername(),
                    warnViolations.size());
            for (String v : warnViolations) {
                rp.sendMessage(GitClientUtils.color(YELLOW, sym(WARNING) + "  " + v));
            }
            pushContext.addStep(PushStep.builder()
                    .stepName(STEP_NAME)
                    .stepOrder(ORDER)
                    .status(StepStatus.PASS)
                    .content(String.join("\n", warnViolations))
                    .build());
        }
    }

    private void recordPass() {
        pushContext.addStep(PushStep.builder()
                .stepName(STEP_NAME)
                .stepOrder(ORDER)
                .status(StepStatus.PASS)
                .build());
    }

    private static String abbrev(String sha) {
        if (sha == null) return "?";
        return sha.substring(0, Math.min(7, sha.length()));
    }

    private static List<Commit> getCommits(Repository repo, ReceiveCommand cmd) throws Exception {
        if (ObjectId.zeroId().equals(cmd.getOldId())) {
            return List.of(CommitInspectionService.getCommitDetails(
                    repo, cmd.getNewId().name()));
        }
        return CommitInspectionService.getCommitRange(
                repo, cmd.getOldId().name(), cmd.getNewId().name());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public String getName() {
        return "IdentityVerificationHook";
    }
}
