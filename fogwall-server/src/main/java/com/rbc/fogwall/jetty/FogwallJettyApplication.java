package com.rbc.fogwall.jetty;

import com.rbc.fogwall.build.BuildInfo;
import com.rbc.fogwall.config.FogwallConfig;
import com.rbc.fogwall.config.FogwallConfigLoader;
import com.rbc.fogwall.config.JettyConfigurationBuilder;
import com.rbc.fogwall.config.LoadedConfig;
import com.rbc.fogwall.config.ScmOAuthConfig;
import com.rbc.fogwall.config.ServerConfig;
import com.rbc.fogwall.config.TlsConfig;
import com.rbc.fogwall.db.PendingPushExpiryTask;
import com.rbc.fogwall.jetty.reload.LiveConfigLoader;
import com.rbc.fogwall.observability.OpenTelemetryBootstrap;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.ssh.SshGitServer;
import com.rbc.fogwall.ssh.SshServerRegistrar;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.VirtualThreadPool;

/**
 * Standalone Jetty server application for the fogwall proxy. Registers two servlets per provider:
 *
 * <ul>
 *   <li><b>GitServlet</b> on {@code /server/...} (and the legacy {@code /push/...} alias) - server mode using JGit's
 *       native ReceivePack/UploadPack stack with sideband validation feedback
 *   <li><b>fogwallServlet</b> on {@code /proxy/...} - transparent HTTP proxy bypass
 * </ul>
 *
 * <p>This entry point runs the proxy only - no dashboard, no REST API. For the full stack including the approval
 * workflow UI, use {@code fogwallWithDashboardApplication} from the {@code fogwall-dashboard} module.
 *
 * <p>Configuration is loaded by {@link FogwallConfigLoader}: the bundled defaults, then the default config file
 * {@code fogwall.yml}, then each profile named in {@code FOGWALL_CONFIG_PROFILES}, overridable with {@code FOGWALL_}
 * environment variables.
 */
@Slf4j
public class FogwallJettyApplication {

    public static void main(String[] args) throws Exception {
        log.info(
                "Starting fogwall {} (proxy only - no dashboard)...",
                BuildInfo.get().display());
        writePidFile();

        var loaded = FogwallConfigLoader.loadLayers();
        // A property of this distribution, not of the assembly: the dashboard reuses start() and can satisfy both.
        rejectDashboardOnlyConfig(new JettyConfigurationBuilder(loaded.getConfig()));
        start(loaded).server().join();
    }

    /**
     * A started server and the pieces a caller needs to reach past it: the port actually bound, which is not the
     * configured one when that was 0, and the context holding the stores its decisions are recorded in.
     */
    public record Running(
            Server server,
            int port,
            FogwallContext ctx,
            List<FogwallProvider> providers,
            LiveConfigLoader liveConfigLoader)
            implements AutoCloseable {
        @Override
        public void close() throws Exception {
            server.stop();
        }
    }

