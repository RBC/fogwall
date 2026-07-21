package com.rbc.fogwall.e2e;

import com.rbc.fogwall.approval.AutoApprovalGateway;
import com.rbc.fogwall.config.ContentPatternConfig;
import com.rbc.fogwall.config.GpgConfig;
import com.rbc.fogwall.config.SshConfig;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.PushStoreFactory;
import com.rbc.fogwall.db.memory.InMemoryUrlRuleRegistry;
import com.rbc.fogwall.db.model.AccessRule;
import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import com.rbc.fogwall.git.LocalRepositoryCache;
import com.rbc.fogwall.git.StoreAndForwardReceivePackFactory;
import com.rbc.fogwall.permission.InMemoryRepoPermissionStore;
import com.rbc.fogwall.permission.RepoPermission;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.provider.ForgejoProvider;
import com.rbc.fogwall.service.SshScmIdentityEnricher;
import com.rbc.fogwall.ssh.SshGitServer;
import com.rbc.fogwall.ssh.SshKeyUtils;
import com.rbc.fogwall.user.ScmIdentity;
import com.rbc.fogwall.user.SshKeyEntry;
import com.rbc.fogwall.user.StaticUserStore;
import com.rbc.fogwall.user.UserEntry;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Test fixture that starts a fogwall SSH server ({@link SshGitServer}) on an ephemeral port, wired to a
 * {@link StaticUserStore} pre-seeded with a test SSH key.
 *
 * <p>The fixture targets a {@link GiteaContainer} as the upstream git provider via SSH. Approval is auto-granted so
 * happy-path tests go straight to FORWARDED without UI interaction.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * proxy = new SshProxyFixture(gitea, pubKeyLine);
 * // push via: ssh://localhost:<proxy.getSshPort()>/<gitea-host>:<gitea-ssh-port>/owner/repo.git
 * }</pre>
 */
class SshProxyFixture implements AutoCloseable {

    /** Proxy username used for the pre-seeded SSH test key. */
    static final String TEST_USER = "ssh-test-user";

    private final SshGitServer sshServer;
    private final int sshPort;
    private final PushStore pushStore;
    private final String giteaSshHostPort;
    private final InMemoryUrlRuleRegistry urlRuleRegistry;

    /**
     * Creates and starts the SSH fixture.
     *
     * @param gitea running Gitea container (SSH port must be exposed)
     * @param publicKeyLine OpenSSH authorized_keys line for the test identity
     */
    SshProxyFixture(GiteaContainer gitea, String publicKeyLine, String giteaApiToken) throws Exception {
        String fingerprint = SshKeyUtils.fingerprint(publicKeyLine);

        var sshKeyEntry = SshKeyEntry.builder()
                .id("config:" + fingerprint)
                .fingerprint(fingerprint)
                .publicKey(SshKeyUtils.normalise(publicKeyLine))
                .label("e2e-test")
                .createdAt(Instant.EPOCH)
                .locked(true)
                .build();

        URI giteaSshUri = gitea.getSshUri();
        this.giteaSshHostPort = giteaSshUri.getHost() + ":" + giteaSshUri.getPort();

        // Provider name is also the providerId used when matching scm_identities in the enricher.
        String providerId = "gitea-ssh-e2e";

        // The test key is registered in Gitea under ADMIN_USER — link the test user's SCM identity accordingly
        // so the enricher can verify the connecting fingerprint against ADMIN_USER's Gitea keys.
        var testUser = UserEntry.builder()
                .username(TEST_USER)
                .emails(List.of(GiteaContainer.VALID_AUTHOR_EMAIL))
                .scmIdentities(List.of(ScmIdentity.builder()
                        .provider(providerId)
                        .username(GiteaContainer.ADMIN_USER)
                        .build()))
                .sshKeys(List.of(sshKeyEntry))
                .build();

        var userStore = new StaticUserStore(List.of(testUser));

        // ForgejoProvider implements SshKeyFingerprintLookup — required for SSH identity verification.
        // apiUri points at Gitea's HTTP API since the transport uri uses the SSH scheme.
        // apiToken is required because the Gitea container has REQUIRE_SIGNIN_VIEW=true by default.
        var provider = ForgejoProvider.builder()
                .name(providerId)
                .uri(giteaSshUri)
                .apiUri(URI.create(gitea.getBaseUrl()))
                .apiToken(giteaApiToken)
                .build();

        pushStore = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());
        var cache = new LocalRepositoryCache(Files.createTempDirectory("fogwall-ssh-e2e-cache-"), 0, true);

        urlRuleRegistry = new InMemoryUrlRuleRegistry();
        urlRuleRegistry.save(AccessRule.builder()
                .ruleOrder(1)
                .access(AccessRule.Access.ALLOW)
                .operation(AccessRule.Operation.BOTH)
                .target(MatchTarget.OWNER)
                .value("*")
                .matchType(MatchType.GLOB)
                .build());

        // Seed a catch-all PUSH permission for the test user so CheckUserPushPermissionHook passes.
        var permissionStore = new InMemoryRepoPermissionStore();
        permissionStore.save(RepoPermission.builder()
                .username(TEST_USER)
                .provider(provider.getProviderId())
                .value("/**")
                .matchType(MatchType.GLOB)
                .grant(RepoPermission.Grant.PUSH)
                .build());
        var permissionService = new RepoPermissionService(permissionStore);

        var receivePackFactory = new StoreAndForwardReceivePackFactory(
                provider,
                JettyProxyFixture::buildCommitConfig,
                null,
                null,
                null,
                ContentPatternConfig.defaultConfig(),
                GpgConfig.defaultConfig(),
                permissionService,
                null,
                pushStore,
                new AutoApprovalGateway(pushStore),
                null,
                Duration.ofSeconds(10),
                urlRuleRegistry);
        receivePackFactory.setSshScmIdentityEnricher(new SshScmIdentityEnricher());

        sshPort = findFreePort();
        var sshConfig = new SshConfig();
        sshConfig.setEnabled(true);
        sshConfig.setPort(sshPort);
        sshConfig.setHostKeyPath(Files.createTempDirectory("fogwall-ssh-e2e-hostkey-")
                .resolve("host_key")
                .toString());
        // The Gitea test container regenerates its SSH host key on each start, so it can't be pinned ahead of time.
        // Trust-on-first-use pins whatever key the container presents on the first upstream connect — the same
        // mechanism operators opt into for internal providers (see SshConfig#isTrustOnFirstUse).
        sshConfig.setTrustOnFirstUse(true);

        sshServer = SshGitServer.create(sshConfig, provider, cache, receivePackFactory, userStore, urlRuleRegistry);
        sshServer.start();
    }

    /** The host port the fogwall SSH server is listening on. */
    int getSshPort() {
        return sshPort;
    }

    /** The push URL for a repository, targeting this SSH fixture. */
    String pushUrl(String owner, String repo) {
        return "ssh://localhost:" + sshPort + "/" + giteaSshHostPort + "/" + owner + "/" + repo + ".git";
    }

    PushStore getPushStore() {
        return pushStore;
    }

    InMemoryUrlRuleRegistry getUrlRuleRegistry() {
        return urlRuleRegistry;
    }

    @Override
    public void close() {
        sshServer.stop();
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            s.setReuseAddress(true);
            return s.getLocalPort();
        }
    }
}
