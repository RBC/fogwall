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
 * Pre-receive hook enforcing the <b>commit attribution policy</b>: each pushed commit's committer and/or author email
 * must be a registered email of the resolved push identity. This inspects client-controlled commit metadata — it is a
 * policy conformance check on commit provenance, <em>not</em> authentication of the pusher (that is
 * {@link CheckUserPushPermissionHook} at order 150). Runs in server mode at order 160, before content-validation hooks
 * (200+).
 *
 * <p>Behaviour is controlled per field (committer, author) by {@link CommitConfig#getAttributionPolicy()}:
 *
 * <ul>
 *   <li>{@code STRICT} — blocks the push and reports all mismatching commits.
 *   <li>{@code WARN} — sends yellow sideband warnings but allows the push through (committer default).
 *   <li>{@code OFF} — skips the check entirely (author default, so rebased/imported commits are not blocked).
 * </ul>
 *
 * <p>Applies to both transports: HTTP pushes resolve the identity from the token; SSH pushes use the public-key
 * pre-authenticated identity. When no {@link PushIdentityResolver} is configured and the push is not pre-authenticated
 * (open/permissive mode) the hook is a no-op.
 */
@Slf4j
public class CommitAttributionPolicyHook implements FogwallHook {

    static final int ORDER = 160;
    static final String STEP_NAME = "commitAttributionPolicy";

    private final PushIdentityResolver identityResolver;
    private final CommitConfig.CommitAttributionPolicyConfig config;
    private final ValidationContext validationContext;
    private final PushContext pushContext;
    private final FogwallProvider provider;

    public CommitAttributionPolicyHook(
            PushIdentityResolver identityResolver,
            CommitConfig.CommitAttributionPolicyConfig config,
            ValidationContext validationContext,
            PushContext pushContext,
            FogwallProvider provider) {
        this.identityResolver = identityResolver;
        this.config = config != null
                ? config
                : CommitConfig.CommitAttributionPolicyConfig.builder().build();
        this.validationContext = validationContext;
        this.pushContext = pushContext;
        this.provider = provider;
    }

    @Override
    public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
        if (config.isEffectivelyOff()) {
            log.debug("Commit attribution policy disabled (committer=off, author=off)");
            recordPass();
            return;
        }

        // Resolve the push identity whose registered emails the commit emails are checked against. SSH pushes are
        // pre-authenticated by public key at connection time (no token); HTTP pushes are resolved from the token. The
        // check itself is transport-independent.
        UserEntry user;
        var preAuthenticated = pushContext.getTransport().preAuthenticatedUser();
        if (preAuthenticated.isPresent()) {
            user = preAuthenticated.get();
        } else {
            if (identityResolver == null) {
                log.debug("No identity resolver configured — skipping commit attribution policy (open mode)");
                recordPass();
                return;
            }
            String pushUser = pushContext.getPushUser();
            String pushToken = pushContext.getPushToken();
            if (pushUser == null || pushUser.isEmpty()) {
                log.debug("No push user in repo config — skipping commit attribution policy");
                recordPass();
                return;
            }
            Optional<UserEntry> resolved = identityResolver.resolve(provider, pushUser, pushToken);
            if (resolved.isEmpty()) {
                log.debug("Push user '{}' could not be resolved — skipping commit attribution policy", pushUser);
                return;
            }
            user = resolved.get();
        }
        List<String> registeredEmails = user.getEmails() != null ? user.getEmails() : List.of();
        Repository repo = rp.getRepository();
        List<String> blockingViolations = new ArrayList<>();
        List<String> warnViolations = new ArrayList<>();
        boolean hadError = false;

        for (ReceiveCommand cmd : commands) {
            if (cmd.getType() == ReceiveCommand.Type.DELETE) continue;
            try {
                for (Commit commit : getCommits(repo, cmd)) {
                    String sha = abbrev(commit.getSha());

                    if (config.getCommitter() != CommitConfig.CommitAttributionPolicyMode.OFF
                            && commit.getCommitter() != null) {
                        String email = commit.getCommitter().getEmail();
                        if (email != null && !registeredEmails.contains(email)) {
                            String msg = "Unrecognised committer email: <" + email + "> (commit " + sha
                                    + ") — not in proxy user registry";
                            if (config.getCommitter() == CommitConfig.CommitAttributionPolicyMode.STRICT) {
                                blockingViolations.add(msg);
                            } else {
                                warnViolations.add(msg);
                            }
                        }
                    }

                    if (config.getAuthor() != CommitConfig.CommitAttributionPolicyMode.OFF
                            && commit.getAuthor() != null) {
                        String email = commit.getAuthor().getEmail();
                        if (email != null && !registeredEmails.contains(email)) {
                            String msg = "Unrecognised author email: <" + email + "> (commit " + sha
                                    + ") — not in proxy user registry";
                            if (config.getAuthor() == CommitConfig.CommitAttributionPolicyMode.STRICT) {
                                blockingViolations.add(msg);
                            } else {
                                warnViolations.add(msg);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Fail closed: a commit attribution policy check that cannot run must block the push, not silently
                // pass.
                log.error("Failed to check commit attribution policy for {}", cmd.getRefName(), e);
                validationContext.addError(
                        STEP_NAME,
                        "commit attribution policy could not complete for " + cmd.getRefName(),
                        "Commit attribution policy error: " + e.getMessage());
                hadError = true;
            }
        }

        if (blockingViolations.isEmpty() && warnViolations.isEmpty()) {
            if (!hadError) {
                log.debug("Commit attribution policy passed for push user '{}'", user.getUsername());
                recordPass();
            }
            // else: an error was recorded to the validation context, which blocks the push — do not record PASS.
            return;
        }

        if (!blockingViolations.isEmpty()) {
            List<String> allViolations = new ArrayList<>(blockingViolations);
            allViolations.addAll(warnViolations);
            log.warn(
                    "Commit attribution policy failed for push user '{}': {} violation(s)",
                    user.getUsername(),
                    allViolations.size());
            String detail = GitClientUtils.format(
                    sym(NO_ENTRY) + "  Push Blocked — Unrecognised Commit Email",
                    String.join("\n", allViolations),
                    RED,
                    null);
            validationContext.addIssue(
                    STEP_NAME, "Commit email not registered to push user " + user.getUsername(), detail);
        } else {
            log.warn(
                    "Commit attribution policy warnings for push user '{}': {} mismatch(es)",
                    user.getUsername(),
                    warnViolations.size());
            for (String v : warnViolations) {
                rp.sendMessage(GitClientUtils.color(YELLOW, sym(WARNING) + "  " + v));
            }
            pushContext.addStep(PushStep.builder()
                    .stepName(STEP_NAME)
                    .stepOrder(ORDER)
                    .status(StepStatus.WARN)
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
        return "CommitAttributionPolicyHook";
    }
}
