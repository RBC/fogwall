package com.rbc.fogwall.servlet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class RequestBodyWrapperTest {

    private static HttpServletRequest requestWithBody(byte[] body) throws IOException {
        HttpServletRequest req = mock(HttpServletRequest.class);
        ByteArrayInputStream backing = new ByteArrayInputStream(body);
        when(req.getInputStream()).thenReturn(new ServletInputStream() {
            @Override
            public int read() {
                return backing.read();
            }

            @Override
            public int read(byte[] b, int off, int len) {
                return backing.read(b, off, len);
            }

            @Override
            public boolean isFinished() {
                return backing.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {}
        });
        return req;
    }

    @Test
    void bodyUnderTheLimitIsBufferedIntact() throws Exception {
        byte[] body = new byte[1024];
        for (int i = 0; i < body.length; i++) body[i] = (byte) i;

        RequestBodyWrapper wrapper = new RequestBodyWrapper(requestWithBody(body), 4096);

        assertArrayEquals(body, wrapper.getBody());
    }

    @Test
    void bodyExactlyAtTheLimitIsAccepted() throws Exception {
        byte[] body = new byte[2048];

        RequestBodyWrapper wrapper = new RequestBodyWrapper(requestWithBody(body), 2048);

        assertEquals(2048, wrapper.getBody().length);
    }

    @Test
    void bodyOverTheLimitIsRejected() throws Exception {
        HttpServletRequest req = requestWithBody(new byte[5000]);

        PushTooLargeException e = assertThrows(PushTooLargeException.class, () -> new RequestBodyWrapper(req, 2048));

        assertEquals(2048, e.getLimitBytes());
    }

    /** A chunked push declares no Content-Length, so the counting read is the only thing that can bound it. */
    @Test
    void limitAppliesWithNoContentLengthDeclared() throws Exception {
        HttpServletRequest req = requestWithBody(new byte[300_000]);
        when(req.getContentLengthLong()).thenReturn(-1L);

        assertThrows(PushTooLargeException.class, () -> new RequestBodyWrapper(req, 65_536));
    }

    @Test
    void zeroMeansNoConfiguredLimit() throws Exception {
        byte[] body = new byte[300_000];

        RequestBodyWrapper wrapper = new RequestBodyWrapper(requestWithBody(body), 0);

        assertEquals(300_000, wrapper.getBody().length);
    }

    @Test
    void emptyBodyIsFine() throws Exception {
        RequestBodyWrapper wrapper = new RequestBodyWrapper(requestWithBody(new byte[0]), 2048);

        assertEquals(0, wrapper.getBody().length);
    }
}
