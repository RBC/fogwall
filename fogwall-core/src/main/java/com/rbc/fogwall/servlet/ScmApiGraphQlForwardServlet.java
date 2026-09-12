package com.rbc.fogwall.servlet;

import com.rbc.fogwall.db.model.ScmApiActionStatus;
import com.rbc.fogwall.net.FogwallHttpExecutor;
import com.rbc.fogwall.observability.FogwallTelemetry;
import com.rbc.fogwall.scmapi.ProposalRegistrar;
import com.rbc.fogwall.scmapi.ScmApiUserAgent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ContentType;

/**
 * Thin forwarding servlet for GitHub's GraphQL SCM API dialect: relays an already-buffered, already-checked GraphQL
 * request to the provider's single GraphQL endpoint, using the caller's own {@code Authorization} header (BYO-token
 * model — fogwall never mints or substitutes its own credential), and relays the response back verbatim. The REST
 * dialects use {@link ScmApiRestForwardServlet}, whose target URL varies per request.
 */
@Slf4j
public class ScmApiGraphQlForwardServlet extends HttpServlet {

    private final String upstreamGraphqlUrl;

    private final ProposalRegistrar proposalRegistrar;

    private final FogwallTelemetry telemetry;

    public ScmApiGraphQlForwardServlet(
            String upstreamGraphqlUrl, ProposalRegistrar proposalRegistrar, FogwallTelemetry telemetry) {
        this.upstreamGraphqlUrl = upstreamGraphqlUrl;
        this.proposalRegistrar = proposalRegistrar;
        this.telemetry = telemetry;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        var context = (ScmApiRequestContext) request.getAttribute(ScmApiRequestContext.SCM_API_REQUEST_ATTR);
        String authHeader = ScmApiTokenExtractor.authHeaderName(request);
        // The gate filter's wrapper is what bounds the read. An unwrapped request means the chain that authorizes
        // this one is missing, so it fails rather than reading the stream raw.
        if (!(request instanceof RequestBodyWrapper wrapper)) {
            throw new IllegalStateException(
                    "SCM API request reached the forwarder unwrapped; the gate filter is not in the chain");
        }
        byte[] body = wrapper.getBody();

        Request upstreamRequest = Request.post(upstreamGraphqlUrl);
        if (authHeader != null) {
            upstreamRequest.addHeader(authHeader, request.getHeader(authHeader));
        }
        ScmApiUserAgent.relay(upstreamRequest, ScmApiUserAgent.of(request));

        boolean mutation = context != null && context.getMutationField() != null;
        String provider = context != null && context.getProvider() != null ? context.getProvider() : "unknown";
        Span forwardSpan = telemetry.startScmApiForwardSpan(provider);
        try {
            // A read is streamed, with the upstream's content type relayed: nothing here assumes it is JSON. A
            // mutation's response is also kept (bounded) — it is the upstream's own statement of what it created,
            // and the proposal registry reads it once the client has it.
            var captured = new byte[1][];
            int upstreamStatus = upstreamRequest
                    .bodyByteArray(body, ContentType.APPLICATION_JSON)
                    .execute(FogwallHttpExecutor.instance())
                    .handleResponse(upstream -> {
                        response.setStatus(upstream.getCode());
                        var entity = upstream.getEntity();
                        if (entity != null && entity.getContentType() != null) {
                            response.setContentType(entity.getContentType());
                        }
                        if (entity != null && entity.getContentLength() >= 0) {
                            response.setContentLengthLong(entity.getContentLength());
                        }
                        if (entity != null) {
                            try (var in = entity.getContent()) {
                                if (mutation) {
                                    captured[0] = UpstreamResponseRelay.relayAndCapture(in, response.getOutputStream());
                                } else {
                                    in.transferTo(response.getOutputStream());
                                }
                            }
                        }
                        return upstream.getCode();
                    });

            forwardSpan.setAttribute("http.response.status_code", upstreamStatus);
            if (mutation) {
                context.setStatus(ScmApiActionStatus.FORWARDED);
                proposalRegistrar.recordUpstreamResponse(context, null, upstreamStatus, captured[0]);
            }
        } catch (IOException e) {
            forwardSpan.recordException(e);
            forwardSpan.setStatus(StatusCode.ERROR, "upstream forward failed");
            log.warn("SCM API proxy forward failed: {}", e.getMessage());
            if (context != null && context.getMutationField() != null) {
                context.setStatus(ScmApiActionStatus.ERROR);
                context.setReason("Failed to forward to upstream: " + e.getMessage());
            }
            ScmApiErrorResponse.write(response, HttpServletResponse.SC_BAD_GATEWAY, "upstream forward failed");
        } finally {
            forwardSpan.end();
        }
    }
}
