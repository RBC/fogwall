package com.rbc.fogwall.db.model;

/**
 * An approved deferred push that no instance holds a live claim on.
 *
 * @param pushId the push record id
 * @param claimLapsed whether an instance had claimed it and stopped before finishing; false when its forward never
 *     started
 */
public record ForwardablePush(String pushId, boolean claimLapsed) {}
