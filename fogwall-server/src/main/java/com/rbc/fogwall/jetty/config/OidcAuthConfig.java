package com.rbc.fogwall.jetty.config;

import lombok.Data;

/** Binds the {@code auth.oidc} block in git-proxy.yml. */
@Data
public class OidcAuthConfig {

    /**
     * OIDC provider issuer URI. Spring Security will fetch {@code {issuerUri}/.well-known/openid-configuration} at
     * startup to discover authorization, token, and JWKS endpoints. Example: {@code https://accounts.example.com} or
     * {@code http://localhost:9090/default} (mock server).
     *
     * <p><b>Entra ID note:</b> Entra ID tokens are issued by {@code https://sts.windows.net/{tenant}/} rather than the
     * discovery URL, so issuer validation fails with the standard discovery path. Set {@code jwk-set-uri} to bypass
     * issuer validation (see below).
     */
    private String issuerUri = "";

    /** OAuth2 client ID registered with the provider. */
    private String clientId = "";

    /**
     * OAuth2 client secret. Not required when {@code private-key-path} is set (the server authenticates via signed JWT
     * assertion instead).
     */
    private String clientSecret = "";

    /**
     * Optional JWK Set URI for JWT signature verification. When set, the client registration is built from explicit
     * endpoint URLs (derived from {@code issuerUri}) rather than OIDC discovery, and issuer claim validation is
     * disabled. Useful when the provider's reported issuer URL does not match the URL used to reach it.
     *
     * <p><b>Required for Entra ID</b> because tokens carry {@code iss=https://sts.windows.net/{tenant}/} rather than
     * the discovery base URL. Set this to {@code https://login.microsoftonline.com/{tenant}/discovery/v2.0/keys}.
     *
     * <p>Example: {@code https://login.microsoftonline.com/{tenant}/discovery/v2.0/keys}
     */
    private String jwkSetUri = "";

    /**
     * Override for the authorization endpoint URL. When blank, the value from OIDC discovery is used. Rarely needed —
     * only set this when the provider's discovery document points to an endpoint that is unreachable from the client's
     * browser (e.g. an internal hostname that is only routable inside the cluster).
     */
    private String authorizationUri = "";

    /**
     * Override for the token endpoint URL. When blank, the value from OIDC discovery is used. Useful in local
     * development when the server-side token exchange must reach the OIDC provider via a different hostname than the
     * browser-facing authorization URL (e.g. Podman injects the host {@code /etc/hosts} into containers, so
     * {@code host.containers.internal} can be used to reach a port-mapped provider from inside a container).
     */
    private String tokenUri = "";

    /**
     * Override for the UserInfo endpoint URL. When blank, the value from OIDC discovery is used. Rarely needed — only
     * set this when the provider's discovered UserInfo endpoint is unreachable or must be substituted.
     *
     * <p>For Entra ID, prefer setting {@code skip-user-info: true} instead of overriding this URL.
     */
    private String userInfoUri = "";

    /**
     * JWT claim used as the principal name (username). Defaults to {@code sub}, which is always present in OIDC tokens.
     * Set to {@code preferred_username} for providers that populate it (e.g. Entra ID, Keycloak).
     */
    private String userNameAttribute = "sub";

    /**
     * Path to a PKCS#8 PEM-encoded RSA private key file for {@code private_key_jwt} client authentication. When set,
     * the client presents a signed JWT assertion to the token endpoint instead of a {@code client_secret}. This is the
     * preferred authentication method for confidential applications (no shared secret, less frequent rotation).
     *
     * <p>Generate a key pair with:
     *
     * <pre>
     * openssl genrsa -out private.pem 2048
     * openssl pkcs8 -topk8 -nocrypt -in private.pem -out private-pkcs8.pem
     * openssl rsa -in private.pem -pubout -out public.pem   # register the public key with your IDP
     * </pre>
     *
     * <p>Example: {@code /run/secrets/fogwall-oidc-private-key.pem}
     */
    private String privateKeyPath = "";

    /**
     * Path to the X.509 certificate (PEM or DER) corresponding to {@code private-key-path}. When set, the SHA-1
     * thumbprint of the certificate is included as {@code x5t} in the JWT assertion header.
     *
     * <p><b>Required for Entra ID</b> — Entra matches registered certificates by {@code x5t}, not {@code kid}. Without
     * this field every token exchange fails with {@code AADSTS700027}. Providers that match on {@code kid} (Keycloak,
     * Dex) do not need this field.
     *
     * <p>Generate the certificate from the private key:
     *
     * <pre>
     * openssl req -new -x509 -key private.pem -out cert.pem -days 365
     * </pre>
     *
     * <p>Then upload {@code cert.pem} to your Entra ID app registration under <em>Certificates &amp; secrets →
     * Certificates</em>.
     *
     * <p>Example: {@code /run/secrets/fogwall-oidc-cert.pem}
     */
    private String certPath = "";

    /**
     * Skip the UserInfo endpoint call entirely. When {@code true}, all claims are read from the ID token and the
     * UserInfo endpoint is never contacted.
     *
     * <p><b>Required for Entra ID</b> — Entra's discovered UserInfo endpoint
     * ({@code graph.microsoft.com/oidc/userinfo}) returns HTTP 200 but omits {@code preferred_username}. This is a
     * Microsoft design constraint present on all Entra tenants; it is not a tenant configuration issue. With the
     * {@code profile} scope, the Entra v2.0 ID token contains all required claims ({@code preferred_username},
     * {@code groups}, {@code email}).
     *
     * <p>Set to {@code false} (the default) for standard OIDC providers (Keycloak, Okta, Dex) that return all required
     * claims from their UserInfo endpoints.
     */
    private boolean skipUserInfo = false;

    /**
     * Explicit {@code kid} (key ID) to include in the {@code private_key_jwt} assertion header. Use this when the IdP
     * matches the client assertion against a registered JWKS by {@code kid} — Keycloak, Okta, Auth0, and Dex all work
     * this way.
     *
     * <p>Find the correct value by inspecting your provider's JWKS endpoint (e.g.
     * {@code <issuer>/protocol/openid-connect/certs} on Keycloak) and matching it to the public key you registered.
     *
     * <p>Precedence when {@code private-key-path} is set:
     *
     * <ol>
     *   <li>{@code cert-path} set → {@code x5t#S256} thumbprint (Entra ID)
     *   <li>{@code key-id} set → explicit {@code kid} (Keycloak, Okta, Auth0, Dex)
     *   <li>Neither → random UUID {@code kid} (only suitable for providers that accept any {@code kid})
     * </ol>
     *
     * <p>Example: {@code my-registered-kid}
     */
    private String keyId = "";
}
