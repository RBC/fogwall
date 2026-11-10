package com.rbc.fogwall.db.model;

/**
 * SCM API actions in one {@link ActivityKey} group with one outcome and one kind of contribution.
 *
 * @param actions the action records, one per request
 * @param targets the contributions: distinct pairs of a person and a pull/merge request or issue those actions created
 *     or touched, whatever the grouping. A client that sends one edit as several requests does not inflate it; two
 *     people editing the same pull request are two. An action whose target fogwall could not identify counts as its
 *     own.
 */
public record ScmApiActivityCount(
        ActivityKey key, ScmApiActionStatus status, ScmApiActivity activity, long actions, long targets) {}
