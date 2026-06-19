package com.rbc.fogwall.jetty;

import com.rbc.fogwall.jetty.config.FogwallConfigLoader;
import com.rbc.fogwall.jetty.config.JettyConfigurationBuilder;
import com.rbc.fogwall.jetty.config.TlsConfig;
import com.rbc.fogwall.jetty.reload.LiveConfigLoader;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.tls.SslUtil;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

/**
 * Standalone Jetty server application for the JGit proxy. Registers two servlets per provider:
 *
 * <ul>
 *   <li><b>GitServlet</b> on {@code /push/...} - store-and-forward mode using JGit's native ReceivePack/UploadPack
 *       stack with sideband validation feedback
 *   <li><b>fogwallServlet</b> on {@code /proxy/...} - transparent HTTP proxy bypass
 * </ul>
 *
 * <p>This entry point runs the proxy only - no dashboard, no REST API. For the full stack including the approval
 * workflow UI, use {@code fogwallWithDashboardApplication} from the {@code fogwall-dashboard} module.
 *
 * <p>Configuration is loaded from {@code fogwall.yml} and {@code fogwall-local.yml}, overridable with {@code fogwall_}
 * environment variables.
 */
@Slf4j
public class FogwallJettyApplication {

    public static void main(String[] args) throws Exception {
        log.info("Starting JGit Proxy (proxy only - no dashboard)...");
        writePidFile();

        var fogwallConfig = FogwallConfigLoader.load();
        var configBuilder = new JettyConfigurationBuilder(fogwallConfig);
        configBuilder.validateProviderReferences(); // fail fast before any DB or port setup

        var threadPool = new QueuedThreadPool();
        threadPool.setName("fogwall-server");

        var server = new Server(threadPool);
        var connector = new ServerConnector(server);
        connector.setPort(configBuilder.getServerPort());
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

        FogwallServletRegistrar.registerProviders(context, ctx, configBuilder, providers);

        var liveConfigLoader = new LiveConfigLoader(
                configBuilder.buildConfigHolder(),
                fogwallConfig,
                configBuilder.getReloadConfig(),
                ctx.urlRuleRegistry(),
                ctx.repoPermissionService());
        liveConfigLoader.start();
        server.addEventListener(new LifeCycle.Listener() {
            @Override
            public void lifeCycleStopping(LifeCycle event) {
                liveConfigLoader.stop();
            }
        });

        server.setHandler(new BlockingContentHandler(context));
        server.start();

        log.info("Fogwall started on port {}", connector.getPort());
        for (FogwallProvider provider : providers) {
            log.info(
                    "  - {} (store-and-forward) at {}{}",
                    provider.getName(),
                    FogwallServletRegistrar.PUSH_PATH_PREFIX,
                    provider.servletMapping());
            log.info(
                    "  - {} (proxy bypass) at {}{}",
                    provider.getName(),
                    FogwallServletRegistrar.PROXY_PATH_PREFIX,
                    provider.servletMapping());
        }

        server.join();
    }

    public static ServerConnector buildHttpsConnector(Server server, TlsConfig tls) throws Exception {
        var sslContextFactory = new SslContextFactory.Server();
        if (tls.getCertificate() != null && tls.getKey() != null) {
            sslContextFactory.setSslContext(
                    SslUtil.buildServerSslContext(Path.of(tls.getCertificate()), Path.of(tls.getKey())));
        } else {
            TlsConfig.KeystoreConfig ks = tls.getKeystore();
            sslContextFactory.setKeyStorePath(ks.getPath());
            sslContextFactory.setKeyStorePassword(ks.getPassword());
            sslContextFactory.setKeyStoreType(ks.getType());
        }
        var http = new HttpConnectionFactory();
        var ssl = new SslConnectionFactory(sslContextFactory, http.getProtocol());
        var connector = new ServerConnector(server, ssl, http);
        connector.setPort(tls.getPort());
        return connector;
    }

    /** Write PID file so {@code ./gradlew :fogwall-server:stop} can find and kill this process. */
    public static void writePidFile() {
        String pidFilePath = System.getProperty("fogwall.pidfile");
        if (pidFilePath == null) return;
        try {
            var pidFile = java.nio.file.Path.of(pidFilePath);
            java.nio.file.Files.createDirectories(pidFile.getParent());
            java.nio.file.Files.writeString(
                    pidFile, String.valueOf(ProcessHandle.current().pid()));
            log.info("Wrote PID file: {}", pidFilePath);
        } catch (Exception e) {
            log.warn("Could not write PID file: {}", e.getMessage());
        }
    }
}
