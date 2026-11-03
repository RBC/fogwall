package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import com.rbc.fogwall.config.SecretScanConfig;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests GitleaksRunner's two modes: {@code scanGit()} over git history (push content), and {@code scanText()} /
 * {@code scanMessages()} over plain text (SCM API prose, commit and tag messages).
 *
 * <p>These tests run the real gitleaks binary bundled in the JAR. They are skipped if the binary is unavailable (CI
 * environments without the bundled resource).
 */
class GitleaksRunnerTest {

    // Structurally valid PKCS#8 PEM block with fake base64 content.
    // The gitleaks private-key rule matches on the header line, not the key material.
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

    @TempDir
    Path tempDir;

    private SecretScanConfig enabledConfig() {
        return SecretScanConfig.builder().enabled(true).build();
    }

    /** Skips the test if the gitleaks binary cannot be resolved (bundled resource absent). */
    private GitleaksRunner runnerOrSkip() {
        GitleaksRunner runner = new GitleaksRunner();
        // Probe: if resolveBinaryPath returns null, the binary is unavailable — skip rather than fail.
        Optional<List<GitleaksRunner.Finding>> probe = runner.scanText("probe text", enabledConfig());
        assumeTrue(probe.isPresent(), "gitleaks binary not available — skipping");
        return runner;
    }

    // ── scanGit() — git-native mode ──────────────────────────────────────────

    @Test
    void scanGit_detectsPrivateKeyInCommit(@TempDir Path repoDir) throws Exception {
        GitleaksRunner runner = runnerOrSkip();

        Git git = Git.init().setDirectory(repoDir.toFile()).call();
        git.getRepository().getConfig().setBoolean("commit", null, "gpgsign", false);
        git.getRepository().getConfig().save();

        // Base commit — already merged into the branch before the "push" arrives
        Files.writeString(repoDir.resolve("readme.txt"), "Hello world");
        git.add().addFilepattern("readme.txt").call();
        RevCommit base = git.commit()
                .setAuthor(new PersonIdent("Test", "test@example.com"))
                .setCommitter(new PersonIdent("Test", "test@example.com"))
                .setMessage("initial commit")
                .call();

        // Bad commit — simulates the new commit being pushed (base..bad is the scan range)
        File keyFile = repoDir.resolve("private.key").toFile();
        Files.writeString(keyFile.toPath(), FAKE_PKCS8_KEY);
        git.add().addFilepattern("private.key").call();
        RevCommit bad = git.commit()
                .setAuthor(new PersonIdent("Test", "test@example.com"))
                .setCommitter(new PersonIdent("Test", "test@example.com"))
                .setMessage("add private key")
                .call();

        // Use commitFrom..commitTo (branch-update range) — mirrors what SecretScanningHook does
        Optional<List<GitleaksRunner.Finding>> result =
                runner.scanGit(repoDir, base.name(), bad.name(), enabledConfig());

        assertTrue(result.isPresent(), "Scanner must return a result (not fail-open)");
        assertFalse(result.get().isEmpty(), "gitleaks must detect the PKCS#8 private key in git-native mode");
        assertTrue(
                result.get().stream().anyMatch(f -> "private-key".equals(f.getRuleId())),
                "Finding must match the 'private-key' rule");
    }

    @Test
    void scanGit_cleanCommit_noFindings(@TempDir Path repoDir) throws Exception {
        GitleaksRunner runner = runnerOrSkip();

        Git git = Git.init().setDirectory(repoDir.toFile()).call();
        git.getRepository().getConfig().setBoolean("commit", null, "gpgsign", false);
        git.getRepository().getConfig().save();

        Files.writeString(repoDir.resolve("readme.txt"), "Hello");
        git.add().addFilepattern("readme.txt").call();
        RevCommit base = git.commit()
                .setAuthor(new PersonIdent("Test", "test@example.com"))
                .setCommitter(new PersonIdent("Test", "test@example.com"))
                .setMessage("initial")
                .call();

        Files.writeString(repoDir.resolve("feature.txt"), "just code, no secrets");
        git.add().addFilepattern("feature.txt").call();
        RevCommit clean = git.commit()
                .setAuthor(new PersonIdent("Test", "test@example.com"))
                .setCommitter(new PersonIdent("Test", "test@example.com"))
                .setMessage("clean commit")
                .call();

        Optional<List<GitleaksRunner.Finding>> result =
                runner.scanGit(repoDir, base.name(), clean.name(), enabledConfig());

        assertTrue(result.isPresent(), "Scanner must return a result");
        assertTrue(result.get().isEmpty(), "Clean commit must produce no findings");
    }

