package com.rbc.fogwall.servlet;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Reads an upstream {@code git-receive-pack} response as it is relayed to the client and reports whether upstream
 * refused the push.
 *
 * <p>Smart HTTP answers a refused ref with HTTP 200: the refusal is a report-status line ({@code ng <ref> <reason>} or
 * {@code unpack <error>}), either as plain pkt-lines or nested inside sideband channel 1 when the client asked for
 * {@code side-band-64k}. Sideband channel 3 carries a fatal upstream error. The scanner consumes the response in the
 * chunks Jetty delivers and keeps at most one partial pkt-line per framing layer, so memory stays bounded by the
 * pkt-line maximum regardless of response size. It only reads the bytes it is given; relaying them is the caller's job.
 *
 * <p>JGit's {@code PacketLineIn} and {@code SideBandInputStream} pull from a blocking {@code InputStream}, so they
 * cannot consume push-delivered chunks without holding the whole response.
 */
final class ReceivePackReportScanner {

    private static final byte BAND_DATA = 1;
    private static final byte BAND_PROGRESS = 2;
    private static final byte BAND_ERROR = 3;
    private static final int MAX_FAILURES = 5;

    private enum Framing {
        UNKNOWN,
        PLAIN,
        SIDEBAND
    }

    private final PktLineReader outer = new PktLineReader();
    private final PktLineReader inner = new PktLineReader();
    private final List<String> failures = new ArrayList<>();
    private Framing framing = Framing.UNKNOWN;
    private boolean unpackOk;

    /** Consumes the next chunk of the response body. */
    void accept(byte[] buffer, int offset, int length) {
        outer.feed(buffer, offset, length, this::onOuterPacket);
    }

    /**
     * The reason upstream refused the push, or empty when the report says every ref was accepted. A response that is
     * not a readable report-status, or that ends without one, counts as a refusal: the git client treats it the same
     * way.
     */
    Optional<String> failure() {
        if (!failures.isEmpty()) {
            return Optional.of(String.join("; ", failures));
        }
        if (outer.isMalformed() || inner.isMalformed()) {
            return Optional.of("Upstream response is not a readable report-status");
        }
        if (outer.hasPartialPacket() || inner.hasPartialPacket()) {
            return Optional.of("Upstream response ended mid-report");
        }
        if (!unpackOk) {
            return Optional.of("Upstream sent no report-status");
        }
        return Optional.empty();
    }

    private void onOuterPacket(byte[] payload) {
        if (framing == Framing.UNKNOWN) {
            framing = payload.length > 0 && payload[0] >= BAND_DATA && payload[0] <= BAND_ERROR
                    ? Framing.SIDEBAND
                    : Framing.PLAIN;
        }
        if (framing == Framing.PLAIN) {
            onReportLine(payload, 0, payload.length);
            return;
        }
        if (payload.length == 0) {
            return;
        }
        switch (payload[0]) {
            case BAND_DATA -> inner.feed(payload, 1, payload.length - 1, p -> onReportLine(p, 0, p.length));
            case BAND_PROGRESS -> {}
            case BAND_ERROR -> addFailure("Upstream error: " + text(payload, 1, payload.length - 1));
            default -> outer.markMalformed();
        }
    }

    private void onReportLine(byte[] payload, int offset, int length) {
        String line = text(payload, offset, length);
        if (line.equals("unpack ok")) {
            unpackOk = true;
        } else if (line.startsWith("unpack ")) {
            addFailure("Upstream unpack failed: " + line.substring("unpack ".length()));
        } else if (line.startsWith("ng ")) {
            String rest = line.substring("ng ".length());
            int space = rest.indexOf(' ');
            addFailure(
                    space < 0
                            ? "Upstream rejected " + rest
                            : "Upstream rejected " + rest.substring(0, space) + ": " + rest.substring(space + 1));
        } else if (line.startsWith("ERR ")) {
            addFailure("Upstream error: " + line.substring("ERR ".length()));
        }
    }

    private void addFailure(String failure) {
        if (failures.size() < MAX_FAILURES) {
            failures.add(failure);
        }
    }

    private static String text(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.UTF_8).strip();
    }

    /**
     * Incremental pkt-line framer: a four-hex-digit length (including itself) followed by the payload. Lengths 0000,
     * 0001 and 0002 are flush, delimiter and response-end markers with no payload.
     */
    private static final class PktLineReader {
        private static final int HEADER_LENGTH = 4;
        private static final int MAX_PACKET_LENGTH = 65520;

        private final byte[] header = new byte[HEADER_LENGTH];
        private int headerFilled;
        private byte[] payload = new byte[0];
        private int payloadFilled;
        private boolean inPayload;
        private boolean malformed;

        void feed(byte[] buffer, int offset, int length, Consumer<byte[]> onPacket) {
            int pos = offset;
            int end = offset + length;
            while (pos < end && !malformed) {
                if (!inPayload) {
                    int take = Math.min(HEADER_LENGTH - headerFilled, end - pos);
                    System.arraycopy(buffer, pos, header, headerFilled, take);
                    headerFilled += take;
                    pos += take;
                    if (headerFilled == HEADER_LENGTH) {
                        startPacket(onPacket);
                    }
                } else {
                    int take = Math.min(payload.length - payloadFilled, end - pos);
                    System.arraycopy(buffer, pos, payload, payloadFilled, take);
                    payloadFilled += take;
                    pos += take;
                    if (payloadFilled == payload.length) {
                        inPayload = false;
                        onPacket.accept(payload);
                    }
                }
            }
        }

        private void startPacket(Consumer<byte[]> onPacket) {
            headerFilled = 0;
            int packetLength = parseLength();
            if (packetLength < 0 || packetLength == 3 || packetLength > MAX_PACKET_LENGTH) {
                malformed = true;
            } else if (packetLength > HEADER_LENGTH) {
                payload = new byte[packetLength - HEADER_LENGTH];
                payloadFilled = 0;
                inPayload = true;
            } else if (packetLength == HEADER_LENGTH) {
                onPacket.accept(new byte[0]);
            }
        }

        private int parseLength() {
            int value = 0;
            for (byte b : header) {
                int digit = Character.digit((char) (b & 0xff), 16);
                if (digit < 0) {
                    return -1;
                }
                value = value * 16 + digit;
            }
            return value;
        }

        void markMalformed() {
            malformed = true;
        }

        boolean isMalformed() {
            return malformed;
        }

        boolean hasPartialPacket() {
            return !malformed && (inPayload || headerFilled > 0);
        }
    }
}
