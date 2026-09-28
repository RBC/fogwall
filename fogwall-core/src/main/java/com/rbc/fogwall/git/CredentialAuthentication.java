package com.rbc.fogwall.git;

import com.rbc.fogwall.user.UserEntry;

/**
 * An HTTP request authenticated by a git credential fogwall issued, rather than by the client's own SCM credential.
 *
 * @param user the fogwall user the credential belongs to
 * @param credentialId the credential's id
 * @param credentialName the name its owner gave it
 */
public record CredentialAuthentication(UserEntry user, String credentialId, String credentialName) {}
