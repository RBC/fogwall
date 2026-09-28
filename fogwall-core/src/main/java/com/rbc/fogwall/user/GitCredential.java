package com.rbc.fogwall.user;

import java.time.Instant;

/**
 * A git credential fogwall issued to one of its users, as the {@link GitCredentialStore} holds it: the secret only as a
 * hash.
 *
 * @param id the non-secret lookup key, also carried in the credential itself
 * @param username the fogwall user the credential authenticates as
 * @param name the label the user gave it, unique per user, such as the machine it is used from
 * @param secretHash the hash of the credential's secret
 * @param createdAt when the credential was issued or last rotated
 * @param expiresAt when it stops working; null when it does not expire
 * @param lastUsedAt when it last authenticated a request, to within a few minutes; null when never used
 */
public record GitCredential(
        String id,
        String username,
        String name,
        String secretHash,
        Instant createdAt,
        Instant expiresAt,
        Instant lastUsedAt) {}
