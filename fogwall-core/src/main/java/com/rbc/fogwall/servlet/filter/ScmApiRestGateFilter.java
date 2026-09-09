package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.ScmApiGateResponse.deny;
import static com.rbc.fogwall.servlet.ScmApiGateResponse.fail;

import com.rbc.fogwall.scmapi.OwnerRepo;
import com.rbc.fogwall.scmapi.ScmApiRestMatch;
import com.rbc.fogwall.servlet.RequestBodyWrapper;
import com.rbc.fogwall.servlet.ScmApiQueryPolicy;
import com.rbc.fogwall.servlet.ScmApiRequestContext;
import com.rbc.fogwall.servlet.ScmApiRestPath;
import com.rbc.fogwall.servlet.ScmApiRestPathPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * The path-addressed REST half of {@link ScmApiGateFilter}, shared by GitLab and Forgejo/Gitea: both read the target
 * off the URL, gate reads by authentication alone, and deny any non-GET the allowlist does not recognise. They differ
 * only in the three hooks below — which encoded separators a segment may legitimately carry, which allowlist matches
 * the operation, and how the matched request names the repository to authorize against.
 */
public interface ScmApiRestGateFilter extends ScmApiGateFilter {

    ScmApiRestPathPolicy.EncodedSeparators separators();

    Optional<ScmApiRestMatch> matchAllowlist(String method, String path);

    /**
     * The repository a matched request must be authorized against. Straight off the path for Forgejo; for GitLab a
     * {@code mr create} names the upstream only in the body, so this resolves that. Empty means deny.
     */
    Optional<OwnerRepo> resolveTarget(HttpServletRequest request, RequestBodyWrapper wrapper, ScmApiRestMatch match)
            throws IOException;

    /** The refusal when {@link #resolveTarget} returns empty; only GitLab's fork path can reach it. */
    default String unresolvedTargetMessage() {
        return "Request names a target that could not be resolved to a repository";
    }

    @Override
    default GateOutcome evaluate(
            HttpServletRequest request,
            HttpServletResponse response,
            ScmApiRequestContext context,
            RequestBodyWrapper wrapper)
            throws IOException {
        String method = request.getMethod();
        String path = ScmApiRestPath.rawSubPath(request);

        // Checked ahead of the read/mutate split so it covers GETs too, which skip the allowlist.
        if (!ScmApiRestPathPolicy.isForwardable(path, separators())) {
            fail(context, response, HttpServletResponse.SC_BAD_REQUEST, "Malformed request path");
            return GateOutcome.REFUSED;
        }

        // A request whose shape no supported CLI produces is refused before anything downstream reads what it
        // carries. The forwarder checks again, as it does the path.
        String refusedParameter =
                ScmApiQueryPolicy.refusedParameter(request.getQueryString(), !"GET".equalsIgnoreCase(method));
        if (refusedParameter != null) {
            deny(
                    context,
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Query parameter '" + refusedParameter + "' is not permitted on this request");
            return GateOutcome.REFUSED;
        }

        if ("GET".equalsIgnoreCase(method)) {
            return GateOutcome.FORWARD;
        }

        // The request body is the audit evidence for a write, as the GraphQL variables are for GitHub — recorded
        // before the allowlist so a denied operation carries what it tried to send. The audit filter drops it again
        // on a content rejection, so the secret fogwall just refused never lands in its own database.
        context.setVariablesJson(payloadOf(wrapper));

        Optional<ScmApiRestMatch> match = matchAllowlist(method, path);
        if (match.isEmpty()) {
            deny(
                    context,
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Operation '" + method + " " + path + "' is not allowlisted");
            return GateOutcome.REFUSED;
        }

        String operation = match.get().operation();
        context.setMutationField(operation);

        Optional<OwnerRepo> target = resolveTarget(request, wrapper, match.get());
        if (target.isEmpty()) {
            fail(context, response, HttpServletResponse.SC_FORBIDDEN, unresolvedTargetMessage());
            return GateOutcome.REFUSED;
        }
        return new GateOutcome.Mutation(operation, target.get());
    }

    private static String payloadOf(RequestBodyWrapper wrapper) {
        byte[] body = wrapper.getBody();
        return body == null || body.length == 0 ? null : new String(body, StandardCharsets.UTF_8);
    }
}
