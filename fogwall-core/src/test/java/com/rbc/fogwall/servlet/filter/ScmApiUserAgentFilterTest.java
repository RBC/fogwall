package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.ScmApiRequestContext.SCM_API_REQUEST_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.scmapi.ScmApiClientType;
import com.rbc.fogwall.servlet.ScmApiRequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

class ScmApiUserAgentFilterTest {

    private static HttpServletRequest request(String userAgent, ScmApiRequestContext context) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("User-Agent")).thenReturn(userAgent);
        when(req.getAttribute(SCM_API_REQUEST_ATTR)).thenReturn(context);
        return req;
    }

    @Test
    void recordsTheUserAgentClientTypeAndVersionForAudit() throws Exception {
        var context = new ScmApiRequestContext();
        var chain = mock(FilterChain.class);

        new ScmApiUserAgentFilter()
                .doFilter(
                        request("tea/0.15.1 (linux/amd64) go-sdk/v1.2.0", context),
                        mock(HttpServletResponse.class),
                        chain);

        assertEquals("tea/0.15.1 (linux/amd64) go-sdk/v1.2.0", context.getUserAgent());
        assertEquals(ScmApiClientType.TEA_CLI, context.getClientType());
        assertEquals("0.15.1", context.getClientVersion());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void recordsAnUnknownClientAndPassesItThrough() throws Exception {
        var context = new ScmApiRequestContext();
        var chain = mock(FilterChain.class);

        new ScmApiUserAgentFilter().doFilter(request("curl/8.9.1", context), mock(HttpServletResponse.class), chain);

        verify(chain).doFilter(any(), any());
        assertEquals(ScmApiClientType.UNKNOWN, context.getClientType());
        assertNull(context.getStatus(), "recording never stamps a denial on the audit record");
    }
}