    /**
     * Builds and starts the proxy from a loaded configuration, and returns before serving finishes.
     *
     * <p>Everything between configuration and a listening socket lives here rather than in {@link #main}, so that a
     * caller assembling the same server for a test drives the real {@link JettyConfigurationBuilder} and
     * {@link FogwallServletRegistrar} rather than a second copy of this wiring that can drift from it.
     */
    public static Running start(LoadedConfig loaded) throws Exception {
        FogwallConfig fogwallConfig = loaded.getConfig();
        var configBuilder = new JettyConfigurationBuilder(fogwallConfig);
        configBuilder.validateProviderReferences(); // fail fast before any DB or port setup
        configBuilder.applyOutboundProxySystemWiring(); // before any outbound connection is made
        configBuilder.setTelemetry(OpenTelemetryBootstrap.build(
                fogwallConfig.getOtel(), BuildInfo.get().version()));

        var threadPool = new QueuedThreadPool();
        threadPool.setName("fogwall-server");
        configureThreadPool(threadPool, configBuilder.getThreadsConfig());

        var server = new Server(threadPool);
        enableVirtualThreads(server, threadPool, "fogwall-server", configBuilder.getMaxConcurrentRequests());
        var connector = new ServerConnector(server);
        connector.setPort(configBuilder.getServerPort());
        connector.setName(FogwallServletRegistrar.MAIN_HTTP_CONNECTOR);
        server.addConnector(connector);

        // Graceful shutdown: drain in-flight requests for up to 30s on SIGTERM before the JVM exits.
        // Without this, rolling deploys on Kubernetes/OCP hard-kill active git push/proxy streams.
        server.setStopTimeout(30_000);
        server.setStopAtShutdown(true);

        TlsConfig tls = configBuilder.getTlsConfig();
        if (tls.isServerTlsConfigured()) {
            server.addConnector(buildHttpsConnector(server, tls));
            log.info("HTTPS listener configured on port {}", tls.getPort());
        }

        var ctx = configBuilder.buildProxyContext();
        log.info("Push store initialized: {}", ctx.pushStore().getClass().getSimpleName());

        List<FogwallProvider> providers = configBuilder.buildProviders();
        var context = new ServletContextHandler("/", false, false);
        context.setVirtualHosts(FogwallServletRegistrar.MAIN_VIRTUAL_HOSTS);

        FogwallServletRegistrar.registerProviders(context, ctx, configBuilder, providers);

        SshGitServer sshGitServer =
                SshServerRegistrar.startIfEnabled(fogwallConfig.getServer().getSsh(), providers, ctx, configBuilder);

        final SshGitServer finalSshGitServer = sshGitServer;
        var liveConfigLoader = new LiveConfigLoader(
                configBuilder.buildConfigHolder(),
                loaded,
                configBuilder.getReloadConfig(),
                ctx.urlRuleRegistry(),
                ctx.repoPermissionService());
        liveConfigLoader.start();

        var pendingPushExpiryTask =
                new PendingPushExpiryTask(ctx.pushStore(), Duration.ofDays(configBuilder.getPendingPushExpiryDays()));
        pendingPushExpiryTask.start();

        server.addEventListener(new LifeCycle.Listener() {
            @Override
            public void lifeCycleStopping(LifeCycle event) {
                liveConfigLoader.stop();
                pendingPushExpiryTask.stop();
                if (finalSshGitServer != null) {
                    finalSshGitServer.stop();
                }
            }
        });

        var contexts = new ContextHandlerCollection();
        contexts.addHandler(context);
        FogwallServletRegistrar.registerScmApiListeners(server, contexts, ctx, configBuilder, providers);

        server.setHandler(new BlockingContentHandler(contexts));
        server.start();

        log.info("fogwall started on port {}", connector.getPort());
        for (FogwallProvider provider : providers) {
            log.info(
                    "  - {} (server mode) at {}{} (legacy alias: {}{})",
                    provider.getName(),
                    FogwallServletRegistrar.SERVER_PATH_PREFIX,
                    provider.servletMapping(),
                    FogwallServletRegistrar.PUSH_PATH_PREFIX,
                    provider.servletMapping());
            log.info(
                    "  - {} (proxy bypass) at {}{}",
                    provider.getName(),
                    FogwallServletRegistrar.PROXY_PATH_PREFIX,
                    provider.servletMapping());
        }

        return new Running(server, connector.getLocalPort(), ctx, providers, liveConfigLoader);
    }

    /**
     * Dispatches request handling to virtual threads, bounded by {@code maxConcurrentRequests}. The platform pool keeps
     * running selectors and acceptors; blocking application work (validation hooks, approval waits, upstream forwards)
     * parks cheaply on virtual threads instead of holding platform threads. The bound replaces platform pool size as
     * the admission limit — without it, virtual threads would accept unlimited concurrent pushes, each holding its
     * buffered pack bytes until the request completes.
     *
     * <p>The executor is registered as a {@link Server} bean because {@link VirtualThreadPool} is a lifecycle component
     * that must be started before use. A limit of 0 (or lower) leaves the platform pool handling requests directly, as
     * an operational escape hatch.
     */
    /**
     * Fails startup on configuration that only a dashboard can satisfy.
     *
     * <p>This distribution serves git traffic and records its decisions; a push's lifecycle here is automated checks
     * and nothing else. It has no REST API and no web UI, so settings whose completion depends on one cannot be
     * satisfied — not partially, not eventually. Accepting them means the operator discovers it one refused or hung
     * push at a time, which is worse than refusing to start.
     *
     * <p>Deliberately fatal rather than ignored: silently downgrading a security setting an operator asked for is the
     * worse failure. Run the dashboard distribution if you want either of these.
     *
     * <p>Called from {@link #main} rather than from {@link #start}, because it describes what this distribution can
     * serve rather than how the server is assembled — the dashboard builds the same server and can satisfy both.
     */
    static void rejectDashboardOnlyConfig(JettyConfigurationBuilder configBuilder) {
        if (configBuilder.buildScmOAuthConfig().getIdentityMode() == ScmOAuthConfig.IdentityMode.STRICT) {
            throw new IllegalStateException("scm-oauth.identity-mode: strict requires the dashboard distribution."
                    + " Strict mode honours only OAuth-verified identities and the SSH keys imported with them, and"
                    + " account linking is a dashboard flow that this server does not serve. With users configured it"
                    + " would refuse every push; with none it is skipped entirely. Use identity-mode: permissive here,"
                    + " or run fogwall-dashboard.");
        }
        if ("ui".equals(configBuilder.getServerApprovalMode())) {
            throw new IllegalStateException("server.approval-mode: ui requires the dashboard distribution. This server"
                    + " has no REST API or review UI, so a push held for review would wait until the approval timeout"
                    + " and then fail. Use approval-mode: auto here, or run fogwall-dashboard.");
        }
    }

