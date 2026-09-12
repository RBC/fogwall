package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.ScmApiRequestContext.SCM_API_REQUEST_ATTR;

import com.rbc.fogwall.db.model.ScmApiActionStatus;
import com.rbc.fogwall.scmapi.HeadCommitValidator;
import com.rbc.fogwall.servlet.RequestBodyWrapper;
import com.rbc.fogwall.servlet.ScmApiErrorResponse;
import com.rbc.fogwall.servlet.ScmApiRequestContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Enforces {@code providers.<name>.scm-api.require-validated-head}: refuses a pull/merge request create whose head
 * commit fogwall has no push record for. See {@link HeadCommitValidator}.
 *
 * <p>Runs after the dialect's gate filter, keyed off {@link ScmApiRequestContext#getMutationField()} — set by that
 * filter — so it only ever looks at a request already allowlisted and authorized. One class serves all three dialects,
 * parameterized the same way {@link ScmApiContentInspectionFilter} is: dialect-specific extraction and resolution are
 * supplied as functions at registration time, rather than one subclass per dialect.
 */
@Slf4j
@RequiredArgsConstructor
public class ScmApiHeadValidationFilter implements Filter {

    private static final JsonMapper MAPPER = new JsonMapper();

    /**
     * Resolves a caller-supplied head ref to the tip commit SHA it currently names — differs per dialect in both the
     * fork-encoding it parses and the credential it presents upstream. Empty means unresolvable.
     */
    public interface HeadShaResolution {
        Optional<String> resolve(HttpServletRequest request, ScmApiRequestContext context, String headRef);
    }

    /**
     * The create operation this check applies to, as {@link ScmApiRequestContext#getMutationField()} names it —
     * {@code createPullRequest}, {@code merge_requests.create}, or {@code pulls.create}. Every other operation on the
     * dialect (update, close, comment) passes through untouched.
     */
    private final String createOperation;

    /** Reads the head ref a create body names, from the parsed request body. */
    private final Function<JsonNode, Optional<String>> headRefExtractor;

    private final HeadShaResolution shaResolver;
    private final HeadCommitValidator headCommitValidator;
    private final boolean requireValidatedHead;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        var context = (ScmApiRequestContext) httpRequest.getAttribute(SCM_API_REQUEST_ATTR);

        if (!requireValidatedHead || context == null || !createOperation.equals(context.getMutationField())) {
            chain.doFilter(request, response);
            return;
        }

        JsonNode body = parse(readBody(request));
        Optional<String> headRef = body == null ? Optional.empty() : headRefExtractor.apply(body);
        if (headRef.isEmpty()) {
            fail(context, response, "Could not read the head branch from the request");
            return;
        }

        Optional<String> headSha = shaResolver.resolve(httpRequest, context, headRef.get());
        if (headSha.isEmpty()) {
            fail(context, response, "Could not resolve head branch '" + headRef.get() + "' to a commit upstream");
            return;
        }

        if (headCommitValidator.isValidated(headSha.get())) {
            chain.doFilter(request, response);
            return;
        }

        deny(
                context,
                response,
                "Head branch '" + headRef.get()
                        + "' has no fogwall push record for its current commit — push it through fogwall, then"
                        + " reopen");
    }

    /** No decision was reachable — the request named no head, or it could not be resolved upstream. */
    private static void fail(ScmApiRequestContext context, ServletResponse response, String reason) throws IOException {
        respond(context, response, ScmApiActionStatus.ERROR, reason);
    }

    /** A decision was reached, and it was no: the head commit resolved, but fogwall has no push record for it. */
    private static void deny(ScmApiRequestContext context, ServletResponse response, String reason) throws IOException {
        respond(context, response, ScmApiActionStatus.DENIED, reason);
    }

    private static void respond(
            ScmApiRequestContext context, ServletResponse response, ScmApiActionStatus status, String reason)
            throws IOException {
        log.debug("SCM API proxy require-validated-head refused the request ({}): {}", status, reason);
        context.setStatus(status);
        context.setReason(reason);
        ScmApiErrorResponse.write((HttpServletResponse) response, HttpServletResponse.SC_FORBIDDEN, reason);
    }

    private static byte[] readBody(ServletRequest request) throws IOException {
        return request instanceof RequestBodyWrapper wrapper
                ? wrapper.getBody()
                : request.getInputStream().readAllBytes();
    }

    private static JsonNode parse(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            log.warn("Could not parse SCM API request body to check its head commit: {}", e.getMessage());
            return null;
        }
    }
}
