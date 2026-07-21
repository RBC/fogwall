package com.rbc.fogwall.config;

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
     * When {@code false} (default), the upstream SCM's SSH host key must already be known to the proxy user (present in
     * {@code known_hosts}); an unknown or mismatched key aborts the forward. This is the MITM protection for the
     * agent-forwarded upstream leg — a spoofed upstream would otherwise receive the client's forwarded agent.
     *
     * <p>Set {@code true} ONLY for local testing against ephemeral servers that regenerate their host key on each start
     * (e.g. a throwaway Gitea container in e2e/smoke tests). It disables upstream host-key verification and must never
     * be enabled in production.
     */
    private boolean insecureUpstreamHostKey = false;
}