    public static void enableVirtualThreads(
            Server server, QueuedThreadPool threadPool, String name, int maxConcurrentRequests) {
        if (maxConcurrentRequests <= 0) {
            log.info("Virtual-thread dispatch disabled (server.max-concurrent-requests: 0)");
            return;
        }
        var virtualExecutor = new VirtualThreadPool();
        virtualExecutor.setName(name + "-virtual");
        virtualExecutor.setMaxConcurrentTasks(maxConcurrentRequests);
        threadPool.setVirtualThreadsExecutor(virtualExecutor);
        server.addBean(virtualExecutor);
        log.info("Virtual-thread dispatch enabled (max {} concurrent requests)", maxConcurrentRequests);
    }

    /**
     * Applies {@code server.threads.*} sizing to the platform {@link QueuedThreadPool}. Call before
     * {@code server.start()}. With virtual-thread dispatch on (the default) this pool runs only acceptors/selectors;
     * the knobs exist for tuning a large or constrained instance. Min and max are set in whichever order avoids a
     * transient {@code min > max}, so both raising and lowering the pool from its defaults are valid; a genuinely
     * inverted config (min > max) still fails loudly.
     */
    public static void configureThreadPool(QueuedThreadPool threadPool, ServerConfig.ThreadsConfig threads) {
        int min = threads.getMin();
        int max = threads.getMax();
        if (min > max) {
            throw new IllegalArgumentException(
                    "server.threads.min (" + min + ") must not exceed server.threads.max (" + max + ")");
        }
        if (min <= threadPool.getMaxThreads()) {
            threadPool.setMinThreads(min);
            threadPool.setMaxThreads(max);
        } else {
            threadPool.setMaxThreads(max);
            threadPool.setMinThreads(min);
        }
        threadPool.setIdleTimeout(threads.getIdleTimeoutMs());
        log.info("Platform thread pool sized: min={} max={} idleTimeoutMs={}", min, max, threads.getIdleTimeoutMs());
    }

    public static ServerConnector buildHttpsConnector(Server server, TlsConfig tls) throws Exception {
        var http = new HttpConnectionFactory();
        var ssl = new SslConnectionFactory(JettyTls.serverSslContextFactory(tls), http.getProtocol());
        var connector = new ServerConnector(server, ssl, http);
        connector.setPort(tls.getPort());
        connector.setName(FogwallServletRegistrar.MAIN_HTTPS_CONNECTOR);
        return connector;
    }

    /** Write PID file so {@code ./gradlew :fogwall-server:stop} can find and kill this process. */
    public static void writePidFile() {
        String pidFilePath = System.getProperty("fogwall.pidfile");
        if (pidFilePath == null) return;
        try {
            var pidFile = Path.of(pidFilePath);
            java.nio.file.Files.createDirectories(pidFile.getParent());
            java.nio.file.Files.writeString(
                    pidFile, String.valueOf(ProcessHandle.current().pid()));
            log.info("Wrote PID file: {}", pidFilePath);
        } catch (Exception e) {
            log.warn("Could not write PID file: {}", e.getMessage());
        }
    }
}
