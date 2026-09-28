package com.rbc.fogwall.e2e;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.model.PushQuery;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import com.rbc.fogwall.db.model.PushStep;
import com.rbc.fogwall.jetty.FogwallContext;
import com.rbc.fogwall.user.UserStore;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * End-to-end tests for server-mode pushes made with a fogwall-issued credential and forwarded upstream with the
 * pusher's linked OAuth token.
 *
 * <p>Linking itself needs a browser, so the token is seeded straight into the token store the way the link callback
 * stores it: the Gitea access token, encrypted. Gitea accepts an access token as the HTTP password, the same as an
 * OAuth token, so the forward exercises the real upstream path.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BrokeredPushE2ETest {

    static GiteaContainer gitea;
    static String adminToken;
    static JettyProxyFixture proxy;
    static Path tempDir;
    static String credential;

    @BeforeAll
    static void startInfrastructure() throws Exception {
        gitea = new GiteaContainer();
        gitea.start();
        gitea.createAdminUser();
        adminToken = gitea.generateAdminPushToken();
        gitea.createTestRepo();

        tempDir = Files.createTempDirectory("fogwall-brokered-e2e-");
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        Path keyFile = Files.writeString(
                tempDir.resolve("token-key"), Base64.getEncoder().encodeToString(key));
        Path secretFile = Files.writeString(tempDir.resolve("client-secret"), "unused");
        proxy = JettyProxyFixture.brokeredPush(gitea.getBaseUri(), keyFile, secretFile);

        FogwallContext ctx = proxy.getContext();
        // The dashboard materialises a config user's database row before storing anything that references it.
        ((UserStore) ctx.userStore()).upsertUser(GiteaContainer.ADMIN_USER);
        credential = ctx.gitCredentialService()
                .issue(GiteaContainer.ADMIN_USER, "e2e")
                .value();
    }

    @AfterAll
    static void stopInfrastructure() throws Exception {
        if (proxy != null) proxy.close();
        if (gitea != null) gitea.stop();
    }

    private static void linkToken() {
        FogwallContext ctx = proxy.getContext();
        byte[] encrypted =
                ctx.tokenCipherProvider().cipher().orElseThrow().encrypt(adminToken.getBytes(StandardCharsets.UTF_8));
        ctx.scmOAuthTokenStore()
                .save(GiteaContainer.ADMIN_USER, JettyProxyFixture.PROVIDER_NAME, encrypted, null, null, null);
    }

    private static void unlinkToken() {
        proxy.getContext().scmOAuthTokenStore().remove(GiteaContainer.ADMIN_USER, JettyProxyFixture.PROVIDER_NAME);
    }

    /** Server-mode URL carrying {@code password} with an arbitrary HTTP username, which plays no part in identity. */
    private static String serverUrl(String password) {
        return "http://me:" + URLEncoder.encode(password, StandardCharsets.UTF_8) + "@localhost:" + proxy.getPort()
                + "/server/" + proxy.getGiteaHostPort() + "/"
                + GiteaContainer.TEST_ORG + "/" + GiteaContainer.TEST_REPO + ".git";
    }

    private static Path cloneAndCommit(String dirSuffix, String cloneUrl) throws Exception {
        GitHelper git = new GitHelper(tempDir);
        Path repo = git.clone(cloneUrl, dirSuffix);
        git.setAuthor(repo, GiteaContainer.VALID_AUTHOR_NAME, GiteaContainer.VALID_AUTHOR_EMAIL);
        git.writeAndStage(repo, "brokered.txt", dirSuffix + " - " + Instant.now());
        git.commit(repo, "feat: push forwarded with the linked token");
        return repo;
    }

    /**
     * Pushes a new commit authenticating with {@code password}. A push, not a clone: the test repository is public, so
     * fogwall serves a clone of it without challenging, and git never sends the credential at all.
     */
    private static GitHelper.PushResult pushWith(String password, String dirSuffix) throws Exception {
        GitHelper git = new GitHelper(tempDir);
        Path repo = cloneAndCommit(dirSuffix, serverUrl(credential));
        git.setRemoteUrl(repo, "origin", serverUrl(password));
        return git.pushWithResult(repo);
    }

    private static List<String> stepLogs(PushRecord record, String stepName) {
        return record.getSteps().stream()
                .filter(s -> stepName.equals(s.getStepName()))
                .map(PushStep::getLogs)
                .flatMap(List::stream)
                .toList();
    }

    @Test
    @Order(1)
    void pushWithFogwallCredential_isForwardedWithTheLinkedToken() throws Exception {
        linkToken();
        GitHelper git = new GitHelper(tempDir);
        Path repo = cloneAndCommit("brokered-pass", serverUrl(credential));

        var result = git.pushWithResult(repo);

        assertTrue(
                result.succeeded(), "push with a fogwall credential should be forwarded. Output:\n" + result.output());
        PushRecord record = proxy
                .getPushStore()
                .find(PushQuery.builder().limit(100).build())
                .stream()
                .filter(r -> r.getStatus() == PushStatus.FORWARDED)
                .filter(r ->
                        stepLogs(r, "push-permission").stream().anyMatch(l -> l.contains("fogwall credential 'e2e'")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no forwarded push recorded against the credential"));
        assertEquals(GiteaContainer.ADMIN_USER, record.getResolvedUser());
        assertTrue(
                stepLogs(record, "push-permission").stream()
                        .anyMatch(l -> l.contains("Forwarded with the linked gitea OAuth token")),
                "the record should name the linked token the push was forwarded with: "
                        + stepLogs(record, "push-permission"));
    }

    @Test
    @Order(2)
    void wrongSecret_isRefusedAsUnauthenticated() throws Exception {
        String forged = credential.substring(0, credential.length() - 4) + "AAAA";

        var result = pushWith(forged, "brokered-forged");

        assertFalse(result.succeeded(), "a credential with the wrong secret must not authenticate");
        assertTrue(
                result.output().contains("Authentication failed")
                        || result.output().contains("401"),
                "refusal should be an authentication failure. Output:\n" + result.output());
    }

    @Test
    @Order(3)
    void withoutALinkedToken_isRefusedWithTheRemedy() throws Exception {
        unlinkToken();
        try {
            var result = pushWith(credential, "brokered-unlinked");

            assertFalse(result.succeeded(), "without a linked token nothing can reach upstream");
            assertTrue(
                    result.output().contains("is not linked to fogwall"),
                    "refusal should say the account is not linked. Output:\n" + result.output());
        } finally {
            linkToken();
        }
    }

    @Test
    @Order(4)
    void revokedCredential_isRefused() throws Exception {
        FogwallContext ctx = proxy.getContext();
        var revocable = ctx.gitCredentialService().issue(GiteaContainer.ADMIN_USER, "revoked");
        assertTrue(ctx.gitCredentialService()
                .revoke(GiteaContainer.ADMIN_USER, revocable.credential().id()));
        var result = pushWith(revocable.value(), "brokered-revoked");

        assertFalse(result.succeeded(), "a revoked credential must not authenticate");
        assertTrue(
                result.output().contains("Authentication failed")
                        || result.output().contains("401"),
                "refusal should be an authentication failure. Output:\n" + result.output());
    }
}
