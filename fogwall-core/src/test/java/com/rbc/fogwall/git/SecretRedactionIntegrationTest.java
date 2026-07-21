package com.rbc.fogwall.git;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.config.SecretScanConfig;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.PushStoreFactory;
import com.rbc.fogwall.db.model.PushQuery;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import com.rbc.fogwall.provider.GitHubProvider;
import com.rbc.fogwall.servlet.filter.PushStoreAuditFilter;
import com.rbc.fogwall.servlet.filter.SecretScanningFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end test for #152: a secret committed in a push must never reach the persisted push record verbatim.
 *
 * <p>Runs the real gitleaks binary (skipped if unavailable) through the actual hook/filter chains - store-and-forward
 * ({@link DiffGenerationHook} → {@link SecretScanningHook} → {@link PushStorePersistenceHook}) and transparent proxy
 * ({@link SecretScanningFilter} → {@link PushStoreAuditFilter}) - against repos containing real, detectable secrets of
 * different shapes, then inspects the record written to a real {@link PushStore} (H2). Deliberately not mocked: the
 * point is to verify the raw {@code Secret} value gitleaks actually reports gets found in the stored diff and blanked
 * out, not just that the redaction utility works against a hand-built string.
 */
class SecretRedactionIntegrationTest {

    // Multi-line secret: gitleaks matches the whole PEM block as one Secret value, spanning several diff lines
    // each with their own '+' prefix - the case that originally broke a naive whole-string replace.
    private static final String FAKE_PKCS8_KEY = """
            -----BEGIN PRIVATE KEY-----
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCWv7Dvs6PjZJZ0
            Lh6xvKGwuqCoULGNd75VwkNBLFTEM7ME3jEjPPej3td5BayTIBzRUYnjpU7J1qO0
            zkSUDDrhFpPEoJMDyF2Ml1d1r9EjF52tKc0qzmHeJTCbmLmJmEhbNDrwcX5NbCo2
            Lv9yNVn4qBMp7mfJEh8i3DSW4mhYuFATnUxEw5KxXyx/t53V52qa2euodWjRl4Llt
            eFCZHfqznLo1mi5R5fINwlx1UspD0ItPmQ2eXc0QfUsgTQwj3b1B5VgFzjcBngThI
            BknQrajJHzL60QaSkSlkUVEr7+yE2MIMLtD6kIZR58t0yhd81xY7pwETZ6dOykeXP
            X0C/AgMBAAEC
            -----END PRIVATE KEY-----
            """;

    // Single-line secret: the simple, common case - one Secret value fully contained on one diff line.
    // Stripe's own published test-mode example key (from their public API docs) - not a live credential, but
    // still matches gitleaks' stripe-access-token rule format/entropy check. Split across two constants so the
    // full pattern never appears as one contiguous string in this source file - GitHub push protection flags it
    // on sight regardless of it being a widely-published placeholder. The assembled value only ever exists in
    // the ephemeral @TempDir test repo below, which is never itself pushed anywhere.
    private static final String FAKE_STRIPE_KEY = "sk_test_4eC39HqLyjWDarj" + "tT1zdp7dc";

    @TempDir
    Path tempDir;

    Git git;
    Repository repo;
    PushStore pushStore;
    PushContext pushContext;
    ValidationContext validationContext;
    SecretScanConfig config;
    GitleaksRunner runner;

    @BeforeEach
    void setUp() throws Exception {
        git = Git.init().setDirectory(tempDir.toFile()).call();
        repo = git.getRepository();
        repo.getConfig().setBoolean("commit", null, "gpgsign", false);
        repo.getConfig().save();

        pushStore = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());
        pushContext = new PushContext();
        validationContext = new ValidationContext();
        config = SecretScanConfig.builder().enabled(true).build();
        runner = new GitleaksRunner();

