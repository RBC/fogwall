package com.rbc.fogwall.scmapi;

import static com.rbc.fogwall.scmapi.ProposalResponseJson.integer;
import static com.rbc.fogwall.scmapi.ProposalResponseJson.numberInPath;
import static com.rbc.fogwall.scmapi.ProposalResponseJson.parse;
import static com.rbc.fogwall.scmapi.ProposalResponseJson.text;

import com.rbc.fogwall.db.model.ScmApiProposalRecord.Kind;
import com.rbc.fogwall.db.model.ScmApiProposalRecord.State;
import com.rbc.fogwall.servlet.ScmApiRequestContext;
import java.util.Locale;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * Every GitLab write returns the object it wrote: an issue or merge request with {@code iid}, {@code web_url},
 * {@code state} and {@code title}; a note with {@code noteable_iid}. Issues and merge requests have separate number
 * spaces, so the kind comes from the operation.
 */
public class GitLabProposalResponseReader implements ProposalResponseReader {

    @Override
    public Optional<ProposalOutcome> read(ScmApiRequestContext context, String requestPath, String body) {
        String operation = context.getMutationField();
        if (operation == null) {
            return Optional.empty();
        }
        Kind kind = operation.startsWith("issues.") ? Kind.ISSUE : Kind.PULL_REQUEST;
        JsonNode json = parse(body);
        if (operation.endsWith(".note")) {
            Integer number = integer(json, "noteable_iid");
            if (number == null) {
                number = numberInPath(requestPath);
            }
            return number == null
                    ? Optional.empty()
                    : Optional.of(new ProposalOutcome(kind, number, null, null, null, null));
        }
        Integer number = integer(json, "iid");
        if (number == null) {
            number = numberInPath(requestPath);
        }
        if (number == null) {
            return Optional.empty();
        }
        State state = json != null && json.hasNonNull("state")
                ? state(json.get("state").asText())
                : null;
        return Optional.of(new ProposalOutcome(
                kind, number, text(json, "web_url"), null, text(json, "title"), state, text(json, "merge_commit_sha")));
    }

    private static State state(String state) {
        return switch (state.toLowerCase(Locale.ROOT)) {
            case "closed" -> State.CLOSED;
            case "merged" -> State.MERGED;
            default -> State.OPEN; // opened, locked
        };
    }
}
