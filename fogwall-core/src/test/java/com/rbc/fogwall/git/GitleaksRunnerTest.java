package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import com.rbc.fogwall.config.SecretScanConfig;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests that GitleaksRunner detects RSA/PKCS#8 private keys via both scanning modes: - scanGit(): git-native mode used
 * by SecretScanningHook and SecretScanningFilter - scan(): --pipe diff mode used by SecretScanCheck
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

    // Same key wrapped in a unified diff (as produced by git diff / git format-patch).
    // Each content line is prefixed with '+' — this is what GitleaksRunner.scan() receives.
    private static final String FAKE_KEY_IN_DIFF = """
            diff --git a/private.key b/private.key
            new file mode 100644
            index 0000000..1234567
            --- /dev/null
            +++ b/private.key
            @@ -0,0 +1,12 @@
            +-----BEGIN PRIVATE KEY-----
            +MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCWv7Dvs6PjZJZ0
            +Lh6xvKGwuqCoULGNd75VwkNBLFTEM7ME3jEjPPej3td5BayTIBzRUYnjpU7J1qO0
            +zkSUDDrhFpPEoJMDyF2Ml1d1r9EjF52tKc0qzmHeJTCbmLmJmEhbNDrwcX5NbCo2
            +Lv9yNVn4qBMp7mfJEh8i3DSW4mhYuFATnUxEw5KxXyx/t53V52qa2euodWjRl4Llt
            +eFCZHfqznLo1mi5R5fINwlx1UspD0ItPmQ2eXc0QfUsgTQwj3b1B5VgFzjcBngThI
            +BknQrajJHzL60QaSkSlkUVEr7+yE2MIMLtD6kIZR58t0yhd81xY7pwETZ6dOykeXP
            +X0C/AgMBAAEC
            +-----END PRIVATE KEY-----
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
        Optional<List<GitleaksRunner.Finding>> probe = runner.scan("probe text", enabledConfig());
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

    // ── scan() — --pipe diff mode ─────────────────────────────────────────────

    @Test
    void scan_pipeMode_detectsPrivateKeyInDiff() {
        GitleaksRunner runner = runnerOrSkip();

        Optional<List<GitleaksRunner.Finding>> result = runner.scan(FAKE_KEY_IN_DIFF, enabledConfig());

        assertTrue(result.isPresent(), "Scanner must return a result (not fail-open)");
        assertFalse(
                result.get().isEmpty(),
                "gitleaks --pipe mode must detect the PKCS#8 private key embedded in a unified diff. "
                        + "If this assertion fails, the '+' diff prefix is preventing the private-key regex from matching.");
    }

    @Test
    void scan_pipeMode_cleanDiff_noFindings() {
        GitleaksRunner runner = runnerOrSkip();

        String cleanDiff = """
                diff --git a/readme.txt b/readme.txt
                new file mode 100644
                --- /dev/null
                +++ b/readme.txt
                @@ -0,0 +1,1 @@
                +Hello world
                """;

        Optional<List<GitleaksRunner.Finding>> result = runner.scan(cleanDiff, enabledConfig());

        assertTrue(result.isPresent(), "Scanner must return a result");
        assertTrue(result.get().isEmpty(), "Clean diff must produce no findings");
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
