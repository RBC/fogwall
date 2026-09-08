package com.rbc.fogwall.servlet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

class UpstreamResponseRelayTest {

    @Test
    void smallBody_isRelayedAndKept() throws Exception {
        byte[] body = "{\"number\":1}".getBytes();
        var out = new ByteArrayOutputStream();
        byte[] kept = UpstreamResponseRelay.relayAndCapture(new ByteArrayInputStream(body), out);
        assertArrayEquals(body, out.toByteArray());
        assertArrayEquals(body, kept);
    }

    @Test
    void bodyPastTheBound_isRelayedInFullButNotKept() throws Exception {
        byte[] body = new byte[UpstreamResponseRelay.MAX_CAPTURED_BYTES + 1];
        var out = new ByteArrayOutputStream();
        byte[] kept = UpstreamResponseRelay.relayAndCapture(new ByteArrayInputStream(body), out);
        assertArrayEquals(body, out.toByteArray(), "the client gets every byte regardless");
        assertNull(kept);
    }
}
