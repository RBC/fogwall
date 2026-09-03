package com.rbc.fogwall.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * SSH transport settings for a single provider entry (the {@code ssh:} sub-block under a {@code providers:} entry).
 * Lets one provider entry serve both HTTP and SSH access to the same upstream, rather than requiring a duplicate
 * {@code ssh://} entry (see fogwall#531).
 */
@Data
public class SshProviderConfig {

    /**
     * Enables SSH transport for this provider. When {@code true} and {@link #uri} is unset, the SSH endpoint is derived
     * as {@code ssh://git@<host>} from the provider's HTTP {@code uri} host (port 22 implied). A legacy standalone
     * entry whose top-level {@code uri} is already {@code ssh://…} serves SSH without this flag.
     */
    private boolean enabled = false;

    /**
     * Explicit SSH transport endpoint, overriding the {@code ssh://git@<host>} default derived from the HTTP
     * {@code uri}. Required when the upstream uses a non-{@code git} SSH username or a non-standard port — for example
     * GitHub Enterprise Cloud with data residency, where the enterprise slug is the SSH user:
     * {@code ssh://{slug}@{tenant}.ghe.com}.
     */
    private String uri = "";

    /**
     * Inline {@code known_hosts} lines pinning this upstream's SSH host key(s). Merged on top of the global
     * {@link SshConfig#getExtraKnownHosts()} / bundled defaults at startup. Each entry is a standard known_hosts line,
     * e.g. {@code git.internal.example.com ssh-ed25519 AAAA...}. Since known_hosts lines are keyed by host,
     * per-provider entries scope themselves to this provider's upstream host.
     */
    private List<String> knownHosts = new ArrayList<>();

    /**
     * Path to a {@code known_hosts} file whose lines pin this upstream's SSH host key(s). Read once at startup and
     * merged like {@link #knownHosts}. Use an absolute path or one relative to the working directory.
     */
    private String knownHostsPath = "";
}
