package com.rbc.fogwall.servlet;

import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.model.PushStatus;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.net.OutboundProxyJetty;
import com.rbc.fogwall.net.ResolvedOutboundProxy;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import javax.net.ssl.SSLContext;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.client.BytesRequestContent;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.transport.HttpClientTransportOverHTTP;
import org.eclipse.jetty.ee11.proxy.AsyncProxyServlet;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;

@Slf4j
public class FogwallServlet extends AsyncProxyServlet.Transparent {
    public static final String GIT_REQUEST_ATTR = "fogwall.gitRequest";
    public static final String ERROR_ATTR = "fogwall.error";
    public static final String PRE_APPROVED_ATTR = "fogwall.preApproved";
    /** Request attribute holding the UUID of the original APPROVED push record for a transparent-proxy re-push. */
    public static final String APPROVED_PUSH_ID_ATTR = "fogwall.approvedPushId";

    public static final String SERVICE_URL_ATTR = "fogwall.serviceUrl";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String GITHUB_REQUEST_ID_HEADER = "X-Github-Request-Id";

    private final PushStore pushStore;
    private final SSLContext upstreamSslContext;
    private final ResolvedOutboundProxy outboundProxy;

    public FogwallServlet(PushStore pushStore) {
        this(pushStore, null, null);
    }

    public FogwallServlet(PushStore pushStore, SSLContext upstreamSslContext) {
        this(pushStore, upstreamSslContext, null);
    }

    public FogwallServlet(PushStore pushStore, SSLContext upstreamSslContext, ResolvedOutboundProxy outboundProxy) {
        this.pushStore = pushStore;
        this.upstreamSslContext = upstreamSslContext;
        this.outboundProxy = outboundProxy;
    }

    @Override
    protected HttpClient newHttpClient() {
        HttpClient client;
        if (upstreamSslContext != null) {
            var sslFactory = new SslContextFactory.Client();
            sslFactory.setSslContext(upstreamSslContext);
            var connector = new ClientConnector();
            connector.setSslContextFactory(sslFactory);
            client = new HttpClient(new HttpClientTransportOverHTTP(connector));
        } else {
            client = super.newHttpClient();
        }
        OutboundProxyJetty.configure(client, outboundProxy);
        return client;
    }

    @Override
    protected void service(HttpServletRequest clientRequest, HttpServletResponse proxyResponse)
            throws ServletException, IOException {
        var details = (GitRequestDetails) clientRequest.getAttribute(GIT_REQUEST_ATTR);
        var canProxy = details != null && details.getResult() == GitRequestDetails.GitResult.ALLOWED;
        if (canProxy) {
            super.service(clientRequest, proxyResponse);
        }
    }

    // TODO: Allow Via header to be sent if configured (enabled via opt-in)
    @Override
    protected void addViaHeader(Request proxyRequest) {
        // no-op - don't send "Via" header to upstream
    }

    @Override
    protected void addViaHeader(HttpServletRequest clientRequest, Request proxyRequest) {
        // no-op - don't send "Via" header to upstream
    }

    // TODO: Allow X-Forwarded-* headers to be sent if configured (enabled via opt-in)
    // TODO: Allow X-Forwarded-* headers to be customized
    @Override
    protected void addProxyHeaders(HttpServletRequest clientRequest, Request proxyRequest) {
        // no-op - don't send proxy headers (X-Forwarded-*, Forwarded) to upstream
    }

    @Override
    protected Request.Content proxyRequestContent(
            HttpServletRequest request, HttpServletResponse response, Request proxyRequest) throws IOException {
        if (request instanceof RequestBodyWrapper wrapper) {
            byte[] body = wrapper.getBody();
            if (body != null && body.length > 0) {
                log.debug("Sending {} bytes to upstream from wrapped request", body.length);
                return new BytesRequestContent(body);
            }
        }

        // Fall back to default implementation
        return super.proxyRequestContent(request, response, proxyRequest);
    }

    /**
     * Called by Jetty on the async thread after the upstream server responds successfully (2xx). Transitions the
     * original approved push record to {@link PushStatus#FORWARDED}.
     */
    @Override
    protected void onProxyResponseSuccess(
            HttpServletRequest clientRequest, HttpServletResponse proxyResponse, Response serverResponse) {
        String pushId = (String) clientRequest.getAttribute(APPROVED_PUSH_ID_ATTR);
        if (pushId != null) {
            log.info(
                    "Transparent proxy re-push {} forwarded successfully (upstream HTTP {})",
                    pushId,
                    serverResponse.getStatus());
            pushStore.updateForwardStatus(pushId, PushStatus.FORWARDED, null);
        }
        super.onProxyResponseSuccess(clientRequest, proxyResponse, serverResponse);
    }

    /**
     * Called by Jetty on the async thread when the upstream returns a non-2xx response or a network error. Transitions
     * the original approved push record to {@link PushStatus#ERROR}.
     */
    @Override
    protected void onProxyResponseFailure(
            HttpServletRequest clientRequest,
            HttpServletResponse proxyResponse,
            Response serverResponse,
            Throwable failure) {
        String pushId = (String) clientRequest.getAttribute(APPROVED_PUSH_ID_ATTR);
        if (pushId != null) {
            int upstreamStatus = serverResponse != null ? serverResponse.getStatus() : 0;
            String errorMessage = upstreamStatus > 0
                    ? "Upstream returned HTTP " + upstreamStatus
                    : "Upstream error: " + (failure != null ? failure.getMessage() : "unknown");
            log.warn("Transparent proxy re-push {} failed: {}", pushId, errorMessage);
            pushStore.updateForwardStatus(pushId, PushStatus.ERROR, errorMessage);
        }
        super.onProxyResponseFailure(clientRequest, proxyResponse, serverResponse, failure);
    }
}
