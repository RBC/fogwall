package com.rbc.fogwall.ssh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UpstreamKnownHostsTest {

    @Test
    void bundledDefaults_areUsedWhenNoPathConfigured() throws Exception {
        Path f = UpstreamKnownHosts.assemble("", List.of());
        assertNotNull(f, "bundled provider-default keys must be present");
        String content = Files.readString(f);
        assertTrue(content.contains("github.com"), "bundled known_hosts should pin github.com");
        assertTrue(content.contains("gitlab.com"), "bundled known_hosts should pin gitlab.com");
        assertTrue(content.contains("codeberg.org"), "bundled known_hosts should pin codeberg.org");
    }

    @Test
    void extraKnownHosts_areMerged() throws Exception {
        String pin = "git.internal.example.com ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAExample";
        Path f = UpstreamKnownHosts.assemble("", List.of(pin));
        assertTrue(Files.readString(f).contains("git.internal.example.com"), "inline pinned host must be merged");
    }

    @Test
    void configuredPath_takesPrecedenceOverBundled(@TempDir Path tmp) throws Exception {
        Path custom = tmp.resolve("known_hosts");
        Files.writeString(custom, "my.host.example.org ssh-ed25519 AAAAExampleKey\n");
        Path f = UpstreamKnownHosts.assemble(custom.toString(), List.of());
        String content = Files.readString(f);
        assertTrue(content.contains("my.host.example.org"), "configured file must be used");
        assertFalse(content.contains("github.com"), "bundled defaults must not be added when a path is configured");
    }
}
