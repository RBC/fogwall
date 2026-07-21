package com.rbc.fogwall.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** Binds the {@code server.ssh:} block in fogwall.yml. */
@Data
public class SshConfig {

    private boolean enabled = false;

    /** TCP port the SSH server listens on. */
    private int port = 2222;

    /**
     * Path to the SSH host key file. Generated automatically on first start if absent. Use an absolute path or one
     * relative to the working directory.
     */
    private String hostKeyPath = ".ssh/fogwall_host_key";

    /**
     * Path to a {@code known_hosts} file used to verify upstream SSH host keys. When unset (default), fogwall uses its
     * bundled provider-default keys (github.com, gitlab.com, codeberg.org, bitbucket.org, gitea.com). In a container,
     * bake or mount your own file (e.g. {@code /etc/fogwall/known_hosts}) and point this at it to add or rotate hosts
     * without upgrading fogwall.
     */
    private String knownHostsPath = "";

    /**
     * Additional {@code known_hosts} lines merged on top of {@link #knownHostsPath} / the bundled defaults. Pin a
     * custom provider's upstream SSH host key here — the most secure option for a private/internal SCM. Each entry is a
     * standard known_hosts line, e.g. {@code git.internal.example.com ssh-ed25519 AAAA...}.
     */
    private List<String> extraKnownHosts = new ArrayList<>();

    /**
     * When {@code true}, an upstream SSH host key not covered by {@link #knownHostsPath}, {@link #extraKnownHosts}, or
     * the bundled defaults is trusted on first use: pinned for the process lifetime (a subsequent change is then
     * rejected) and logged loudly with its fingerprint. Convenient for trusted-network internal providers whose key
     * can't be pinned ahead of time; it is not a substitute for pinning across an untrusted network. Default
     * {@code false} — an unknown host key is rejected (fail closed).
     */
    private boolean trustOnFirstUse = false;
}
