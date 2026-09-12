package com.rbc.fogwall.scmapi;

import static com.rbc.fogwall.scmapi.EntityResponseJson.integer;
import static com.rbc.fogwall.scmapi.EntityResponseJson.parse;
import static com.rbc.fogwall.scmapi.EntityResponseJson.text;
import static com.rbc.fogwall.scmapi.EntityResponseJson.trailingNumber;

import com.rbc.fogwall.db.model.ScmApiEntityRecord.Kind;
import com.rbc.fogwall.db.model.ScmApiEntityRecord.State;
import com.rbc.fogwall.servlet.ScmApiRequestContext;
import java.util.Locale;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * GitHub's GraphQL responses carry only what {@code gh} selected: {@code id} and {@code url} on a created issue or pull
 * request, and {@code clientMutationId} alone on a close or an update. So a create is read from the response (the
 * number off the URL's tail), and everything else is keyed on the node ID the caller addressed, with the state implied
 * by the mutation — {@code closeIssue} closes.
 */
public class GitHubEntityResponseReader implements EntityResponseReader {

    @Override
    public Optional<EntityOutcome> read(ScmApiRequestContext context, String requestPath, String body) {
        String field = context.getMutationField();
        if (field == null) {
            return Optional.empty();
        }
        String nodeId = context.getNodeId();
        JsonNode variables = parse(context.getVariablesJson());
        JsonNode input = variables == null ? null : variables.path("input");
        String title = text(input, "title");
        return switch (field) {
            case "createIssue", "createPullRequest" -> created(field, title, parse(body));
            case "closeIssue" -> Optional.of(new EntityOutcome(Kind.ISSUE, null, null, nodeId, null, State.CLOSED));
            case "closePullRequest" ->
                Optional.of(new EntityOutcome(Kind.PULL_REQUEST, null, null, nodeId, null, State.CLOSED));
            case "updateIssue" -> Optional.of(new EntityOutcome(Kind.ISSUE, null, null, nodeId, title, null));
            case "updatePullRequest" ->
                Optional.of(new EntityOutcome(Kind.PULL_REQUEST, null, null, nodeId, title, null));
            // gh's own mergePullRequest response selects only clientMutationId — no merge commit field, so the
            // outcome carries a null mergeCommitSha even on a successful merge.
            case "mergePullRequest" ->
                Optional.of(new EntityOutcome(Kind.PULL_REQUEST, null, null, nodeId, title, State.MERGED));
            default -> {
                // addComment, labels, assignees, review requests: the target's kind is whatever the node resolver
                // found, which may be either — the registry lookup goes by node ID and does not need it.
                Kind kind = kindOf(context.getNodeType());
                yield nodeId == null
                        ? Optional.empty()
                        : Optional.of(new EntityOutcome(kind, null, null, nodeId, null, null));
            }
        };
    }

    private static Optional<EntityOutcome> created(String field, String title, JsonNode json) {
        Kind kind = field.equals("createIssue") ? Kind.ISSUE : Kind.PULL_REQUEST;
        JsonNode created =
                json == null ? null : json.path("data").path(field).path(kind == Kind.ISSUE ? "issue" : "pullRequest");
        if (created == null || created.isMissingNode() || created.isNull()) {
            return Optional.empty();
        }
        String url = text(created, "url");
        Integer number = integer(created, "number");
        if (number == null) {
            number = trailingNumber(url);
        }
        State state = created.hasNonNull("state") ? state(created.get("state").asText()) : State.OPEN;
        return Optional.of(new EntityOutcome(kind, number, url, text(created, "id"), title, state));
    }

    private static Kind kindOf(String nodeType) {
        return "ISSUE".equals(nodeType) ? Kind.ISSUE : "PULL_REQUEST".equals(nodeType) ? Kind.PULL_REQUEST : null;
    }

    private static State state(String state) {
        return switch (state.toUpperCase(Locale.ROOT)) {
            case "CLOSED" -> State.CLOSED;
            case "MERGED" -> State.MERGED;
            default -> State.OPEN;
        };
    }
}
