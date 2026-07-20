package com.rbc.fogwall.ssh;

import com.rbc.fogwall.db.UrlRuleRegistry;
import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.git.LocalRepositoryCache;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.servlet.filter.UrlRuleEvaluator;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.agent.SshAgent;
import org.apache.sshd.common.channel.exception.SshChannelClosedException;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.UploadPack;

/**
 * MINA SSHD {@link Command} that handles {@code git-upload-pack} (clone/fetch) over SSH.
 *
 * <p>Mirrors {@link SshGitReceiveCommand}'s connection handling — resolves the route, syncs the local mirror from
 * upstream via the client's forwarded SSH agent (see {@link SshUpstreamTransport}), then serves the fetch from the
 * synced local repo.
 *
 * <p>URL allow/deny rules are enforced explicitly here via {@link UrlRuleEvaluator} before syncing/serving. HTTP
 * enforces this for both push and fetch via {@code UrlRuleAggregateFilter}, a servlet filter — SSH has no equivalent
 * filter chain, so this is the only enforcement point for fetch on this transport (push already gets it via
 * {@code RepositoryUrlRuleHook} in the shared JGit hook chain). No other push-side hook applies here: content
 * validation and approval gating are push-specific, and fetch has no fogwall-side permission grant on either transport
 * today — access is delegated to the upstream SCM via the forwarded agent, same as HTTP delegates via client-forwarded
 * credentials.
 */
@Slf4j
public class SshGitUploadCommand implements Command {

    private final String repoPath;
    private final FogwallProvider provider;
    private final LocalRepositoryCache cache;
    private final FogwallProxyAgentFactory agentFactory;
    private final UrlRuleRegistry urlRuleRegistry;

    private InputStream in;
    private OutputStream out;
    private OutputStream err;
    private ExitCallback exitCallback;

    SshGitUploadCommand(
            String repoPath,
            FogwallProvider provider,
            LocalRepositoryCache cache,
            FogwallProxyAgentFactory agentFactory,
            UrlRuleRegistry urlRuleRegistry) {
        this.repoPath = repoPath;
        this.provider = provider;
        this.cache = cache;
        this.agentFactory = agentFactory;
        this.urlRuleRegistry = urlRuleRegistry;
    }

    @Override
    public void setInputStream(InputStream in) {
        this.in = in;
    }

    @Override
    public void setOutputStream(OutputStream out) {
        this.out = out;
    }

    @Override
    public void setErrorStream(OutputStream err) {
        this.err = err;
    }

    @Override
    public void setExitCallback(ExitCallback callback) {
        this.exitCallback = callback;
    }

    @Override
    public void start(ChannelSession channel, Environment env) {
        String sshUser = channel.getSession().getUsername();
        // SSH_AUTH_SOCK is set on the channel env by MINA SSHD when the client's ssh -A forwarding
        // channel is established. Capture it here before handing off to the worker thread.
        String authSocket = env.getEnv().get(SshAgent.SSH_AUTHSOCKET_ENV_NAME);
        Thread worker = new Thread(() -> runUploadPack(sshUser, authSocket), "ssh-git-upload-" + repoPath);
        worker.setDaemon(true);
        worker.start();
    }

    private void runUploadPack(String sshUser, String authSocket) {
        int exitCode = 0;
        SshAgent agent = null;
        try {
            SshGitReceiveCommand.RepoRoute route;
            try {
                route = SshGitReceiveCommand.resolveRoute(repoPath, provider.getUri());
            } catch (IllegalArgumentException e) {
                writeError(e.getMessage());
                exitCode = 128;
                return;
            }
            String owner = route.owner();
            String repo = route.repo();
            String upstreamUrl = route.upstreamUrl();

            // Mirrors UrlRuleAggregateFilter's fetch-side gate — the only enforcement point for URL allow/deny
            // rules on this transport, since SSH has no servlet filter chain.
            var evaluator = new UrlRuleEvaluator(urlRuleRegistry, provider);
            String slug = owner + "/" + repo;
            UrlRuleEvaluator.Result result = evaluator.evaluate(slug, owner, repo, HttpOperation.FETCH);
            if (!(result instanceof UrlRuleEvaluator.Result.Allowed allowed)) {
                String reason = result instanceof UrlRuleEvaluator.Result.Denied denied
                        ? "Repository blocked by deny rule: " + denied.ruleId()
                        : "Repository not in allow list";
                log.debug("SSH fetch blocked for {}: {}", repoPath, reason);
                writeError(reason);
                exitCode = 128;
                return;
            } else {
                log.debug("SSH fetch allowed by rule: {}", allowed.ruleId());
            }

            // Look up the forwarded SSH agent by the proxy ID that MINA SSHD stored in the
            // channel env as SSH_AUTH_SOCK when the client's ssh -A request was processed.
            agent = agentFactory.getForwardedAgent(authSocket);
            if (agent == null || !agent.isOpen()) {
                writeError(
                        "SSH agent forwarding required — connect with 'ssh -A' or set 'ForwardAgent yes' in ~/.ssh/config");
                exitCode = 128;
                return;
            }

            log.info("SSH git-upload-pack: user='{}' path={} -> {}", sshUser, repoPath, upstreamUrl);

            TransportConfigCallback transportConfig = SshUpstreamTransport.forwardedAgent(agent);
            Repository localRepo = cache.getOrClone(upstreamUrl, null, transportConfig);
            localRepo.getConfig().setString("fogwall", null, "upstreamUrl", upstreamUrl);
            localRepo.getConfig().save();

            UploadPack up = new UploadPack(localRepo);
            up.setBiDirectionalPipe(true);
            up.upload(in, out, err);

        } catch (SshChannelClosedException e) {
            log.debug("SSH channel already closed for {} (client disconnected)", repoPath);
            exitCode = 1;
        } catch (Exception e) {
            log.error("SSH git-upload-pack failed for {}", repoPath, e);
            writeError("Internal error: " + e.getMessage());
            exitCode = 1;
        } finally {
            if (agent != null) {
                try {
                    agent.close();
                } catch (IOException ignored) {
                }
            }
            if (exitCallback != null) {
                exitCallback.onExit(exitCode);
            }
        }
    }

    private void writeError(String message) {
        try {
            err.write(("error: " + message + "\n").getBytes());
            err.flush();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void destroy(ChannelSession channel) {}
}
