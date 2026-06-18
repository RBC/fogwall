package com.rbc.fogwall.dashboard.e2e;

import com.rbc.fogwall.jetty.config.OidcAuthConfig;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Testcontainers wrapper for <a href="https://github.com/navikt/mock-oauth2-server">mock-oauth2-server</a> — a fully
 * OIDC-compliant mock server from NAV IT. Returns proper {@code id_token} JWTs, supports nonces, and publishes a
 * discovery document whose {@code issuer} matches the URL used to reach it.
 *
 * <p>The server accepts any {@code client_id} / {@code client_secret} and any username (no password). The login form at
 * {@code /authorize} has a single {@code username} field.
 *
 * <p>Default issuer path is {@code /default}; the full issuer URI is {@code http://localhost:{port}/default}.
 */
@SuppressWarnings("resource")
class MockOAuth2Container extends GenericContainer<MockOAuth2Container> {

    static final String CLIENT_ID = "test-client";
    static final String CLIENT_SECRET = "test-secret";
    static final String TEST_USER = "user1";

    private static final int SERVER_PORT = 8080;
    private static final String ISSUER_ID = "default";

    MockOAuth2Container() {
        super("ghcr.io/navikt/mock-oauth2-server:2.1.10");
        withExposedPorts(SERVER_PORT);
        waitingFor(Wait.forHttp("/" + ISSUER_ID + "/.well-known/openid-configuration"));
    }

    /** Base URL accessible from the test JVM. */
    String getBaseUrl() {
        return "http://localhost:" + getMappedPort(SERVER_PORT);
    }

    /**
     * Full OIDC issuer URI. Pass this to {@link OidcAuthConfig#setIssuerUri}; Spring Security will fetch the discovery
     * document at {@code {issuerUri}/.well-known/openid-configuration} and verify the reported issuer matches — which
     * it does for this server.
     */
    String getIssuerUri() {
        return getBaseUrl() + "/" + ISSUER_ID;
    }
}
