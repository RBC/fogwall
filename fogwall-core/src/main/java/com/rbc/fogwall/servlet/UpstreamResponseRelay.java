package com.rbc.fogwall.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Relays an upstream response body to the client while keeping a copy of it, up to a bound. A mutation response is a
 * small JSON document naming what the upstream did, which the proposal registry reads; a body past the bound is relayed
 * in full but not kept, so a large or unexpected response costs nothing beyond the relay itself.
 */
final class UpstreamResponseRelay {

    /** A mutation response that is larger than this is not something the registry would read anyway. */
    static final int MAX_CAPTURED_BYTES = 1024 * 1024;

    private UpstreamResponseRelay() {}

    /** Copies {@code in} to {@code out}; returns the bytes copied, or null once they exceed the bound. */
    static byte[] relayAndCapture(InputStream in, OutputStream out) throws IOException {
        ByteArrayOutputStream copy = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        boolean overflowed = false;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
            if (!overflowed) {
                if (copy.size() + n > MAX_CAPTURED_BYTES) {
                    overflowed = true;
                    copy = null;
                } else {
                    copy.write(buffer, 0, n);
                }
            }
        }
        return copy == null ? null : copy.toByteArray();
    }
}
