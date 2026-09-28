package com.rbc.fogwall.db.model;

import java.time.Instant;
import java.util.List;

/**
 * What any instance needs to forward a parked push: whose linked account to forward as, where to, and which ref updates
 * to apply. The pack itself is read separately, as a stream.
 *
 * @param pushId the push record this belongs to
 * @param providerName the provider instance, which the forwarding user's linked account is held under
 * @param forwardUser the fogwall user whose linked account forwards the push
 * @param upstreamUrl the repository to forward to
 * @param refs the ref updates, in the order the client sent them
 * @param packBytes the size of the stored pack; zero when the push carried no objects
 * @param parkedAt when the push was parked
 */
public record ParkedPush(
        String pushId,
        String providerName,
        String forwardUser,
        String upstreamUrl,
        List<ParkedRefUpdate> refs,
        long packBytes,
        Instant parkedAt) {

    public ParkedPush {
        refs = List.copyOf(refs);
    }
}
