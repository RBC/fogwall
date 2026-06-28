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
     * Authorized public keys in OpenSSH {@code authorized_keys} format, one entry per list item.
     *
     * <p>Example: {@code ["ssh-ed25519 AAAA... comment"]}
     */
    private List<String> authorizedKeys = new ArrayList<>();
}
