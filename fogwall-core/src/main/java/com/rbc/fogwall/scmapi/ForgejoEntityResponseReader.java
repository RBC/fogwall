package com.rbc.fogwall.scmapi;

import static com.rbc.fogwall.scmapi.EntityResponseJson.integer;
import static com.rbc.fogwall.scmapi.EntityResponseJson.numberInPath;
import static com.rbc.fogwall.scmapi.EntityResponseJson.parse;
import static com.rbc.fogwall.scmapi.EntityResponseJson.text;
import static com.rbc.fogwall.scmapi.EntityResponseJson.trailingNumber;

import com.rbc.fogwall.db.model.ScmApiEntityRecord.Kind;
import com.rbc.fogwall.db.model.ScmApiEntityRecord.State;
import com.rbc.fogwall.servlet.ScmApiRequestContext;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * Every Forgejo/Gitea write returns the object it wrote: an issue or pull request with {@code number},
 * {@code html_url}, {@code state} and {@code title} (plus {@code merged} on a pull request); a comment with its issue's
 * URL. Issues and pull requests share one number space, and {@code tea pr close} goes through the issue endpoint, so
 * whether the target is a pull request comes from the response — an Issue carrying {@code pull_request}, or a
 * PullRequest carrying {@code head} — rather than from the path. A label or assignee write returns the labels or the
 * issue, so the number falls back to the request path.
 */
public class ForgejoEntityResponseReader implements EntityResponseReader {

    @Override
    public Optional<EntityOutcome> read(ScmApiRequestContext context, String requestPath, String body) {
        String operation = context.getMutationField();
        if (operation == null) {
            return Optional.empty();
        }
        JsonNode json = parse(body);
        if (operation.startsWith("issues.comment")) {
            String issueUrl = text(json, "issue_url");
            Integer number = issueUrl != null ? trailingNumber(issueUrl) : numberInPath(requestPath);
            Kind kind = json != null && json.hasNonNull("pull_request_url") ? Kind.PULL_REQUEST : null;
            return number == null
                    ? Optional.empty()
                    : Optional.of(new EntityOutcome(kind, number, null, null, null, null));
        }
        // The merge endpoint returns no body at all on success, so the target is known only from the request path,
        // and the state is MERGED by construction — the mutation succeeded, and merging is all it does.
        if (operation.equals("pulls.merge")) {
            Integer mergedNumber = numberInPath(requestPath);
            return mergedNumber == null
                    ? Optional.empty()
                    : Optional.of(new EntityOutcome(Kind.PULL_REQUEST, mergedNumber, null, null, null, State.MERGED));
        }
        boolean isPullObject = json != null && json.has("number") && json.has("head");
        boolean isIssueObject = json != null && json.has("number") && !json.has("head");
        boolean isObject = isPullObject || isIssueObject;
        Integer number = isObject ? integer(json, "number") : numberInPath(requestPath);
        if (number == null) {
            return Optional.empty();
        }
        Kind kind;
        if (isPullObject || (isIssueObject && json.hasNonNull("pull_request"))) {
            kind = Kind.PULL_REQUEST;
        } else if (isIssueObject) {
            kind = Kind.ISSUE;
        } else {
            kind = operation.startsWith("pulls.") ? Kind.PULL_REQUEST : null;
        }
        State state = null;
        if (isObject) {
            boolean merged = json.path("merged").asBoolean(false)
                    || json.path("pull_request").path("merged").asBoolean(false);
            state = merged ? State.MERGED : state(text(json, "state"));
        }
        String url = isObject ? text(json, "html_url") : null;
        String title = isObject ? text(json, "title") : null;
        return Optional.of(new EntityOutcome(kind, number, url, null, title, state));
    }

    private static State state(String state) {
        return state != null && state.equalsIgnoreCase("closed") ? State.CLOSED : State.OPEN;
    }
}
