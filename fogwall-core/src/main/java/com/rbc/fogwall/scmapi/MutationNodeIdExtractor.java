package com.rbc.fogwall.scmapi;

import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * Given an allowlisted mutation field name and its parsed {@code variables}, extracts the opaque node ID the mutation
 * targets and which node type it references.
 *
 * <p>The input key holding the node ID differs per mutation field — see docs/internals/SCM_API_PROXY.md's "Mutation →
 * node-ID map" table, verified against live {@code gh} traffic.
 */
public final class MutationNodeIdExtractor {

    private record NodeIdKey(String variablePath, MutationNodeIdRef.NodeType nodeType) {}

    private static final Map<String, NodeIdKey> NODE_ID_KEYS = Map.of(
            "createIssue", new NodeIdKey("input.repositoryId", MutationNodeIdRef.NodeType.REPOSITORY),
            "createPullRequest", new NodeIdKey("input.repositoryId", MutationNodeIdRef.NodeType.REPOSITORY),
            "updateIssue", new NodeIdKey("input.id", MutationNodeIdRef.NodeType.ISSUE),
            "closeIssue", new NodeIdKey("input.issueId", MutationNodeIdRef.NodeType.ISSUE),
            "addComment", new NodeIdKey("input.subjectId", MutationNodeIdRef.NodeType.ISSUE_OR_PULL_REQUEST),
            "updatePullRequest", new NodeIdKey("input.pullRequestId", MutationNodeIdRef.NodeType.PULL_REQUEST),
            "closePullRequest", new NodeIdKey("input.pullRequestId", MutationNodeIdRef.NodeType.PULL_REQUEST));

    private MutationNodeIdExtractor() {}

    /**
     * Extracts the node ID reference for {@code mutationField} from {@code variables}.
     *
     * <p>Empty when {@code mutationField} is not one of the eight known mutations (should not happen for a field that
     * already cleared {@link GitHubMutationAllowlist} — the two maps are kept in lockstep), or the expected variable
     * path is missing or not a string.
     */
    public static Optional<MutationNodeIdRef> extract(String mutationField, JsonNode variables) {
        NodeIdKey key = NODE_ID_KEYS.get(mutationField);
        if (key == null || variables == null) {
            return Optional.empty();
        }
        JsonNode value = navigate(variables, key.variablePath());
        if (value == null || !value.isString()) {
            return Optional.empty();
        }
        return Optional.of(new MutationNodeIdRef(value.asString(), key.nodeType()));
    }

    private static JsonNode navigate(JsonNode root, String dottedPath) {
        JsonNode current = root;
        for (String segment : dottedPath.split("\\.")) {
            if (current == null) {
                return null;
            }
            current = current.get(segment);
        }
        return current;
    }
}
