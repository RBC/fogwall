package com.rbc.fogwall.dashboard.e2e;

import com.rbc.fogwall.config.FogwallConfig;
import com.rbc.fogwall.config.LoadedConfig;
import com.rbc.fogwall.config.UserConfig;
import com.rbc.fogwall.dashboard.FogwallDashboardApplication;
import com.rbc.fogwall.jetty.FogwallJettyApplication;
import com.rbc.fogwall.user.UserEntry;
import java.util.List;

/**
 * Starts the dashboard the way production starts it: {@link FogwallDashboardApplication#start} assembles the proxy, the
 * Spring MVC stack and Spring Security from the supplied {@link FogwallConfig}, so a test covers the dashboard and the
 * proxy in one process.
 *
 * <p>Listens on an ephemeral port. Call {@link #getBaseUrl()} after construction.
 *
 * <p>Typical usage:
 *
 * <pre>{@code
 * FogwallConfig config = new FogwallConfig();
 * config.getAuth().setProvider("ldap");
 * config.getAuth().getLdap().setUrl("ldap://localhost:1389/dc=example,dc=com");
 *
 * try (var dashboard = new DashboardFixture(config)) {
 *     // ... make HTTP requests to dashboard.getBaseUrl() ...
 * }
 * }</pre>
 */
class DashboardFixture implements AutoCloseable {

    private final FogwallJettyApplication.Running running;

    /** Starts a dashboard on the given config, with whatever users that config declares. */
    DashboardFixture(FogwallConfig config) throws Exception {
        config.getServer().setPort(0);
        running = FogwallDashboardApplication.start(LoadedConfig.of(config));
    }

    /**
     * Starts a dashboard on the given config with the supplied users, set on the config because that is where the
     * running application reads them from.
     */
    DashboardFixture(FogwallConfig config, List<UserEntry> users) throws Exception {
        config.setUsers(users.stream().map(DashboardFixture::toUserConfig).toList());
        config.getServer().setPort(0);
        running = FogwallDashboardApplication.start(LoadedConfig.of(config));
    }

    private static UserConfig toUserConfig(UserEntry entry) {
        var user = new UserConfig();
        user.setUsername(entry.getUsername());
        user.setPasswordHash(entry.getPasswordHash());
        user.setRoles(entry.getRoles());
        user.setEmails(entry.getEmails());
        return user;
    }

    /** Base URL of the dashboard, e.g. {@code http://localhost:54321}. */
    String getBaseUrl() {
        return "http://localhost:" + getPort();
    }

    /** Port the server is listening on. */
    int getPort() {
        return running.port();
    }

    @Override
    public void close() throws Exception {
        running.close();
    }

    /** Convenience factory for a config with local auth and the given pre-hashed users. */
    static DashboardFixture withLocalUsers(List<UserEntry> users) throws Exception {
        var config = new FogwallConfig();
        config.getAuth().setProvider("local");
        return new DashboardFixture(config, users);
    }
}
