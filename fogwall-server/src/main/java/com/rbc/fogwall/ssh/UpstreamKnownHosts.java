package com.rbc.fogwall.ssh;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Assembles the effective {@code known_hosts} file that fogwall verifies upstream SSH host keys against (see
 * {@link SshUpstreamTransport}). The effective set is:
 *
 * <ol>
 *   <li>a base file — the operator-configured {@code knownHostsPath} if present, else the bundled provider-default keys
 *       shipped on the classpath at {@code ssh/known_hosts} (github.com, gitlab.com, codeberg.org, bitbucket.org,
 *       gitea.com);
 *   <li>plus any inline {@code extraKnownHosts} lines (per-provider pinned keys).
 * </ol>
 *
 * <p>The result is written to a private temp file named {@code known_hosts} whose parent directory is handed to JGit's
 * {@link org.eclipse.jgit.transport.sshd.ServerKeyDatabase}. Trust-on-first-use keys, when enabled, are pinned in
 * memory at connection time (see {@code SshUpstreamTransport}) rather than written here.
 */
@Slf4j
final class UpstreamKnownHosts {

    private static final String BUNDLED_RESOURCE = "ssh/known_hosts";

    private UpstreamKnownHosts() {}

    /**
     * @return the path to the assembled {@code known_hosts} file, or {@code null} if there is nothing to pin (in which
     *     case the caller falls back to JGit's default {@code ~/.ssh/known_hosts}).
     */
    static Path assemble(String knownHostsPath, List<String> extraKnownHosts) throws IOException {
        List<String> lines = new ArrayList<>();

        if (knownHostsPath != null && !knownHostsPath.isBlank() && Files.isReadable(Path.of(knownHostsPath))) {
            lines.addAll(Files.readAllLines(Path.of(knownHostsPath)));
            log.info("SSH upstream: verifying host keys against {}", knownHostsPath);
        } else {
            if (knownHostsPath != null && !knownHostsPath.isBlank()) {
                log.warn(
                        "SSH upstream: configured known-hosts-path '{}' is not readable — using bundled defaults",
                        knownHostsPath);
            }
            lines.addAll(readBundled());
        }

        if (extraKnownHosts != null) {
            for (String entry : extraKnownHosts) {
                if (entry != null && !entry.isBlank()) {
                    lines.add(entry.strip());
                }
            }
        }

        boolean hasEntry = lines.stream().anyMatch(l -> !l.isBlank() && !l.startsWith("#"));
        if (!hasEntry) {
            log.warn("SSH upstream: no known_hosts entries assembled — falling back to the default ~/.ssh/known_hosts");
            return null;
        }

        Path dir = Files.createTempDirectory("fogwall-knownhosts-");
        Path file = dir.resolve("known_hosts");
        Files.write(file, lines);
        file.toFile().deleteOnExit();
        dir.toFile().deleteOnExit();
        return file;
    }

    private static List<String> readBundled() throws IOException {
        try (InputStream in = UpstreamKnownHosts.class.getClassLoader().getResourceAsStream(BUNDLED_RESOURCE)) {
            if (in == null) {
                log.warn("SSH upstream: bundled known_hosts resource '{}' not found on classpath", BUNDLED_RESOURCE);
                return new ArrayList<>();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return new ArrayList<>(reader.lines().toList());
            }
        }
    }
}
