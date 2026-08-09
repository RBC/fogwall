package com.rbc.fogwall.servlet;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class RequestBodyWrapper extends HttpServletRequestWrapper {

    /** Largest body this class can hold, since the body is a single array. */
    public static final long ABSOLUTE_MAX_BYTES = Integer.MAX_VALUE - 8L;

    private static final int CHUNK_SIZE = 64 * 1024;

    private final byte[] body;

    /** Wraps {@code request}, buffering its body with no size limit. */
    public RequestBodyWrapper(HttpServletRequest request) throws IOException {
        this(request, 0);
    }

    /**
     * Wraps {@code request}, buffering its body up to {@code maxBytes}.
     *
     * @param maxBytes size limit in bytes; 0 means no configured limit, in which case {@link #ABSOLUTE_MAX_BYTES} still
     *     applies because the body is held as one array
     * @throws PushTooLargeException if the body exceeds the limit. Reading stops at that point rather than draining the
     *     rest, so an over-size body costs only the limit in heap and not its full size.
     */
    public RequestBodyWrapper(HttpServletRequest request, long maxBytes) throws IOException {
        super(request);
        long limit = maxBytes > 0 ? Math.min(maxBytes, ABSOLUTE_MAX_BYTES) : ABSOLUTE_MAX_BYTES;
        InputStream inputStream = request.getInputStream();
        body = inputStream != null ? readUpTo(inputStream, limit) : new byte[0];
        log.debug("RequestBodyWrapper: Content-Length={}, cached {} bytes", request.getContentLength(), body.length);
    }

    /**
     * Reads {@code in} fully, failing as soon as more than {@code limit} bytes have arrived.
     *
     * <p>{@code Content-Length} is checked separately and earlier, but it cannot be relied on alone: git clients send
     * push bodies with chunked transfer encoding, where no length is declared up front. This counting read is what
     * actually bounds the allocation.
     */
    private static byte[] readUpTo(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[CHUNK_SIZE];
        long total = 0;
        int read;
        while ((read = in.read(chunk)) != -1) {
            total += read;
            if (total > limit) {
                throw new PushTooLargeException(limit, total);
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public int read() throws IOException {
                return byteArrayInputStream.read();
            }

            @Override
            public boolean isFinished() {
                return byteArrayInputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // No implementation needed
            }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(this.getInputStream()));
    }
}
