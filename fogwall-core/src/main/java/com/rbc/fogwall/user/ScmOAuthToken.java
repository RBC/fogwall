package com.rbc.fogwall.user;

import java.time.Instant;

/**
 * An OAuth token obtained by SCM account linking, as the {@link ScmOAuthTokenStore} holds it: the access and refresh
 * tokens still encrypted.
 *
 * @param encryptedAccessToken the access token, encrypted
 * @param encryptedRefreshToken the refresh token, encrypted; null when the provider issued none
 * @param scopes the scopes the provider reported, as it reported them; null when it reported none
 * @param expiresAt when the access token expires; null when the provider gave it no expiry
 * @param authorizedAt when the user linked the account; a refresh does not change it
 */
public record ScmOAuthToken(
        byte[] encryptedAccessToken,
        byte[] encryptedRefreshToken,
        String scopes,
        Instant expiresAt,
        Instant authorizedAt) {}
