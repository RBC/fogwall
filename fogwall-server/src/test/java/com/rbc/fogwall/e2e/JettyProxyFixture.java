package com.rbc.fogwall.e2e;

import com.rbc.fogwall.config.FogwallConfigLoader;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.model.AccessRule;
import com.rbc.fogwall.jetty.FogwallJettyApplication;
import com.rbc.fogwall.jetty.FogwallServletRegistrar;
import com.rbc.fogwall.permission.RepoPermissionService;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Starts a real fogwall server for {@code @Tag("e2e")} tests, through the same path production takes:
 * {@link FogwallConfigLoader} composes {@code fogwall-test-e2e.yml} with a generated override, and
 * {@link FogwallJettyApplication#start} does the assembly.
 *
 * <p>The override carries what is only known once the process is running — the port the upstream container bound —
 * along with the handful of settings a test varies: the approval mode, whether fetches are served, and the access rules
 * in force.
 *
 * <p>The provider is declared {@code type: forgejo}, which is what a Gitea host is: the Forgejo provider implements
 * token identity lookup, so a test authenticating with a Gitea access token resolves through the same {@code GET
 * /api/v1/user} path production uses.
 *
 * <p>Intended as a JUnit {@code @BeforeAll} / {@code @AfterAll} resource.
 */
class JettyProxyFixture implements AutoCloseable {

    /** Which approval gateway the server runs, mapped to {@code server.approval-mode}. */
    enum ApprovalMode {
        /** Clean pushes block pending human review. The production default for the dashboard. */
        UI("ui"),
        /** Clean pushes are approved as they arrive; nothing waits for a reviewer. */
        AUTO("auto");

        private final String configValue;

        ApprovalMode(String configValue) {
            this.configValue = configValue;
        }
    }

    /**
     * The provider's config map key, which is also its {@code providerId} — so a test can name it in a permission grant
     * or an SCM identity before the fixture is built.
     *
     * <p>A name the base config already declares, rather than one invented here. A reload composes the base config with
     * the reload source and nothing else, so a user or permission naming a provider that exists only in a fixture's
     * generated override would fail validation the moment anything reloaded.
     */
    static final String PROVIDER_NAME = "gitea";

    /**
     * A proxy user for the override. {@code scmLogin} is the login on the upstream that the user's token resolves to;
     * the real Forgejo lookup matches it, so it has to be the account the test pushes as.
     */
    record TestUser(String username, String email, String scmLogin) {}

    /**
     * The registered user a fixture gets unless a test names its own: the Gitea account the suite pushes as, linked to
     * a proxy user of the same name.
     *
     * <p>There is no such thing as a fixture with no users. The base config ships an {@code admin} entry, so the
     * identity path is always live — a push whose token resolves to a login no user claims is refused, exactly as it
     * would be for an operator.
     */
    private static final List<TestUser> DEFAULT_USERS = List.of(
            new TestUser(GiteaContainer.ADMIN_USER, GiteaContainer.VALID_AUTHOR_EMAIL, GiteaContainer.ADMIN_USER));

    private final FogwallJettyApplication.Running running;
    private final String providerId;
    private final String giteaHostPort;

    /** UI (block-then-approve) approval, fetches served, one catch-all allow rule, no registered users. */
    JettyProxyFixture(URI giteaUri) throws Exception {
        this(giteaUri, ApprovalMode.UI, List.of(), true, DEFAULT_USERS, null, true);
    }

    /** As {@link #JettyProxyFixture(URI)} with a chosen approval mode. */
    JettyProxyFixture(URI giteaUri, ApprovalMode approvalMode) throws Exception {
        this(giteaUri, approvalMode, List.of(), true, DEFAULT_USERS, null, true);
    }

    /**
     * An explicit rule set instead of the catch-all allow, on auto-approve so that a push the rules allow completes
     * rather than stopping at a review step the test is not about.
     */
    JettyProxyFixture(URI giteaUri, List<AccessRule> configRules) throws Exception {
        this(giteaUri, ApprovalMode.AUTO, configRules, true, DEFAULT_USERS, null, true);
    }

    /** UI approval, with {@code providers.<name>.serve-fetch} set. */
    JettyProxyFixture(URI giteaUri, boolean serveFetch) throws Exception {
        this(giteaUri, ApprovalMode.UI, List.of(), serveFetch, DEFAULT_USERS, null, true);
    }

    /** A chosen approval mode, with {@code providers.<name>.serve-fetch} set. */
    JettyProxyFixture(URI giteaUri, ApprovalMode approvalMode, boolean serveFetch) throws Exception {
        this(giteaUri, approvalMode, List.of(), serveFetch, DEFAULT_USERS, null, true);
    }

    /**
     * UI approval with named users and <em>no</em> grants — for the tests that assert on the permission gate itself and
     * seed their own grants through {@link #getPermissionService()}.
     */
    JettyProxyFixture(URI giteaUri, List<TestUser> users, String committerAttributionPolicy) throws Exception {
        this(giteaUri, ApprovalMode.UI, List.of(), true, users, committerAttributionPolicy, false);
    }

    JettyProxyFixture(
            URI giteaUri,
            ApprovalMode approvalMode,
            List<AccessRule> configRules,
            boolean serveFetch,
            List<TestUser> users,
            String committerAttributionPolicy,
            boolean grantAll)
            throws Exception {
        this.giteaHostPort = giteaUri.getHost() + ":" + giteaUri.getPort();
        Path override = writeOverride(
                giteaUri, approvalMode, configRules, serveFetch, users, committerAttributionPolicy, grantAll);
        try {
            running = FogwallJettyApplication.start(FogwallConfigLoader.loadWithOverride("test-e2e", override));
        } finally {
            Files.deleteIfExists(override);
        }
        this.providerId = running.providers().getFirst().getProviderId();
    }

    /**
     * Writes the half of the configuration that cannot be committed: the upstream the container bound this run, and the
     * settings this test varies.
     */
    private static Path writeOverride(
            URI giteaUri,
            ApprovalMode approvalMode,
            List<AccessRule> configRules,
            boolean serveFetch,
            List<TestUser> users,
            String committerAttributionPolicy,
            boolean grantAll)
            throws IOException {
        String rules = configRules.isEmpty()
                // No explicit rules — open the proxy, so a test about something else is not refused by an access rule.
                ? """
                rules:
                  allow:
                    - enabled: true
                      order: 1
                      operation: BOTH
                      match:
                        target: OWNER
                        value: "*"
                        type: GLOB
                """
                : renderRules(configRules);

        String yaml = """
                server:
                  approval-mode: %s
                providers:
                  %s:
                    enabled: true
                    type: forgejo
                    uri: %s
                    serve-fetch: %s
                %s%s%s%s""".formatted(
                        approvalMode.configValue,
                        PROVIDER_NAME,
                        giteaUri,
                        serveFetch,
                        rules,
                        renderUsers(users),
                        renderAttributionPolicy(committerAttributionPolicy),
                        grantAll ? renderGrants(users) : "");

        Path file = Files.createTempFile("fogwall-e2e-override-", ".yml");
        Files.writeString(file, yaml);
        return file;
    }

    private static String renderUsers(List<TestUser> users) {
        if (users.isEmpty()) {
            return "";
        }
        return "users:\n"
                + users.stream()
                        .map(u -> """
                                  - username: %s
                                    emails:
                                      - %s
                                    scm-identities:
                                      - provider: %s
                                        username: %s
                                """.formatted(u.username(), u.email(), PROVIDER_NAME, u.scmLogin()))
                        .collect(Collectors.joining());
    }

    /** A catch-all grant per user, so a test about validation is not stopped by the permission gate first. */
    private static String renderGrants(List<TestUser> users) {
        if (users.isEmpty()) {
            return "";
        }
        return "permissions:\n"
                + users.stream()
                        .map(u -> """
                                  - username: %s
                                    provider: %s
                                    match:
                                      target: SLUG
                                      value: ".*"
                                      type: REGEX
                                    grant: MAINTAIN
                                """.formatted(u.username(), PROVIDER_NAME))
                        .collect(Collectors.joining());
    }

    private static String renderAttributionPolicy(String committer) {
        return committer == null ? "" : """
                commit:
                  attribution-policy:
                    committer: %s
                """.formatted(committer);
    }

    /** Renders a test's {@link AccessRule} list back into the config shape the loader reads. */
    private static String renderRules(List<AccessRule> configRules) {
        String allow = renderRuleList(configRules, AccessRule.Access.ALLOW);
        String deny = renderRuleList(configRules, AccessRule.Access.DENY);
        var sb = new StringBuilder("rules:\n");
        if (!deny.isEmpty()) {
            sb.append("  deny:\n").append(deny);
        }
        if (!allow.isEmpty()) {
            sb.append("  allow:\n").append(allow);
        }
        return sb.toString();
    }

    private static String renderRuleList(List<AccessRule> rules, AccessRule.Access access) {
        return rules.stream()
                .filter(r -> r.getAccess() == access)
                .map(r -> """
                            - enabled: %s
                              order: %d
                              operation: %s
                              provider: %s
                              match:
                                target: %s
                                value: "%s"
                                type: %s
                        """.formatted(
                                r.isEnabled(),
                                r.getRuleOrder(),
                                r.getOperation(),
                                PROVIDER_NAME,
                                r.getTarget(),
                                r.getValue(),
                                r.getMatchType()))
                .collect(Collectors.joining());
    }

    /** The port the proxy is listening on. */
    int getPort() {
        return running.port();
    }

    /** The push store the server records its decisions in. */
    PushStore getPushStore() {
        return running.ctx().pushStore();
    }

    /** The permission service behind the server — writable, so a test can grant mid-run. */
    RepoPermissionService getPermissionService() {
        return running.ctx().repoPermissionService();
    }

    String getProviderId() {
        return providerId;
    }

    /** {@code host:port} of the upstream Gitea, as it appears in a proxy URL. */
    String getGiteaHostPort() {
        return giteaHostPort;
    }

    /** Base URL for server mode, at the deprecated {@code /push} alias production still serves. */
    String getPushBase() {
        return "http://localhost:" + getPort() + FogwallServletRegistrar.PUSH_PATH_PREFIX + "/" + giteaHostPort;
    }

    /** Base URL for the transparent proxy. */
    String getProxyBase() {
        return "http://localhost:" + getPort() + FogwallServletRegistrar.PROXY_PATH_PREFIX + "/" + giteaHostPort;
    }

    @Override
    public void close() throws Exception {
        running.close();
    }
}
