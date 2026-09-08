package com.rbc.fogwall.scmapi;

import com.rbc.fogwall.servlet.ScmApiRequestContext;
import java.util.Optional;

/**
 * Reads what a forwarded mutation created or touched out of the upstream's own response — the number, URL, node ID and
 * state the upstream assigned — so the registry can name it. A client-server API's response is the authoritative
 * statement of what now exists, which is what the git path can never say about a push. One implementation per dialect,
 * chosen where the dialect's gate filter is.
 */
public interface ProposalResponseReader {

    /**
     * @param context the request as the gate filter left it: mutation field, node ID and type, variables
     * @param requestPath the still-encoded sub-path of a REST request, or null for GraphQL
     * @param body the upstream's response body, or null when it was not captured
     * @return what the response says about the proposal, or empty when it names nothing the registry can use
     */
    Optional<ProposalOutcome> read(ScmApiRequestContext context, String requestPath, String body);
}
