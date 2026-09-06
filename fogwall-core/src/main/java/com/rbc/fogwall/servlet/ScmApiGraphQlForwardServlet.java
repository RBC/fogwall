package com.rbc.fogwall.servlet;

import com.rbc.fogwall.db.model.ScmApiActionStatus;
import com.rbc.fogwall.net.FogwallHttpExecutor;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;

/**
 * Thin forwarding servlet for GitHub's GraphQL SCM API dialect: relays an already-buffered, already-checked GraphQL
 * request to the provider's single GraphQL endpoint, using the caller's own {@code Authorization} header (BYO-token
 * model — fogwall never mints or substitutes its own credential), and relays the response back verbatim. The REST
 * dialects (GitLab today) use {@link ScmApiRestForwardServlet} instead, since their target URL varies per request
 * rather than being one fixed endpoint.
 *
 * <p>Uses the blocking Apache HttpClient5 fluent API via {@link FogwallHttpExecutor} rather than Jetty's
 * {@link com.rbc.fogwall.servlet.FogwallServlet} (an {@code AsyncProxyServlet}) — a single POST/relay for a small JSON
 * body doesn't need async proxy machinery, and this mirrors the idiom already used for provider identity calls
 * ({@code GitHubProvider.fetchUserFromHttp}).
 */
@Slf4j
public class ScmApiGraphQlForwardServlet extends HttpServlet {

    private final String upstreamGraphqlUrl;

    public ScmApiGraphQlForwardServlet(String upstreamGraphqlUrl) {
        this.upstreamGraphqlUrl = upstreamGraphqlUrl;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        var context = (ScmApiRequestContext) request.getAttribute(ScmApiRequestContext.SCM_API_REQUEST_ATTR);
        String authHeader = ScmApiTokenExtractor.authHeaderName(request);
        byte[] body = request instanceof RequestBodyWrapper wrapper
                ? wrapper.getBody()
                : request.getInputStream().readAllBytes();

        Request upstreamRequest = Request.post(upstreamGraphqlUrl);
        if (authHeader != null) {
            upstreamRequest.addHeader(authHeader, request.getHeader(authHeader));
        }

        try {
            ForwardResult result = upstreamRequest
                    .bodyByteArray(body, ContentType.APPLICATION_JSON)
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