    @Test
    void scanGit_quarantinedCommit_invisibleWithoutAlternate_visibleWithIt(@TempDir Path repoDir) throws Exception {
        GitleaksRunner runner = runnerOrSkip();

        // The "mirror": holds a base commit reachable from a ref, exactly like fogwall's local clone.
        Git git = Git.init().setDirectory(repoDir.toFile()).call();
        git.getRepository().getConfig().setBoolean("commit", null, "gpgsign", false);
        git.getRepository().getConfig().save();

        Files.writeString(repoDir.resolve("readme.txt"), "Hello world");
        git.add().addFilepattern("readme.txt").call();
        RevCommit base = git.commit()
                .setAuthor(new PersonIdent("Test", "test@example.com"))
                .setCommitter(new PersonIdent("Test", "test@example.com"))
                .setMessage("initial commit")
                .call();

        // Simulate a server-mode push: the incoming commit's objects land in a quarantine object store that shares
        // the mirror's git dir but writes objects to a separate directory — never into the mirror itself.
        try (QuarantineObjectStore quarantine = QuarantineObjectStore.create(git.getRepository())) {
            ObjectId badId;
            try (ObjectInserter inserter = quarantine.getRepository().newObjectInserter()) {
                ObjectId blob = inserter.insert(Constants.OBJ_BLOB, FAKE_PKCS8_KEY.getBytes(StandardCharsets.UTF_8));
                TreeFormatter tree = new TreeFormatter();
                tree.append("private.key", FileMode.REGULAR_FILE, blob);
                ObjectId treeId = inserter.insert(tree);
                CommitBuilder commit = new CommitBuilder();
                commit.setTreeId(treeId);
                commit.setParentId(base.getId());
                PersonIdent who = new PersonIdent("Test", "test@example.com");
                commit.setAuthor(who);
                commit.setCommitter(who);
                commit.setMessage("add private key");
                badId = inserter.insert(commit);
                inserter.flush();
            }

            // Without the quarantine exposed, the pushed tip is invisible to the child git gitleaks spawns. This is
            // the regression: it must fail closed (empty result → hook blocks), not report a clean pass.
            Optional<List<GitleaksRunner.Finding>> blind =
                    runner.scanGit(repoDir, base.getId().name(), badId.name(), enabledConfig());
            assertTrue(
                    blind.isEmpty(),
                    "A pushed commit the scan environment cannot resolve must fail closed, not report no findings");

            // With the quarantine's object dir passed as a git alternate, gitleaks resolves and scans the commit.
            Optional<List<GitleaksRunner.Finding>> scanned = runner.scanGit(
                    repoDir, quarantine.getObjectsDirectory(), base.getId().name(), badId.name(), enabledConfig());
            assertTrue(scanned.isPresent(), "Scanner must return a result once the quarantine is exposed");
            assertFalse(scanned.get().isEmpty(), "gitleaks must detect the secret in the quarantined commit");
            assertTrue(
                    scanned.get().stream().anyMatch(f -> "private-key".equals(f.getRuleId())),
                    "Finding must match the 'private-key' rule");
        }
    }

    // ── scanText() — plain text ───────────────────────────────────────────────

    @Test
    void scanText_detectsPrivateKeyInProse() {
        GitleaksRunner runner = runnerOrSkip();

        Optional<List<GitleaksRunner.Finding>> result =
                runner.scanText("Here is the key you asked for:\n" + FAKE_PKCS8_KEY, enabledConfig());

        assertTrue(result.isPresent(), "Scanner must return a result");
        assertFalse(result.get().isEmpty(), "plain-text mode must detect the PKCS#8 private key");
    }

    @Test
    void scanText_cleanText_noFindings() {
        GitleaksRunner runner = runnerOrSkip();

        Optional<List<GitleaksRunner.Finding>> result =
                runner.scanText("Fixes the flaky login test.\n\nNo secrets here.", enabledConfig());

        assertTrue(result.isPresent(), "Scanner must return a result");
        assertTrue(result.get().isEmpty(), "Clean text must produce no findings");
    }

    // ── readFindings() — a findings exit is never read as clean ──────────────

    @Test
    void readFindings_missingReport_isScannerError() {
        assertTrue(GitleaksRunner.readFindings(tempDir.resolve("absent.json")).isEmpty());
    }

    @Test
    void readFindings_unparseableReport_isScannerError() throws Exception {
        Path report = Files.writeString(tempDir.resolve("report.json"), "{not json");
        assertTrue(GitleaksRunner.readFindings(report).isEmpty());
    }

