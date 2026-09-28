package com.rbc.fogwall.e2e;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.model.Attestation;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import com.rbc.fogwall.jetty.FogwallContext;
import com.rbc.fogwall.user.UserStore;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * End-to-end test for deferred forwarding: a server-mode push made with a fogwall credential is acknowledged to the
 * client without waiting for review, reaches Gitea only once approved, and is then forwarded from the stored pack with
 * the pusher's linked OAuth token.
 *
 * <p>As in {@link BrokeredPushE2ETest}, the linked token is seeded straight into the token store.
 */
@Tag("e2e")
class DeferredForwardingE2ETest {

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

        tempDir = Files.createTempDirectory("fogwall-deferred-e2e-");
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        Path keyFile = Files.writeString(
                tempDir.resolve("token-key"), Base64.getEncoder().encodeToString(key));
        Path secretFile = Files.writeString(tempDir.resolve("client-secret"), "unused");
        proxy = JettyProxyFixture.deferredForwarding(gitea.getBaseUri(), keyFile, secretFile);

        FogwallContext ctx = proxy.getContext();
        ((UserStore) ctx.userStore()).upsertUser(GiteaContainer.ADMIN_USER);
        credential = ctx.gitCredentialService()
                .issue(GiteaContainer.ADMIN_USER, "e2e")
                .value();
        byte[] encrypted =
                ctx.tokenCipherProvider().cipher().orElseThrow().encrypt(adminToken.getBytes(StandardCharsets.UTF_8));
        ctx.scmOAuthTokenStore()
                .save(GiteaContainer.ADMIN_USER, JettyProxyFixture.PROVIDER_NAME, encrypted, null, null, null);
    }

    @AfterAll
    static void stopInfrastructure() throws Exception {
        if (proxy != null) proxy.close();
        if (gitea != null) gitea.stop();
    }

    private static String serverUrl() {
        return "http://me:" + URLEncoder.encode(credential, StandardCharsets.UTF_8) + "@localhost:" + proxy.getPort()
                + "/server/" + proxy.getGiteaHostPort() + "/"
                + GiteaContainer.TEST_ORG + "/" + GiteaContainer.TEST_REPO + ".git";
    }

    /** Whether Gitea's copy of the test repository holds {@code content} in {@code file}, read without fogwall. */
    private static boolean upstreamHas(String file, String content, String dirSuffix) throws Exception {
        Path clone = new GitHelper(tempDir)
                .clone(
                        gitea.getBaseUrl() + "/" + GiteaContainer.TEST_ORG + "/" + GiteaContainer.TEST_REPO + ".git",
                        dirSuffix);
        Path path = clone.resolve(file);
        return Files.exists(path) && Files.readString(path).equals(content);
    }

    private static PushRecord awaitTerminal(String pushId) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(60);
        while (Instant.now().isBefore(deadline)) {
            PushRecord record = proxy.getPushStore().findById(pushId).orElseThrow();
            if (record.getStatus() == PushStatus.FORWARDED || record.getStatus() == PushStatus.ERROR) {
                return record;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("push " + pushId + " was not forwarded in time");
    }

    @Test
    void parkedPush_isAcknowledged_thenForwardedOnApproval() throws Exception {
        GitHelper git = new GitHelper(tempDir);
        Path repo = git.clone(serverUrl(), "deferred");
        git.setAuthor(repo, GiteaContainer.VALID_AUTHOR_NAME, GiteaContainer.VALID_AUTHOR_EMAIL);
        String content = "deferred - " + Instant.now();
        git.writeAndStage(repo, "deferred.txt", content);
        git.commit(repo, "feat: forwarded after approval");

        var result = git.pushWithResult(repo);

        assertTrue(result.succeeded(), "a parked push is reported as a successful push. Output:\n" + result.output());
        assertTrue(
                result.output().contains("Push received and queued for review"),
                "the client is told the push is queued. Output:\n" + result.output());
        String pushId = result.extractPushId();
        PushRecord parked = proxy.getPushStore().findById(pushId).orElseThrow();
        assertEquals(PushStatus.PENDING, parked.getStatus());
        assertTrue(parked.isDeferred());
        assertFalse(
                upstreamHas("deferred.txt", content, "upstream-before"), "nothing reaches upstream before approval");

        proxy.getPushStore()
                .approve(
                        pushId,
                        Attestation.builder()
                                .pushId(pushId)
                                .type(Attestation.Type.APPROVAL)
                                .reviewerUsername("reviewer")
                                .build());
        assertTrue(proxy.getContext().deferredForwarder().start(pushId, false));

        PushRecord forwarded = awaitTerminal(pushId);
        assertEquals(PushStatus.FORWARDED, forwarded.getStatus(), forwarded.getErrorMessage());
        assertTrue(upstreamHas("deferred.txt", content, "upstream-after"), "the approved push reaches upstream");
    }
}
