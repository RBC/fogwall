package com.rbc.fogwall.dashboard.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rbc.fogwall.provider.ForgejoProvider;
import com.rbc.fogwall.provider.GitHubProvider;
import java.net.URI;
import org.junit.jupiter.api.Test;

class GitSetupConfigGeneratorTest {

    private final GitHubProvider github = GitHubProvider.builder()
            .name("github")
            .uri(URI.create("https://github.com"))
            .build();

    @Test
    void httpPush_reroutesPushesOnlyToServer() {
        String cfg = GitSetupConfigGenerator.httpPush("https://fogwall.example.internal", github);

        assertTrue(
                cfg.contains("[url \"https://fogwall.example.internal/server/github.com/\"]"),
                "push URL should be the /server path:\n" + cfg);
        assertTrue(cfg.contains("\tpushInsteadOf = https://github.com/"), "push uses pushInsteadOf:\n" + cfg);
        // Push-only: it must NOT rewrite fetches, so no insteadOf / /proxy entry.
        assertFalse(cfg.contains("insteadOf = https://github.com/"), "must not rewrite fetches:\n" + cfg);
        assertFalse(cfg.contains("/proxy/"), "push-only config must not mention the proxy path:\n" + cfg);
    }

    @Test
    void httpRead_routesFetchesToProxy() {
        String cfg = GitSetupConfigGenerator.httpRead("https://fogwall.example.internal", github);
        assertTrue(
                cfg.contains("[url \"https://fogwall.example.internal/proxy/github.com/\"]"),
                "fetch URL should be the /proxy path:\n" + cfg);
        assertTrue(cfg.contains("\tinsteadOf = https://github.com/"), "fetch uses insteadOf:\n" + cfg);
    }

    @Test
    void httpPerRepo_clonesFromUpstreamAndPushesToServer() {
        String cfg = GitSetupConfigGenerator.httpPerRepo("https://fw.internal", github);
        assertTrue(cfg.contains("git clone https://github.com/<owner>/<repo>.git"), "clones from upstream:\n" + cfg);
        assertTrue(
                cfg.contains(
                        "git remote set-url --push origin https://fw.internal/server/github.com/<owner>/<repo>.git"),
                "points only the push URL at fogwall:\n" + cfg);
    }

    @Test
    void httpPush_normalisesTrailingSlashOnUpstream() {
        var gitlab = ForgejoProvider.builder()
                .name("gitlab")
                .uri(URI.create("https://gitlab.example.com/"))
                .build();
        String cfg = GitSetupConfigGenerator.httpPush("https://fw.internal", gitlab);
        assertTrue(cfg.contains("\tpushInsteadOf = https://gitlab.example.com/"), cfg);
        assertFalse(cfg.contains("gitlab.example.com//"), "must not double the upstream trailing slash:\n" + cfg);
    }

    @Test
    void httpPush_nonDefaultPortIsPartOfTheRoutePath() {
        var gitea = ForgejoProvider.builder()
                .name("gitea")
                .uri(URI.create("https://gitea.local:3000"))
                .build();
        String cfg = GitSetupConfigGenerator.httpPush("https://fw.internal", gitea);
        assertTrue(cfg.contains("/server/gitea.local:3000/"), "route path carries the non-default port:\n" + cfg);
        assertTrue(cfg.contains("\tpushInsteadOf = https://gitea.local:3000/"), cfg);
    }

    @Test
    void sshPush_reroutesBothScpAndSshUrlForms() {
        String cfg = GitSetupConfigGenerator.sshPush("fogwall.example.internal", 2222, github);
        assertTrue(
                cfg.contains("[url \"ssh://fogwall.example.internal:2222/github.com/\"]"),
                "SSH route is ssh://<host>:<port>/<path>/:\n" + cfg);
        assertTrue(cfg.contains("\tpushInsteadOf = git@github.com:"), "scp-like upstream form:\n" + cfg);
        assertTrue(cfg.contains("\tpushInsteadOf = ssh://git@github.com/"), "ssh:// upstream form:\n" + cfg);
    }

    @Test
    void sshPerRepo_clonesFromUpstreamAndPushesToFogwall() {
        String cfg = GitSetupConfigGenerator.sshPerRepo("fw.internal", 2222, github);
        assertTrue(
                cfg.contains("git clone git@github.com:<owner>/<repo>.git"), "clones from upstream over SSH:\n" + cfg);
        assertTrue(
                cfg.contains("git remote set-url --push origin ssh://fw.internal:2222/github.com/<owner>/<repo>.git"),
                "points only the push URL at fogwall:\n" + cfg);
    }
}
