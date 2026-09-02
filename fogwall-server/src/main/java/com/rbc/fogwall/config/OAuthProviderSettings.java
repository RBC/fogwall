package com.rbc.fogwall.config;

import lombok.Data;

/**
 * OAuth account-linking settings for a single provider instance (#40), nested under {@code providers.<name>.oauth}.
 * This is always a property of exactly one provider instance — an operator running two GitHub OAuth apps at once (e.g.
 * github.com and a separate {@code *.ghe.com} data-residency tenant) already needs two separate {@code providers:}
 * entries for routing, so nesting the OAuth app registration here means there's only ever one map to keep in sync, not
 * two joined by a repeated name.
 */
@Data
public class OAuthProviderSettings {

    /** Whether "Link via OAuth" is offered for this provider. */
    private boolean enabled = false;

    /** OAuth app/client ID. */
    private String clientId = "";

    /** Path to a file holding the OAuth app/client secret. */
    private String clientSecretPath = "";
}
