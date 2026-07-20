package com.rbc.fogwall.e2e;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.db.model.AccessRule;
import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import com.rbc.fogwall.db.model.PushQuery;
import com.rbc.fogwall.db.model.PushStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;

/**
 * End-to-end tests for the SSH store-and-forward path — both {@code git-receive-pack} (push) and
 * {@code git-upload-pack} (fetch/clone).
 *
 * <p>Push-focused tests clone the upstream test repo from Gitea over HTTP, switch the push remote to fogwall's SSH
 * port, and push a unique commit. Fetch-focused tests clone directly through fogwall's SSH endpoint. The test key is
 * pre-seeded in fogwall's {@link com.rbc.fogwall.user.StaticUserStore} and registered with Gitea, so the full
 * agent-forwarding path is exercised end-to-end.
 *
 * <p>Infrastructure: a Gitea container with SSH exposed, fogwall's SSH server on an ephemeral port, and a real
 * {@code ssh-agent} process started in {@link #startInfrastructure()}.
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SshE2ETest {

    static GiteaContainer gitea;
    static SshProxyFixture proxy;
    static Path tempDir;
    static Path sshKeyFile;
    static String sshAuthSock;
    static String sshAgentPid;
    static Path sshConfigFile;
    static Path noAgentSshConfigFile;

    @BeforeAll
    static void startInfrastructure() throws Exception {
        tempDir = Files.createTempDirectory("fogwall-ssh-e2e-");

        // Generate throw-away ed25519 keypair
        sshKeyFile = tempDir.resolve("test_ssh_key");
        runCmd(tempDir, "ssh-keygen", "-t", "ed25519", "-N", "", "-f", sshKeyFile.toString());
        String pubKeyLine = Files.readString(Path.of(sshKeyFile + ".pub")).trim();

        // Start Gitea with SSH exposed
        gitea = new GiteaContainer();
        gitea.start();
        gitea.createAdminUser();
        gitea.createTestRepo();
        // Register the test key with Gitea so the forwarded-agent push authenticates upstream
        gitea.registerSshKey(GiteaContainer.ADMIN_USER, GiteaContainer.ADMIN_PASSWORD, pubKeyLine);
        // Generate a token for the SSH enricher to call GET /api/v1/users/{login}/keys (auth required by default)
        String adminToken = gitea.generateAdminToken();

        // Start fogwall SSH proxy pre-seeded with the same public key
        proxy = new SshProxyFixture(gitea, pubKeyLine, adminToken);

        // Start an ssh-agent and load the test key so agent forwarding works
        Process agentProc =
                new ProcessBuilder("ssh-agent", "-s").redirectErrorStream(true).start();
        String agentOut = new String(agentProc.getInputStream().readAllBytes());
        agentProc.waitFor();
        sshAuthSock = parseAgentVar(agentOut, "SSH_AUTH_SOCK");
        sshAgentPid = parseAgentVar(agentOut, "SSH_AGENT_PID");

        ProcessBuilder addPb = new ProcessBuilder("ssh-add", sshKeyFile.toString());
        addPb.environment().put("SSH_AUTH_SOCK", sshAuthSock);
        addPb.redirectErrorStream(true);
        Process addProc = addPb.start();
        addProc.getInputStream().readAllBytes(); // drain
        assertEquals(0, addProc.waitFor(), "ssh-add failed — check that ssh-agent is available");

        // SSH config with agent forwarding (normal path)
        sshConfigFile = tempDir.resolve("ssh_config");
        Files.writeString(
                sshConfigFile,
                "Host *\n    ForwardAgent yes\n    StrictHostKeyChecking no\n    UserKnownHostsFile /dev/null\n");

        // SSH config without agent forwarding (for the no-agent test)
        noAgentSshConfigFile = tempDir.resolve("ssh_config_noagent");
        Files.writeString(
                noAgentSshConfigFile,
                "Host *\n    ForwardAgent no\n    StrictHostKeyChecking no\n    UserKnownHostsFile /dev/null\n");
    }

    @AfterAll
    static void stopInfrastructure() throws Exception {
        if (proxy != null) proxy.close();
        if (gitea != null) gitea.stop();
        if (sshAgentPid != null) {
            new ProcessBuilder("kill", sshAgentPid).start().waitFor();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Builds a GitHelper wired for SSH with agent forwarding. */
    private GitHelper sshGit() {
        var git = new GitHelper(tempDir);
        git.setSshEnv(sshAuthSock, "ssh -F " + sshConfigFile);
        return git;
    }

    /**
     * Clones the test repo from Gitea via HTTP, sets the push remote to fogwall SSH, and configures a valid author.
     * Returns the cloned repo path. Push-focused tests only care about the push path, so cloning directly from upstream
     * keeps setup independent of the fetch-via-SSH path covered separately below.
     */
    private Path prepareRepo(String dirSuffix) throws Exception {
        var git = sshGit();
        Path repo = git.clone(
                gitea.getBaseUrl() + "/" + GiteaContainer.TEST_ORG + "/" + GiteaContainer.TEST_REPO + ".git",
                dirSuffix);
        git.setRemoteUrl(repo, "origin", proxy.pushUrl(GiteaContainer.TEST_ORG, GiteaContainer.TEST_REPO));
        git.setAuthor(repo, GiteaContainer.VALID_AUTHOR_NAME, GiteaContainer.VALID_AUTHOR_EMAIL);
        return repo;
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void happyPath_cleanPushIsForwarded() throws Exception {
        var git = sshGit();
        Path repo = prepareRepo("ssh-happy");
        git.writeAndStage(repo, "ssh-test.txt", "SSH push happy path");
        git.commit(repo, "test: SSH store-and-forward happy path");

        var result = git.pushWithResult(repo);
        assertTrue(result.succeeded(), "Expected push to succeed\n" + result.output());

        // Auto-approved FORWARDED pushes don't print a push ID in sideband — query store directly
        var records = proxy.getPushStore().find(PushQuery.builder().limit(100).build());
        assertTrue(
                records.stream().anyMatch(r -> PushStatus.FORWARDED.equals(r.getStatus())),
                "Expected at least one FORWARDED record in the push store");
        assertTrue(
                records.stream().anyMatch(r -> "SSH".equals(r.getMethod())), "Expected push record to have method=SSH");
    }

    @Test
    @Order(2)
    void validationFailure_blockedCommitMessageIsRejected() throws Exception {
        var git = sshGit();
        Path repo = prepareRepo("ssh-blocked");
        git.writeAndStage(repo, "blocked.txt", "blocked");
        git.commit(repo, "WIP: should be blocked");

        var result = git.pushWithResult(repo);
        assertFalse(result.succeeded(), "Expected push to be rejected");
        assertTrue(
                result.output().contains("WIP") || result.output().contains("blocked"),
                "Expected rejection reason in output:\n" + result.output());

        // Push record should be REJECTED — rejected pushes don't emit an ID in sideband output,
        // so query the store directly for the most recent record
        var records = proxy.getPushStore().find(PushQuery.builder().limit(100).build());
        assertTrue(
                records.stream().anyMatch(r -> PushStatus.REJECTED.equals(r.getStatus())),
                "Expected at least one REJECTED record in the push store");
    }

    @Test
    @Order(3)
    void unregisteredKey_sshAuthIsRejected() throws Exception {
        // Generate a second keypair not registered with fogwall
        Path strangerKey = tempDir.resolve("stranger_key");
        runCmd(tempDir, "ssh-keygen", "-t", "ed25519", "-N", "", "-f", strangerKey.toString());

        Path repo = prepareRepo("ssh-unregistered");
        var git = new GitHelper(tempDir);
        // Use the unregistered key directly — no agent, no SSH_AUTH_SOCK
        git.setSshEnv(
                null,
                "ssh -i " + strangerKey + " -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null"
                        + " -o IdentitiesOnly=yes");
        git.writeAndStage(repo, "stranger.txt", "stranger");
        git.commit(repo, "test: unregistered key should be rejected at SSH auth");

        var result = git.pushWithResult(repo);
        assertFalse(result.succeeded(), "Expected SSH auth to be rejected");
        assertTrue(
                result.output().contains("Permission denied") || result.output().contains("publickey"),
                "Expected auth rejection in output:\n" + result.output());

        // No push record created — auth failed before ReceivePack
        var records = proxy.getPushStore().find(PushQuery.builder().limit(100).build());
        assertTrue(
                records.stream()
                        .noneMatch(r -> "ssh-unregistered".contains(r.getRepoName() != null ? r.getRepoName() : "")),
                "No push record expected for auth-rejected push");
    }

    @Test
    @Order(4)
    void noAgentForwarding_fogwallRejectsWithClearError() throws Exception {
        Path repo = prepareRepo("ssh-noagent");
        var git = new GitHelper(tempDir);
        // Authenticate with the registered key but disable agent forwarding
        git.setSshEnv(sshAuthSock, "ssh -F " + noAgentSshConfigFile + " -i " + sshKeyFile);
        git.writeAndStage(repo, "noagent.txt", "no agent");
        git.commit(repo, "test: no agent forwarding should produce clear error");

        var result = git.pushWithResult(repo);
        assertFalse(result.succeeded(), "Expected push to fail without agent forwarding");
        assertTrue(
                result.output().contains("agent forwarding") || result.output().contains("ssh -A"),
                "Expected agent forwarding hint in output:\n" + result.output());
    }

    @Test
    @Order(5)
    void fetchViaSsh_clonesRepoContent() throws Exception {
        var git = sshGit();
        Path repo = git.clone(proxy.pushUrl(GiteaContainer.TEST_ORG, GiteaContainer.TEST_REPO), "ssh-fetch");

        assertTrue(Files.isDirectory(repo.resolve(".git")), "Expected a valid git repo to be cloned via SSH");
        runCmd(repo, "git", "log", "-1"); // throws if HEAD doesn't resolve to a real commit
    }

    @Test
    @Order(6)
    void fetchViaSsh_deniedByUrlRule_isRejected() throws Exception {
        // Lower ruleOrder than the fixture's catch-all allow (order 1), so this is evaluated first.
        // Last test in the class by design — no cleanup needed for a rule that outlives this method.
        proxy.getUrlRuleRegistry()
                .save(AccessRule.builder()
                        .ruleOrder(0)
                        .access(AccessRule.Access.DENY)
                        .operation(AccessRule.Operation.BOTH)
                        .target(MatchTarget.SLUG)
                        .value(GiteaContainer.TEST_ORG + "/" + GiteaContainer.TEST_REPO)
                        .matchType(MatchType.LITERAL)
                        .build());

        var git = sshGit();
        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> git.clone(proxy.pushUrl(GiteaContainer.TEST_ORG, GiteaContainer.TEST_REPO), "ssh-fetch-denied"));
        assertTrue(
                ex.getMessage().contains("denied") || ex.getMessage().contains("not permitted"),
                "Expected URL-rule denial in clone failure output:\n" + ex.getMessage());
    }

    // ── static utilities ──────────────────────────────────────────────────────

    private static void runCmd(Path dir, String... args) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        if (code != 0) {
            throw new RuntimeException(String.join(" ", args) + " failed (exit " + code + "): " + out);
        }
    }

    private static final Pattern AGENT_VAR = Pattern.compile("(\\w+)=([^;]+);");

    private static String parseAgentVar(String agentOutput, String varName) {
        Matcher m = AGENT_VAR.matcher(agentOutput);
        while (m.find()) {
            if (m.group(1).equals(varName)) return m.group(2);
        }
        throw new RuntimeException(varName + " not found in ssh-agent output:\n" + agentOutput);
    }
}
