package com.rbc.fogwall.dashboard.export;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The properties each record type exports, in column order. A record's JSON properties not listed here — a push's
 * per-viewer flags and the URLs the dashboard derives for display — are not part of the record and are not exported.
 */
public final class ExportFields {

    private ExportFields() {}

    public static final List<String> PUSHES = List.of(
            "id",
            "timestamp",
            "status",
            "provider",
            "project",
            "repoName",
            "url",
            "upstreamUrl",
            "branch",
            "commitFrom",
            "commitTo",
            "message",
            "author",
            "authorEmail",
            "committer",
            "committerEmail",
            "user",
            "resolvedUser",
            "scmUsername",
            "userEmail",
            "method",
            "errorMessage",
            "blockedMessage",
            "autoApproved",
            "autoRejected",
            "forwardedAt",
            "deferred",
            "steps",
            "commits",
            "attestation");

    public static final List<String> SCM_API_ACTIONS = List.of(
            "id",
            "timestamp",
            "provider",
            "scmUsername",
            "resolvedUser",
            "repoOwner",
            "repoName",
            "mutationField",
            "nodeId",
            "nodeType",
            "status",
            "origin",
            "reason",
            "variablesJson",
            "userAgent",
            "clientType",
            "clientVersion",
            "upstreamStatus",
            "entityId",
            "mergeCommitSha");

    public static final List<String> FETCH_ACTIVITY = List.of(
            "id",
            "bucketStart",
            "provider",
            "owner",
            "repoName",
            "transport",
            "mode",
            "result",
            "refusal",
            "ruleId",
            "fetchCount",
            "lastSeen");

    public static final List<String> PERMISSION_CHANGES = List.of(
            "id",
            "occurredAt",
            "action",
            "origin",
            "actor",
            "sourceRevision",
            "subjectType",
            "subject",
            "permissionId",
            "provider",
            "target",
            "value",
            "matchType",
            "grant");

    public static final List<String> MEMBERSHIP_CHANGES = List.of(
            "id", "occurredAt", "action", "origin", "actor", "sourceRevision", "groupId", "groupName", "member");

    public static final List<String> ROLE_CHANGES =
            List.of("id", "occurredAt", "origin", "actor", "username", "previousRoles", "roles");

    public static final List<String> GRANTS = List.of(
            "holder",
            "via",
            "group",
            "provider",
            "target",
            "value",
            "matchType",
            "grant",
            "source",
            "grantedBy",
            "grantedAt",
            "effective",
            "permissionId");

    public static final List<String> GROUP_MEMBERS = List.of("groupId", "group", "groupSource", "member");

    /**
     * The CSV columns a caller chose, in their order: a comma-separated subset of {@code fields}. Blank means every
     * field. An unknown or repeated column is a 400, raised before the export starts.
     */
    public static List<String> columns(String requested, List<String> fields, String type) {
        if (requested == null || requested.isBlank()) {
            return fields;
        }
        Set<String> chosen = new LinkedHashSet<>();
        List<String> unknown = new ArrayList<>();
        for (String raw : requested.split(",")) {
            String column = raw.trim();
            if (column.isEmpty()) {
                continue;
            }
            if (!fields.contains(column)) {
                unknown.add(column);
            } else if (!chosen.add(column)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Column named twice: " + column);
            }
        }
        if (!unknown.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unknown " + type + " columns " + unknown + ". Valid columns: " + fields);
        }
        if (chosen.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "columns names no column");
        }
        return List.copyOf(chosen);
    }
}
