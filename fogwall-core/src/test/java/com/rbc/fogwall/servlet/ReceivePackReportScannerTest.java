package com.rbc.fogwall.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.transport.SideBandOutputStream;
import org.junit.jupiter.api.Test;

class ReceivePackReportScannerTest {

    static byte[] plainReport(String... lines) throws IOException {
        var out = new ByteArrayOutputStream();
        var pkt = new PacketLineOut(out);
        for (String line : lines) {
            pkt.writeString(line + "\n");
        }
        pkt.end();
        return out.toByteArray();
    }

    /**
     * The report as a sideband client receives it: progress on channel 2, then the report-status pkt-lines on channel
     * 1, split across sideband packets small enough to cut a report line in two.
     */
    static byte[] sidebandReport(String... lines) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var progress = new SideBandOutputStream(SideBandOutputStream.CH_PROGRESS, 64, out)) {
            progress.write("Resolving deltas: 100% (1/1)\n".getBytes(StandardCharsets.UTF_8));
            progress.flush();
        }
        byte[] report = plainReport(lines);
        try (var data = new SideBandOutputStream(SideBandOutputStream.CH_DATA, 16, out)) {
            data.write(report);
            data.flush();
        }
        new PacketLineOut(out).end();
        return out.toByteArray();
    }

    private static Optional<String> scan(byte[] response) {
        var scanner = new ReceivePackReportScanner();
        scanner.accept(response, 0, response.length);
        return scanner.failure();
    }

    private static Optional<String> scanByteByByte(byte[] response) {
        var scanner = new ReceivePackReportScanner();
        for (int i = 0; i < response.length; i++) {
            scanner.accept(response, i, 1);
        }
        return scanner.failure();
    }

    @Test
    void plainReport_allOk_isAccepted() throws IOException {
        assertEquals(Optional.empty(), scan(plainReport("unpack ok", "ok refs/heads/main")));
    }

    @Test
    void plainReport_ngLine_isRefusedWithReason() throws IOException {
        assertEquals(
                Optional.of("Upstream rejected refs/heads/main: protected branch hook declined"),
                scan(plainReport("unpack ok", "ng refs/heads/main protected branch hook declined")));
    }

    @Test
    void unpackError_isRefused() throws IOException {
        Optional<String> failure =
                scan(plainReport("unpack index-pack abnormal exit", "ng refs/heads/main unpacker error"));
        assertEquals(
                Optional.of("Upstream unpack failed: index-pack abnormal exit; "
                        + "Upstream rejected refs/heads/main: unpacker error"),
                failure);
    }

    @Test
    void reportStatusV2OptionLines_areAccepted() throws IOException {
        assertEquals(
                Optional.empty(),
                scan(plainReport("unpack ok", "ok refs/heads/main", "option refname refs/heads/main")));
    }

    @Test
    void sidebandReport_allOk_isAccepted() throws IOException {
        assertEquals(Optional.empty(), scan(sidebandReport("unpack ok", "ok refs/heads/main")));
    }

    @Test
    void sidebandReport_ngLine_isRefusedWithReason() throws IOException {
        assertEquals(
                Optional.of("Upstream rejected refs/heads/main: non-fast-forward"),
                scan(sidebandReport("unpack ok", "ng refs/heads/main non-fast-forward")));
    }

    @Test
    void sidebandReport_deliveredOneByteAtATime_isRead() throws IOException {
        assertEquals(
                Optional.of("Upstream rejected refs/heads/main: non-fast-forward"),
                scanByteByByte(sidebandReport("unpack ok", "ng refs/heads/main non-fast-forward")));
        assertEquals(Optional.empty(), scanByteByByte(sidebandReport("unpack ok", "ok refs/heads/main")));
    }

    @Test
    void sidebandErrorChannel_isRefused() throws IOException {
        var out = new ByteArrayOutputStream();
        try (var error = new SideBandOutputStream(SideBandOutputStream.CH_ERROR, 64, out)) {
            error.write("pre-receive hook declined\n".getBytes(StandardCharsets.UTF_8));
            error.flush();
        }
        assertEquals(Optional.of("Upstream error: pre-receive hook declined"), scan(out.toByteArray()));
    }

    @Test
    void errPacket_isRefused() throws IOException {
        assertEquals(Optional.of("Upstream error: access denied"), scan(plainReport("ERR access denied")));
    }

    @Test
    void emptyResponse_isRefused() {
        assertEquals(Optional.of("Upstream sent no report-status"), scan(new byte[0]));
    }

    @Test
    void nonPktLineBody_isRefused() {
        byte[] html = "<html>oops</html>".getBytes(StandardCharsets.UTF_8);
        assertEquals(Optional.of("Upstream response is not a readable report-status"), scan(html));
    }

    @Test
    void truncatedReport_isRefused() throws IOException {
        byte[] report = plainReport("unpack ok", "ok refs/heads/main");
        var scanner = new ReceivePackReportScanner();
        scanner.accept(report, 0, report.length - 10);
        assertTrue(scanner.failure().isPresent(), "a report cut short is not an acceptance");
    }
}
