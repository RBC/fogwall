package com.rbc.fogwall.db.model;

/**
 * The group an activity count belongs to. A field the query did not group by is null: {@code provider} under
 * {@link ActivityDimension#PROVIDER} or {@link ActivityDimension#REPO}, {@code owner} and {@code repo} under
 * {@code REPO}, {@code user} under {@link ActivityDimension#USER}, and {@code period} (an index into
 * {@link ActivityQuery#periodStarts()}) under {@link ActivityDimension#PERIOD}.
 */
public record ActivityKey(String provider, String owner, String repo, String user, Integer period) {}