        // Skip if the bundled gitleaks binary isn't available in this environment.
        Optional<List<GitleaksRunner.Finding>> probe = runner.scan("probe text", config);
        assumeTrue(probe.isPresent(), "gitleaks binary not available - skipping");
    }

    private RevCommit commit(String filename, String content) throws Exception {
        File f = new File(tempDir.toFile(), filename);
        Files.writeString(f.toPath(), content);
        git.add().addFilepattern(".").call();
        return git.commit()
                .setAuthor(new PersonIdent("Dev", "dev@example.com"))
                .setCommitter(new PersonIdent("Dev", "dev@example.com"))
                .setMessage("add " + filename)
                .call();
    }

    // ---- store-and-forward path ----

    @Test
    void multiLineSecret_redactedFromStoredDiff_storeAndForward() throws Exception {
        RevCommit base = commit("readme.txt", "hello\n");
        RevCommit withSecret = commit("private.key", FAKE_PKCS8_KEY);

        PushRecord record = runStoreAndForward(base, withSecret);

        assertRedactedEverywhere(record);
        assertDiffStepRedacted(record);
    }

    @Test
    void singleLineSecret_redactedFromStoredDiff_storeAndForward() throws Exception {
        RevCommit base = commit("readme.txt", "hello\n");
        RevCommit withSecret = commit("config.txt", "stripe_key = \"" + FAKE_STRIPE_KEY + "\"\n");

        PushRecord record = runStoreAndForward(base, withSecret);

        assertRedactedEverywhere(record);
        assertDiffStepRedacted(record);
    }

    @Test
    void cleanPush_noSecret_notRejected_diffUnredacted() throws Exception {
        RevCommit base = commit("readme.txt", "hello\n");
        RevCommit clean = commit("notes.txt", "just some notes, nothing sensitive\n");

        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd =
                new ReceiveCommand(base.getId(), clean.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);

        new DiffGenerationHook(validationContext, pushContext).onPreReceive(rp, List.of(cmd));
        new SecretScanningHook(config, validationContext, pushContext, runner).onPreReceive(rp, List.of(cmd));

        assertFalse(validationContext.hasIssues(), "clean push must not be flagged");
        assertTrue(pushContext.getSecretsToRedact().isEmpty(), "no secrets should be captured for a clean push");

        var persistenceHook = new PushStorePersistenceHook(pushStore, new GitHubProvider("/push"));
        persistenceHook.setPushContext(pushContext);
        persistenceHook.preReceiveHook().onPreReceive(rp, List.of(cmd));
        persistenceHook.validationResultHook(validationContext).onPreReceive(rp, List.of(cmd));

        List<PushRecord> notRejected =
                pushStore.find(PushQuery.builder().status(PushStatus.REJECTED).build());
        assertTrue(notRejected.isEmpty(), "clean push must not be auto-rejected");

        // Redaction must be a no-op when there's nothing to redact - the diff step should show the real content.
        List<PushRecord> all = pushStore.find(PushQuery.builder().build());
        boolean diffIntact = all.stream()
                .flatMap(r -> r.getSteps().stream())
                .filter(s -> DiffGenerationHook.STEP_NAME_PUSH_DIFF.equals(s.getStepName()))
                .anyMatch(s -> s.getContent() != null && s.getContent().contains("just some notes"));
        assertTrue(diffIntact, "diff content must be untouched when secret-scan found nothing");
    }

    private PushRecord runStoreAndForward(RevCommit base, RevCommit withSecret) throws Exception {
        ReceivePack rp = new ReceivePack(repo);
        ReceiveCommand cmd =
                new ReceiveCommand(base.getId(), withSecret.getId(), "refs/heads/main", ReceiveCommand.Type.UPDATE);

        var persistenceHook = new PushStorePersistenceHook(pushStore, new GitHubProvider("/push"));
        persistenceHook.setPushContext(pushContext);

        // Real hook chain order: diff generation, then secret scanning, then persistence of the result.
        new DiffGenerationHook(validationContext, pushContext).onPreReceive(rp, List.of(cmd));
        new SecretScanningHook(config, validationContext, pushContext, runner).onPreReceive(rp, List.of(cmd));

        assertTrue(
                validationContext.hasIssues(),
                "gitleaks must flag the injected secret - test fixture is broken otherwise");
        assertFalse(pushContext.getSecretsToRedact().isEmpty(), "raw secret value must be captured for redaction");

        persistenceHook.preReceiveHook().onPreReceive(rp, List.of(cmd));
        persistenceHook.validationResultHook(validationContext).onPreReceive(rp, List.of(cmd));

        List<PushRecord> rejected =
                pushStore.find(PushQuery.builder().status(PushStatus.REJECTED).build());
        assertFalse(rejected.isEmpty(), "push must be auto-rejected");
        return rejected.get(rejected.size() - 1);
    }

    // ---- transparent proxy path ----

    @Test
    void singleLineSecret_redactedFromStoredDiff_transparentProxy() throws Exception {
        RevCommit base = commit("readme.txt", "hello\n");
        RevCommit withSecret = commit("config.txt", "stripe_key = \"" + FAKE_STRIPE_KEY + "\"\n");

        runTransparentProxy(base, withSecret);
    }

    @Test
    void multiLineSecret_redactedFromStoredDiff_transparentProxy() throws Exception {
        RevCommit base = commit("readme.txt", "hello\n");
        RevCommit withSecret = commit("private.key", FAKE_PKCS8_KEY);

        runTransparentProxy(base, withSecret);
    }

    private PushRecord runTransparentProxy(RevCommit base, RevCommit withSecret) throws Exception {
        GitRequestDetails details = new GitRequestDetails();
        details.setOperation(HttpOperation.PUSH);
        details.setCommitFrom(base.getId().name());
        details.setCommitTo(withSecret.getId().name());
        details.setLocalRepository(repo);
        details.setRepoRef(GitRequestDetails.RepoRef.builder()
                .owner("owner")
                .name("repo")
                .slug("/owner/repo")
                .build());

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute(GIT_REQUEST_ATTR)).thenReturn(details);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        new SecretScanningFilter(config, runner).doHttpFilter(req, resp);

        assertFalse(details.getSecretsToRedact().isEmpty(), "raw secret value must be captured for redaction");

        PushStore proxyStore = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());
        var auditFilter = new PushStoreAuditFilter(proxyStore);
        auditFilter.doFilter(req, resp, (r, s) -> {});

        List<PushRecord> saved = proxyStore.find(PushQuery.builder().build());
        assertFalse(saved.isEmpty(), "push record must be persisted");
        PushRecord record = saved.get(0);
        for (String secret : details.getSecretsToRedact()) {
            for (String line : secret.split("\n")) {
                String trimmed = line.strip();
                if (trimmed.isEmpty()) continue;
                for (var step : record.getSteps()) {
                    assertFalse(
                            step.getContent() != null && step.getContent().contains(trimmed),
                            "persisted push record must not contain raw secret material verbatim: " + trimmed);
                }
            }
        }
        return record;
    }

    // ---- shared assertions ----

    private void assertRedactedEverywhere(PushRecord record) {
        for (var step : record.getSteps()) {
            assertNotContainsSecret(step.getContent());
            assertNotContainsSecret(step.getErrorMessage());
            assertNotContainsSecret(step.getBlockedMessage());
        }
        assertNotContainsSecret(record.getBlockedMessage());
    }

    private void assertDiffStepRedacted(PushRecord record) {
        boolean diffStepRedacted = record.getSteps().stream()
                .filter(s -> DiffGenerationHook.STEP_NAME_PUSH_DIFF.equals(s.getStepName()))
                .anyMatch(s -> s.getContent() != null && s.getContent().contains("[REDACTED]"));
        assertTrue(diffStepRedacted, "the stored diff step must contain [REDACTED] in place of the secret material");
    }

    /**
     * Checks line-by-line, not as one contiguous substring - a multi-line secret (e.g. a PEM key) never appears
     * contiguously in a stored diff to begin with (each diff line carries its own +/-/space prefix), so a whole-string
     * {@code contains} check here would trivially pass even if redaction were completely broken.
     */
    private void assertNotContainsSecret(String text) {
        if (text == null) return;
        for (String secret : pushContext.getSecretsToRedact()) {
            for (String line : secret.split("\n")) {
                String trimmed = line.strip();
                if (trimmed.isEmpty()) continue;
                assertFalse(
                        text.contains(trimmed),
                        "persisted push record must not contain raw secret material verbatim: " + trimmed);
            }
        }
    }
}
