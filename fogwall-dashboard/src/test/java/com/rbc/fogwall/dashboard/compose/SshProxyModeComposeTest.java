package com.rbc.fogwall.dashboard.compose;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The SSH transport against the packaged stack: one clean push forwarded, one blocked-commit push refused.
 *
 * <p>Only runs when the stack was brought up with {@code --ssh}; otherwise it skips. SSH is server mode only, and the
 * dashboard distribution always reviews, so a clean push holds the live session pending approval — the test approves it
 * through the REST API while the push waits, which is the streaming path the transparent proxy cannot take. A blocked
 * commit is refused at validation before the hold, so it fails on its own.
 *
 * <p>The key pair is committed ({@code test/ssh/compose_ed25519}); fogwall knows the public half as {@code test-user}'s
 * key ({@code fogwall-docker-default.yml}) and Gitea has it registered for the same login ({@code gitea-seed.sh}), so
 * the agent fogwall forwards authenticates the upstream push.
 */
@Tag("compose")
class SshProxyModeComposeTest {

    private static ComposeStack stack;

    private static Path tempDir;
    private static String authSock;
    private static String agentPid;
    private static Git git;

    @BeforeAll
    static void startInfrastructure() throws Exception {
        stack = ComposeStack.requireRunning();
        assumeTrue(
                stack.sshReachable(),
                "no fogwall SSH server on " + stack.sshHostPort() + " — run: bash compose.sh --ssh -- up -d");

        tempDir = Files.createTempDirectory("fogwall-compose-ssh-");

        // git leaves the checked-out private key world-readable; ssh refuses a key with loose permissions.
        Path key = tempDir.resolve("id_ed25519");
        Files.copy(committedKey(), key);
        Files.setPosixFilePermissions(key, PosixFilePermissions.fromString("rw-------"));

        Process agent =
                new ProcessBuilder("ssh-agent", "-s").redirectErrorStream(true).start();
        String agentOut = new String(agent.getInputStream().readAllBytes());
        agent.waitFor();
        authSock = parseAgentVar(agentOut, "SSH_AUTH_SOCK");
        agentPid = parseAgentVar(agentOut, "SSH_AGENT_PID");

        ProcessBuilder add = new ProcessBuilder("ssh-add", key.toString());
        add.environment().put("SSH_AUTH_SOCK", authSock);
        add.redirectErrorStream(true);
        Process addProc = add.start();
        addProc.getInputStream().readAllBytes();
        assertEquals(0, addProc.waitFor(), "ssh-add failed — is ssh-agent available?");

        // Agent forwarding (ssh -A) is what fogwall relays to authenticate the upstream push.
        Path sshConfig = tempDir.resolve("ssh_config");
        Files.writeString(
                sshConfig, "Host *\n  ForwardAgent yes\n  StrictHostKeyChecking no\n  UserKnownHostsFile /dev/null\n");

        git = new Git(tempDir, Map.of("SSH_AUTH_SOCK", authSock, "GIT_SSH_COMMAND", "ssh -F " + sshConfig));
    }

    @AfterAll
    static void stopInfrastructure() throws Exception {
        if (agentPid != null) {
            new ProcessBuilder("kill", agentPid).start().waitFor();
        }
    }

    @Test
    void cleanPush_heldForReview_thenForwardedOnceApproved() throws Exception {
        String branch = "compose-ssh-" + UUID.randomUUID().toString().substring(0, 8);
        Path repo = git.clone(stack.sshUrl(ComposeStack.TEST_ORG, ComposeStack.TEST_REPO), "ssh-clean");
        git.branch(repo, branch);
        git.commit(repo, "ssh.txt", "pushed over ssh at " + Instant.now() + "\n", "feat: a push worth reviewing");

        // The push holds the SSH session open under UI approval, so run it off-thread and approve while it waits.
        ExecutorService pusher = Executors.newSingleThreadExecutor();
        try {
            Future<Cli.Result> push = pusher.submit(() -> git.push(repo));

            JsonNode pending = awaitPending(branch);
            assertNotNull(pending, "fogwall should record the held push while the SSH session waits");
            var approval = stack.apiPost("/api/push/" + pending.get("id").asText() + "/authorise", "{}");
            assertTrue(approval.statusCode() < 300, "approving should be accepted: " + approval.body());

            Cli.Result result = push.get(120, TimeUnit.SECONDS);
            assertTrue(result.succeeded(), "the approved SSH push should be forwarded. Output:\n" + result.output());
        } finally {
            pusher.shutdownNow();
        }

        JsonNode record = recordFor(branch);
        assertNotNull(record, "fogwall should have a record for " + branch);
        assertTrue(
                record.path("upstreamUrl").asText().startsWith("ssh://"),
                "the push should have been forwarded upstream over SSH: "
                        + record.path("upstreamUrl").asText());
        assertEquals(
                ComposeStack.TEST_USER,
                record.path("resolvedUser").asText(),
                "the connecting key should resolve to the fogwall user it is registered to");
    }

    @Test
    void blockedCommitMessage_isRefused() throws Exception {
        String branch = "compose-ssh-blocked-" + UUID.randomUUID().toString().substring(0, 8);
        Path repo = git.clone(stack.sshUrl(ComposeStack.TEST_ORG, ComposeStack.TEST_REPO), "ssh-blocked");
        git.branch(repo, branch);
        git.commit(repo, "blocked.txt", "blocked\n", "WIP: do not merge");

        Cli.Result result = git.push(repo);

        assertFalse(
                result.succeeded(),
                "a push with a blocked commit message should be refused. Output:\n" + result.output());
        assertTrue(
                result.mentions("WIP") || result.output().toLowerCase().contains("block"),
                "the refusal should name the blocked message, not stop at the review hold. Output:\n"
                        + result.output());
    }

    private JsonNode awaitPending(String branch) throws Exception {
        for (int elapsed = 0; elapsed < 60; elapsed += 2) {
            JsonNode records =
                    new JsonMapper().readTree(stack.api("/api/push?limit=50").body());
            for (JsonNode record : records) {
                if ("PENDING".equals(record.path("status").asText())
                        && record.path("branch").asText().endsWith(branch)) {
                    return record;
                }
            }
            Thread.sleep(2_000);
        }
        return null;
    }

    private JsonNode recordFor(String branch) throws Exception {
        JsonNode records =
                new JsonMapper().readTree(stack.api("/api/push?limit=50").body());
        for (JsonNode record : records) {
            if (record.path("branch").asText().endsWith(branch)) {
                return record;
            }
        }
        return null;
    }

    private static Path committedKey() {
        Path key = Path.of(System.getProperty("fogwall.ssh.key", "../test/ssh/compose_ed25519"));
        assertTrue(Files.exists(key), "committed test key not found at " + key.toAbsolutePath());
        return key;
    }

    private static final Pattern AGENT_VAR = Pattern.compile("(\\w+)=([^;]+);");

    private static String parseAgentVar(String agentOutput, String varName) {
        Matcher m = AGENT_VAR.matcher(agentOutput);
        while (m.find()) {
            if (m.group(1).equals(varName)) {
                return m.group(2);
            }
        }
        throw new IllegalStateException(varName + " not found in ssh-agent output:\n" + agentOutput);
    }
}