    @Test
    void readFindings_reportListingNothing_isScannerError() throws Exception {
        assertTrue(GitleaksRunner.readFindings(Files.writeString(tempDir.resolve("a.json"), "[]"))
                .isEmpty());
        assertTrue(GitleaksRunner.readFindings(Files.writeString(tempDir.resolve("b.json"), "null"))
                .isEmpty());
    }

    @Test
    void readFindings_validReport_returnsFindings() throws Exception {
        Path report = Files.writeString(
                tempDir.resolve("report.json"), "[{\"RuleID\":\"aws-access-token\",\"Secret\":\"AKIA\"}]");
        Optional<List<GitleaksRunner.Finding>> result = GitleaksRunner.readFindings(report);
        assertTrue(result.isPresent());
        assertEquals("aws-access-token", result.get().get(0).getRuleId());
    }

    // ── scanMessages() — commit and tag messages ──────────────────────────────

    private static final String AWS_KEY = "AKIAZ3MSJV4WXYZQ7KLM";

    @Test
    void scanMessages_attributesEachFindingToItsMessage() {
        GitleaksRunner runner = runnerOrSkip();
        List<GitleaksRunner.ScannedMessage> messages = List.of(
                GitleaksRunner.ScannedMessage.ofCommit("aaaaaaa1111111", "Add readme\n\nNothing to see here."),
                GitleaksRunner.ScannedMessage.ofCommit(
                        "bbbbbbb2222222", "Fix deploy\n\nworked once I exported\nAWS_KEY=" + AWS_KEY + "\n"),
                GitleaksRunner.ScannedMessage.ofTag("refs/tags/v1.2.0", "Release\n\nkey " + AWS_KEY));

        Optional<List<GitleaksRunner.Finding>> result = runner.scanMessages(messages, enabledConfig());

        assertTrue(result.isPresent(), "Scanner must return a result");
        List<String> locations = result.get().stream()
                .map(GitleaksRunner.Finding::getMessageLocation)
                .toList();
        assertEquals(List.of("message of commit bbbbbbb", "message of tag v1.2.0"), locations);
        GitleaksRunner.Finding first = result.get().get(0);
        assertTrue(first.toMessage().contains("message of commit bbbbbbb"), first.toMessage());
        assertFalse(first.toMessage().contains(AWS_KEY), "the raw secret must never reach the client message");
    }

    @Test
    void scanMessages_cleanMessages_noFindings() {
        GitleaksRunner runner = runnerOrSkip();

        Optional<List<GitleaksRunner.Finding>> result = runner.scanMessages(
                List.of(GitleaksRunner.ScannedMessage.ofCommit("aaaaaaa1111111", "Bump version to 1.2.0")),
                enabledConfig());

        assertTrue(result.isPresent());
        assertTrue(result.get().isEmpty());
    }

    @Test
    void scanMessages_noMessages_doesNotRunScanner() {
        GitleaksRunner runner = new GitleaksRunner();
        // A bogus scanner path would fail the run; an empty input must short-circuit before it.
        SecretScanConfig config = SecretScanConfig.builder()
                .enabled(true)
                .scannerPath("/nonexistent/gitleaks")
                .build();

        assertEquals(Optional.of(List.of()), runner.scanMessages(List.of(), config));
        assertEquals(
                Optional.of(List.of()),
                runner.scanMessages(List.of(GitleaksRunner.ScannedMessage.ofCommit("abc", "  ")), config));
    }

    @Test
    void findChecksumEntry_matchesExactFilename() {
        String content = """
                1111111111111111111111111111111111111111111111111111111111111111  gitleaks_8.30.1_linux_arm64.tar.gz
                2222222222222222222222222222222222222222222222222222222222222222  gitleaks_8.30.1_linux_x64.tar.gz
                3333333333333333333333333333333333333333333333333333333333333333  gitleaks_8.30.1_darwin_x64.tar.gz
                """;

        assertEquals(
                "2222222222222222222222222222222222222222222222222222222222222222",
                GitleaksRunner.findChecksumEntry(content, "gitleaks_8.30.1_linux_x64.tar.gz"));
        assertNull(GitleaksRunner.findChecksumEntry(content, "gitleaks_8.30.1_windows_x64.zip"));
        assertNull(GitleaksRunner.findChecksumEntry("", "gitleaks_8.30.1_linux_x64.tar.gz"));
    }

    @Test
    void sha256Hex_computesKnownDigest(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("data");
        Files.writeString(f, "hello");

        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", GitleaksRunner.sha256Hex(f));
    }
}
