package com.rbc.fogwall.config;

import lombok.Data;
import lombok.ToString;

/**
 * Binds the {@code scm-oauth:} block in fogwall.yml (#40) — global settings for OAuth account linking that apply across
 * all providers. {@code identity-mode} is translated into the core {@link ScmOAuthConfig} by
 * {@link JettyConfigurationBuilder} and threaded to {@code CheckUserPushPermissionHook}.
 *
 * <p>Per-provider OAuth app registration ({@code client-id}/{@code client-secret}) is <em>not</em> here — it lives
 * under {@code providers.<name>.oauth} (see {@link OAuthProviderSettings}), since it is always a property of one
 * specific provider instance, not a separate config tree that has to be kept in sync by name.
 */
@Data
public class ScmOAuthSettings {

    /** {@code permissive} (default) or {@code strict} — see {@link ScmOAuthConfig.IdentityMode}. */
    private String identityMode = "permissive";

    /**
     * The AES-256-GCM key that encrypts stored OAuth tokens at rest: 32 raw bytes, or the base64 text of 32 bytes.
     * Mutually exclusive with {@link #tokenEncryptionKeyPath}. Once the configuration has loaded, holds the resolved
     * key from either form as base64 text.
     */
    @ToString.Exclude
    private String tokenEncryptionKey = "";

    /** Path to a file holding the token encryption key, in either form {@link #tokenEncryptionKey} accepts. */
    private String tokenEncryptionKeyPath = "";

    /**
     * How long a linked account can be used after the user authorized it, as an ISO-8601 duration such as {@code P30D};
     * empty for no limit. Refreshing a token does not restart it; linking the account again does.
     */
    private String maxLinkAge = "";
}
