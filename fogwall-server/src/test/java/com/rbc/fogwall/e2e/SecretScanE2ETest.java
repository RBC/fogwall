package com.rbc.fogwall.e2e;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Pushes secrets through fogwall with a real git client to a Gitea upstream, in both proxy modes, with gitleaks secret
 * scanning on and {@code include-metadata} set: a secret in a file, in a commit message and in an annotated tag message
 * is each refused with a reason naming where it was found, and a clean push goes through.
 */
@Tag("e2e")
class SecretScanE2ETest {

    /** A shape the shipped gitleaks rules detect as {@code aws-access-token}. */
    private static final String AWS_KEY = "AKIAZ3MSJV4WXYZQ7KLM";

    enum Mode {
        PROXY,
        SERVER
    }

    static GiteaContainer gitea;
    static String adminToken;
    static JettyProxyFixture proxy;
    static Path tempDir;

    @BeforeAll
    static void startInfrastructure() throws Exception {
        gitea = new GiteaContainer();
        gitea.start();
        gitea.createAdminUser();
        adminToken = gitea.generateAdminPushToken();
        gitea.createTestRepo();

        proxy = JettyProxyFixture.secretScan(gitea.getBaseUri());
        tempDir = Files.createTempDirectory("fogwall-secret-scan-e2e-");
    }

    @AfterAll
    static void stopInfrastructure() throws Exception {
        if (proxy != null) proxy.close();
        if (gitea != null) gitea.stop();
    }

    private static String repoUrl(Mode mode) {
        String creds = URLEncoder.encode(GiteaContainer.ADMIN_USER, StandardCharsets.UTF_8)
                + ":"
                + URLEncoder.encode(adminToken, StandardCharsets.UTF_8);
        String base = mode == Mode.PROXY ? proxy.getProxyBase() : proxy.getPushBase();
        return base.replace("http://", "http://" + creds + "@") + "/" + GiteaContainer.TEST_ORG + "/"
                + GiteaContainer.TEST_REPO + ".git";
    }

    /** A fresh clone through {@code mode}, on a new branch named for the test and mode. */
    private static Path cloneOnBranch(GitHelper git, Mode mode, String name) throws Exception {
        String dir = name + "-" + mode.name().toLowerCase();
        Path repo = git.clone(repoUrl(mode), dir);
        git.setAuthor(repo, GiteaContainer.VALID_AUTHOR_NAME, GiteaContainer.VALID_AUTHOR_EMAIL);
        git.createAndCheckoutBranch(repo, dir);
        return repo;
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void secretInFile_isRefusedNamingTheFile(Mode mode) throws Exception {
        GitHelper git = new GitHelper(tempDir);
        Path repo = cloneOnBranch(git, mode, "secret-file");
        git.writeAndStage(repo, "deploy.env", "AWS_ACCESS_KEY_ID=" + AWS_KEY + "\n");
        git.commit(repo, "feat: add deploy settings");

        GitHelper.PushResult result = git.pushWithResult(repo);

        assertFalse(result.succeeded(), "a secret in a file must be refused. Output:\n" + result.output());
        assertTrue(result.output().contains("[aws-access-token]"), result.output());
        assertTrue(result.output().contains("deploy.env"), "the finding must name the file:\n" + result.output());
        assertFalse(result.output().contains(AWS_KEY), "the raw secret must never be echoed:\n" + result.output());
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void secretInCommitMessage_isRefusedNamingTheCommit(Mode mode) throws Exception {
        GitHelper git = new GitHelper(tempDir);
        Path repo = cloneOnBranch(git, mode, "secret-message");
        git.writeAndStage(repo, "notes.txt", "deploy notes - " + Instant.now());
        git.commit(repo, "fix: deploy script\n\nworked once I exported the staging key " + AWS_KEY);
        String shortSha = git.headSha(repo).substring(0, 7);

        GitHelper.PushResult result = git.pushWithResult(repo);

        assertFalse(result.succeeded(), "a secret in a commit message must be refused. Output:\n" + result.output());
        assertTrue(result.output().contains("[aws-access-token]"), result.output());
        assertTrue(
                result.output().contains("message of commit " + shortSha),
                "the finding must name the commit:\n" + result.output());
        assertFalse(result.output().contains(AWS_KEY), "the raw secret must never be echoed:\n" + result.output());
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void cleanBranch_isForwarded_thenSecretInTagMessage_isRefusedNamingTheTag(Mode mode) throws Exception {
        GitHelper git = new GitHelper(tempDir);
        Path repo = cloneOnBranch(git, mode, "tag-base");
        git.writeAndStage(repo, "release.txt", "release notes - " + Instant.now());
        git.commit(repo, "chore: prepare release");

        GitHelper.PushResult branch = git.pushWithResult(repo);
        assertTrue(branch.succeeded(), "a clean push must go through with message scanning on:\n" + branch.output());

        String tag = "v-secret-" + mode.name().toLowerCase();
        git.annotatedTag(repo, tag, "Release\n\nstaging key " + AWS_KEY);
        GitHelper.PushResult result = git.pushRefWithResult(repo, "refs/tags/" + tag);

        assertFalse(result.succeeded(), "a secret in a tag message must be refused. Output:\n" + result.output());
        assertTrue(result.output().contains("[aws-access-token]"), result.output());
        assertTrue(
                result.output().contains("message of tag " + tag),
                "the finding must name the tag:\n" + result.output());
        assertFalse(result.output().contains(AWS_KEY), "the raw secret must never be echoed:\n" + result.output());
    }
}
