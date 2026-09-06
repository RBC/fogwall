package com.rbc.fogwall.servlet;

import com.rbc.fogwall.db.model.ScmApiActionStatus;
import com.rbc.fogwall.net.FogwallHttpExecutor;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;

/**
 * Thin forwarding servlet for REST-based SCM API proxy dialects — GitLab today, and any future path-based REST dialect
 * (Gitea/Forgejo look like good candidates per docs/internals/SCM_API_PROXY.md). Unlike
 * {@link ScmApiGraphQlForwardServlet} (GitHub's single fixed GraphQL endpoint), a REST dialect's target URL varies per
 * request: this relays the same sub-path and query string the caller hit, under the provider's REST API base URL,
 * preserving method and body. Uses the caller's own {@code Authorization} header unchanged — same BYO-token model as
 * the GraphQL forwarder.
 */
@Slf4j
public class ScmApiRestForwardServlet extends HttpServlet {

    private final URI upstreamApiBaseUri;
    private final ScmApiRestPathPolicy.EncodedSeparators encodedSeparators;

    public ScmApiRestForwardServlet(
            String upstreamApiBaseUrl, ScmApiRestPathPolicy.EncodedSeparators encodedSeparators) {
        this.upstreamApiBaseUri = URI.create(upstreamApiBaseUrl);
        this.encodedSeparators = encodedSeparators;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        URI target = upstreamUrl(request);
        if (target == null) {
            rejectTarget(response);
            return;
        }
        forward(request, response, Request.get(target));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        URI target = upstreamUrl(request);
        if (target == null) {
            rejectTarget(response);
            return;
        }
        forward(request, response, Request.post(target).bodyByteArray(readBody(request), ContentType.APPLICATION_JSON));
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        URI target = upstreamUrl(request);
        if (target == null) {
            rejectTarget(response);
            return;
        }
        forward(request, response, Request.put(target).bodyByteArray(readBody(request), ContentType.APPLICATION_JSON));
    }

    /**
     * The upstream URI for this request, or {@code null} when the caller's path is not one this servlet will forward.
     *
     * <p>The sub-path is relayed <b>as the caller sent it</b>, still encoded — taking it from
     * {@link HttpServletRequest#getPathInfo()} would decode {@code /projects/acme%2Fwidgets} to
     * {@code /projects/acme/widgets}, which GitLab reads as a different (and usually nonexistent) project, the same
     * decode hazard {@link ScmApiRestPath} exists to avoid on the authorization side.
     *
     * <p>Because that path is caller-controlled and is concatenated onto the provider's base, the result is checked to
     * still address the configured provider before it is used: same scheme, host, port, and still under the base path.
     * The gate filters apply {@link ScmApiRestPathPolicy} before this point; repeating it here keeps the guarantee
     * attached to the request that is actually sent, rather than resting on filter ordering.
     */
    private URI upstreamUrl(HttpServletRequest request) {
        String subPath = ScmApiRestPath.rawSubPath(request);
        if (!ScmApiRestPathPolicy.isForwardable(subPath, encodedSeparators)) {
            return null;
        }
        String query = request.getQueryString();
        URI target;
        try {
            target = new URI(upstreamApiBaseUri + subPath + (query != null ? "?" + query : ""));
        } catch (URISyntaxException e) {
            return null;
        }
        return addressesConfiguredUpstream(target) ? target : null;
    }

    /** Whether {@code target} still points at the provider this servlet was configured for. */
    private boolean addressesConfiguredUpstream(URI target) {
        return target.isAbsolute()
                && !target.isOpaque()
                && Objects.equals(target.getScheme(), upstreamApiBaseUri.getScheme())
                && Objects.equals(target.getHost(), upstreamApiBaseUri.getHost())
                && target.getPort() == upstreamApiBaseUri.getPort()
                && target.getRawUserInfo() == null
                && target.getRawPath() != null
                && target.getRawPath().startsWith(upstreamApiBaseUri.getRawPath());
    }

    private static void rejectTarget(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response.getOutputStream().write("{\"error\":\"Malformed request path\"}".getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] readBody(HttpServletRequest request) throws IOException {
        return request instanceof RequestBodyWrapper wrapper
                ? wrapper.getBody()
                : request.getInputStream().readAllBytes();
    }

    private void forward(HttpServletRequest request, HttpServletResponse response, Request upstreamRequest)
            throws IOException {
        var context = (ScmApiRequestContext) request.getAttribute(ScmApiRequestContext.SCM_API_REQUEST_ATTR);
        String authHeader = ScmApiTokenExtractor.authHeaderName(request);
        if (authHeader != null) {
            upstreamRequest.addHeader(authHeader, request.getHeader(authHeader));
        }

        try {
            ForwardResult result = upstreamRequest
                    .execute(FogwallHttpExecutor.instance())
                    .handleResponse(upstream -> new ForwardResult(
                            upstream.getCode(),
                            upstream.getEntity() != null
                                    ? EntityUtils.toByteArray(upstream.getEntity())
                                    : new byte[0]));

            response.setStatus(result.statusCode());
            response.setContentType("application/json");
            response.getOutputStream().write(result.body());

            if (context != null && context.getMutationField() != null) {
                context.setStatus(ScmApiActionStatus.FORWARDED);
            }
        } catch (IOException e) {
            log.warn("SCM API proxy forward failed: {}", e.getMessage());
            if (context != null && context.getMutationField() != null) {
                context.setStatus(ScmApiActionStatus.ERROR);
                context.setReason("Failed to forward to upstream: " + e.getMessage());
            }
            response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
            response.setContentType("application/json");
            response.getOutputStream()
                    .write(("{\"error\":\"upstream forward failed\"}").getBytes(StandardCharsets.UTF_8));
        }
    }

    private record ForwardResult(int statusCode, byte[] body) {}
}
