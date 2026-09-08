package com.rbc.fogwall.scmapi;

import com.rbc.fogwall.db.ScmApiProposalStore;
import com.rbc.fogwall.db.model.ScmApiProposalRecord;
import com.rbc.fogwall.db.model.ScmApiProposalRecord.Kind;
import com.rbc.fogwall.db.model.ScmApiProposalRecord.State;
import com.rbc.fogwall.servlet.ScmApiRequestContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps the proposal registry current from the upstream responses the forwarders relay. Called once per forwarded
 * mutation, after the response has gone to the client: nothing here can change what the caller received, and a failure
 * is logged rather than thrown — the audit record still lands, without a proposal link.
 *
 * <p>A create inserts the row; any later forwarded mutation on the same target updates it — state when the response or
 * the mutation says so, title and URL when the response carries them — and points {@code last_action_id} at the action
 * record. A mutation on a target the registry has never seen, but whose number the response or path names, inserts it
 * too: that is a proposal opened outside fogwall, now touched through it. GitHub's non-create mutations name their
 * target by node ID only, so such a target stays unregistered there until a response names it.
 */
@Slf4j
@RequiredArgsConstructor
public class ProposalRegistrar {

    private final ScmApiProposalStore store;
    private final ProposalResponseReader reader;

    /**
     * @param requestPath the still-encoded sub-path of a REST request, null for GraphQL
     * @param body the upstream response body, or null when it exceeded the capture bound
     */
    public void recordUpstreamResponse(
            ScmApiRequestContext context, String requestPath, int upstreamStatus, byte[] body) {
        context.setUpstreamStatus(upstreamStatus);
        if (context.getMutationField() == null || upstreamStatus < 200 || upstreamStatus >= 300) {
            return;
        }
        try {
            reader.read(context, requestPath, body == null ? null : new String(body, StandardCharsets.UTF_8))
                    .flatMap(outcome -> register(context, outcome))
                    .ifPresent(context::setProposalId);
        } catch (RuntimeException e) {
            log.warn(
                    "Proposal registry not updated for {} {} on {}/{}: {}",
                    context.getProvider(),
                    context.getMutationField(),
                    context.getRepoOwner(),
                    context.getRepoName(),
                    e.toString());
        }
    }

    private Optional<String> register(ScmApiRequestContext context, ProposalOutcome outcome) {
        Optional<ScmApiProposalRecord> existing = Optional.empty();
        if (outcome.number() != null) {
            // A comment response names its target by number but not always by kind; on the providers where the two
            // share a number space, the registry cannot hold both under one number anyway.
            List<Kind> kinds =
                    outcome.kind() != null ? List.of(outcome.kind()) : List.of(Kind.PULL_REQUEST, Kind.ISSUE);
            for (Kind kind : kinds) {
                existing = store.findByTarget(
                        context.getProvider(), context.getRepoOwner(), context.getRepoName(), kind, outcome.number());
                if (existing.isPresent()) break;
            }
        }
        if (existing.isEmpty() && outcome.nodeId() != null) {
            existing = store.findByNodeId(context.getProvider(), outcome.nodeId());
        }
        if (existing.isPresent()) {
            ScmApiProposalRecord row = existing.get();
            if (outcome.state() != null) row.setState(outcome.state());
            if (outcome.title() != null) row.setTitle(outcome.title());
            if (outcome.url() != null) row.setUrl(outcome.url());
            if (outcome.nodeId() != null) row.setNodeId(outcome.nodeId());
            row.setUpdatedAt(Instant.now());
            row.setLastActionId(context.getActionId());
            store.update(row);
            return Optional.of(row.getId());
        }
        if (outcome.number() == null || outcome.kind() == null || context.getRepoOwner() == null) {
            return Optional.empty();
        }
        ScmApiProposalRecord row = ScmApiProposalRecord.builder()
                .provider(context.getProvider())
                .repoOwner(context.getRepoOwner())
                .repoName(context.getRepoName())
                .kind(outcome.kind())
                .number(outcome.number())
                .url(outcome.url())
                .nodeId(outcome.nodeId())
                .title(outcome.title())
                .state(outcome.state() == null ? State.OPEN : outcome.state())
                .createdBy(context.getResolvedUser())
                .createdByScmUsername(context.getScmLogin())
                .createdActionId(context.getActionId())
                .lastActionId(context.getActionId())
                .build();
        store.save(row);
        return Optional.of(row.getId());
    }

    /** Exposed for the dashboard's read side, which joins action records to their proposal. */
    public ScmApiProposalStore store() {
        return store;
    }
}
