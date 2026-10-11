package com.rbc.fogwall.config;

import lombok.Data;
import lombok.ToString;

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

    /** OAuth app/client secret. */
    @ToString.Exclude
    private String clientSecret = "";

    /** Path to a file holding the OAuth app/client secret. Mutually exclusive with {@link #clientSecret}. */
    private String clientSecretPath = "";

    /**
     * Whether server-mode pushes authenticated by a fogwall-issued git credential are forwarded with the pusher's
     * linked OAuth token. Linking then also requests the provider's repository write scope. Requires {@link #enabled}.
     */
    private boolean brokeredPush = false;

    /**
     * Whether a server-mode push made with a fogwall-issued git credential is acknowledged once received and forwarded
     * after approval, rather than holding the connection open for review. Requires {@link #brokeredPush}.
     */
    private boolean deferredForwarding = false;
}
