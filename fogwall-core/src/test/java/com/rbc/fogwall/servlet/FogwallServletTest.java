package com.rbc.fogwall.servlet;

import static com.rbc.fogwall.servlet.FogwallServlet.APPROVED_PUSH_ID_ATTR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.model.PushStatus;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FogwallServletTest {

    private static final String PUSH_ID = "push-1";

    private final PushStore pushStore = mock(PushStore.class);
    private final FogwallServlet servlet = new FogwallServlet(pushStore);
    private final Map<String, Object> attributes = new HashMap<>();
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final ServletOutputStream clientOutput = mock(ServletOutputStream.class);

    @BeforeEach
    void setUp() throws IOException, ServletException {
        ServletConfig config = mock(ServletConfig.class);
        when(config.getServletName()).thenReturn("fogwall");
        when(config.getInitParameter("proxyTo")).thenReturn("http://upstream.example");
        when(config.getInitParameter("maxThreads")).thenReturn("4");
        when(config.getServletContext()).thenReturn(mock(ServletContext.class));
        servlet.init(config);

        when(request.getAttribute(anyString())).thenAnswer(inv -> attributes.get(inv.<String>getArgument(0)));
        doAnswer(inv -> attributes.put(inv.getArgument(0), inv.getArgument(1)))
                .when(request)
                .setAttribute(anyString(), any());
        AsyncContext asyncContext = mock(AsyncContext.class);
        when(asyncContext.getResponse()).thenReturn(response);
        when(request.getAsyncContext()).thenReturn(asyncContext);
        when(clientOutput.isReady()).thenReturn(true);
        doAnswer(inv -> {
                    inv.<WriteListener>getArgument(0).onWritePossible();
                    return null;
                })
                .when(clientOutput)
                .setWriteListener(any());
        when(response.getOutputStream()).thenReturn(clientOutput);
    }

    @AfterEach
    void tearDown() {
        servlet.destroy();
    }

    private static Response upstream(int status, HttpFields headers) {
        Response serverResponse = mock(Response.class);
        when(serverResponse.getStatus()).thenReturn(status);
        when(serverResponse.getHeaders()).thenReturn(headers);
        return serverResponse;
    }

    private static Response upstream(int status) {
        return upstream(status, HttpFields.EMPTY);
    }

    private void relay(Response serverResponse, byte[] body) {
        servlet.onResponseContent(request, response, serverResponse, body, 0, body.length, mock(Callback.class));
    }

    @Test
    void okReport_recordsForwarded() throws IOException {
        attributes.put(APPROVED_PUSH_ID_ATTR, PUSH_ID);
        Response serverResponse = upstream(200);
        relay(serverResponse, ReceivePackReportScannerTest.plainReport("unpack ok", "ok refs/heads/main"));

        servlet.onProxyResponseSuccess(request, response, serverResponse);

        verify(pushStore).updateForwardStatus(eq(PUSH_ID), eq(PushStatus.FORWARDED), isNull());
    }

    @Test
    void serverError_recordsErrorWithUpstreamStatus() {
        attributes.put(APPROVED_PUSH_ID_ATTR, PUSH_ID);
        Response serverResponse = upstream(500);
        relay(serverResponse, "Internal Server Error".getBytes(StandardCharsets.UTF_8));

        servlet.onProxyResponseSuccess(request, response, serverResponse);

        verify(pushStore).updateForwardStatus(PUSH_ID, PushStatus.ERROR, "Upstream returned HTTP 500");
        verify(pushStore, never()).updateForwardStatus(eq(PUSH_ID), eq(PushStatus.FORWARDED), any());
    }

    @Test
    void refusedRef_recordsErrorWithReason() throws IOException {
        attributes.put(APPROVED_PUSH_ID_ATTR, PUSH_ID);
        Response serverResponse = upstream(200);
        relay(
                serverResponse,
                ReceivePackReportScannerTest.plainReport(
                        "unpack ok", "ng refs/heads/main protected branch hook declined"));

        servlet.onProxyResponseSuccess(request, response, serverResponse);

        verify(pushStore)
                .updateForwardStatus(
                        PUSH_ID, PushStatus.ERROR, "Upstream rejected refs/heads/main: protected branch hook declined");
    }

    @Test
    void sidebandRefusedRef_recordsErrorWithReason() throws IOException {
        attributes.put(APPROVED_PUSH_ID_ATTR, PUSH_ID);
        Response serverResponse = upstream(200);
        byte[] body = ReceivePackReportScannerTest.sidebandReport("unpack ok", "ng refs/heads/main non-fast-forward");
        int half = body.length / 2;
        Callback first = mock(Callback.class);
        Callback second = mock(Callback.class);
        servlet.onResponseContent(request, response, serverResponse, body, 0, half, first);
        servlet.onResponseContent(request, response, serverResponse, body, half, body.length - half, second);

        servlet.onProxyResponseSuccess(request, response, serverResponse);

        verify(pushStore)
                .updateForwardStatus(PUSH_ID, PushStatus.ERROR, "Upstream rejected refs/heads/main: non-fast-forward");
        verify(clientOutput).write(body, 0, half);
        verify(clientOutput).write(body, half, body.length - half);
        verify(first).succeeded();
        verify(second).succeeded();
    }

    @Test
    void sidebandOkReport_recordsForwarded() throws IOException {
        attributes.put(APPROVED_PUSH_ID_ATTR, PUSH_ID);
        Response serverResponse = upstream(200);
        relay(serverResponse, ReceivePackReportScannerTest.sidebandReport("unpack ok", "ok refs/heads/main"));

        servlet.onProxyResponseSuccess(request, response, serverResponse);

        verify(pushStore).updateForwardStatus(eq(PUSH_ID), eq(PushStatus.FORWARDED), isNull());
    }

    @Test
    void emptyOkResponse_recordsError() {
        attributes.put(APPROVED_PUSH_ID_ATTR, PUSH_ID);

        servlet.onProxyResponseSuccess(request, response, upstream(200));

        verify(pushStore).updateForwardStatus(PUSH_ID, PushStatus.ERROR, "Upstream sent no report-status");
    }

    @Test
    void encodedOkResponse_isJudgedByStatusOnly() {
        attributes.put(APPROVED_PUSH_ID_ATTR, PUSH_ID);
        Response serverResponse = upstream(200, HttpFields.build().add(HttpHeader.CONTENT_ENCODING, "gzip"));
        relay(serverResponse, new byte[] {0x1f, (byte) 0x8b, 0x08, 0x00});

        servlet.onProxyResponseSuccess(request, response, serverResponse);

        verify(pushStore).updateForwardStatus(eq(PUSH_ID), eq(PushStatus.FORWARDED), isNull());
    }

    @Test
    void networkFailure_recordsError() {
        attributes.put(APPROVED_PUSH_ID_ATTR, PUSH_ID);

        servlet.onProxyResponseFailure(request, response, null, new IOException("connection reset"));

        verify(pushStore).updateForwardStatus(PUSH_ID, PushStatus.ERROR, "Upstream error: connection reset");
    }

    @Test
    void notAnApprovedRePush_recordsNothing() throws IOException {
        Response serverResponse = upstream(500);
        relay(serverResponse, ReceivePackReportScannerTest.plainReport("unpack ok"));

        servlet.onProxyResponseSuccess(request, response, serverResponse);

        verify(pushStore, never()).updateForwardStatus(anyString(), any(), any());
    }
}
