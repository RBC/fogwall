package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.db.model.StepStatus;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.LifecycleStage;
import com.rbc.fogwall.git.PushStepKind;
import com.rbc.fogwall.observability.FogwallTelemetry;
import com.rbc.fogwall.observability.InMemoryTelemetry;
import com.rbc.fogwall.observability.StageOutcome;
import com.rbc.fogwall.provider.GitHubProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** The transparent proxy times each filter as a pipeline stage when the request carries telemetry. */
class FilterStageTelemetryTest {

    private static final String STAGE = "TimedFilter";

    /** Records the given step status, or throws, when it runs. */
    private static class TimedFilter extends AbstractCustomFogwallFilter {
        private final StepStatus status;
        private final RuntimeException failure;

        TimedFilter(StepStatus status) {
            this(status, null);
        }

        TimedFilter(StepStatus status, RuntimeException failure) {
            super(LifecycleStage.CUSTOM_PRE);
            this.status = status;
            this.failure = failure;
        }

        @Override
        public void doHttpFilter(HttpServletRequest request, HttpServletResponse response) {
            if (failure != null) throw failure;
            if (status != StepStatus.PASS) recordStep(request, status, "reason", "content");
        }
    }

    private final InMemoryTelemetry otel = new InMemoryTelemetry();

    private HttpServletRequest pushRequest(boolean withTelemetry) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getContentType()).thenReturn("application/x-git-receive-pack-request");
        when(req.getRequestURI()).thenReturn("/proxy/github.com/owner/repo.git/git-receive-pack");
        when(req.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
        var details = new GitRequestDetails();
        details.setProvider(new GitHubProvider("/proxy"));
        when(req.getAttribute(GIT_REQUEST_ATTR)).thenReturn(details);
        if (withTelemetry) {
            when(req.getAttribute(FogwallTelemetry.REQUEST_ATTR)).thenReturn(otel.telemetry());
        }
        return req;
    }

    private long count(StageOutcome outcome) {
        return otel.stageCount(STAGE, FogwallTelemetry.MODE_PROXY, "github", outcome);
    }

    @Test
    void passingFilter_isAPassStage_endedBeforeTheChainContinues() throws Exception {
        AtomicInteger spansWhenChainRan = new AtomicInteger(-1);
        FilterChain chain = mock(FilterChain.class);
        doAnswer(inv -> {
                    spansWhenChainRan.set(otel.spans().size());
                    return null;
                })
                .when(chain)
                .doFilter(any(), any());

        new TimedFilter(StepStatus.PASS).doFilter(pushRequest(true), mock(HttpServletResponse.class), chain);

        assertEquals(1, count(StageOutcome.PASS));
        assertEquals(1, spansWhenChainRan.get(), "the stage covers this filter alone, not the chain after it");
    }

    @Test
    void blockedStep_isABlockStage() throws Exception {
        new TimedFilter(StepStatus.BLOCKED)
                .doFilter(pushRequest(true), mock(HttpServletResponse.class), mock(FilterChain.class));

        assertEquals(1, count(StageOutcome.BLOCK));
        assertEquals(0, count(StageOutcome.PASS));
    }

    @Test
    void failedStep_isAnErrorStage() throws Exception {
        // A failed step means the check could not run, which operators need to tell apart from a policy refusal.
        new TimedFilter(StepStatus.FAIL)
                .doFilter(pushRequest(true), mock(HttpServletResponse.class), mock(FilterChain.class));

        assertEquals(1, count(StageOutcome.ERROR));
        assertEquals(0, count(StageOutcome.BLOCK));
    }

    @Test
    void warningStep_isAPassStage() throws Exception {
        new TimedFilter(StepStatus.WARN)
                .doFilter(pushRequest(true), mock(HttpServletResponse.class), mock(FilterChain.class));

        assertEquals(1, count(StageOutcome.PASS));
    }

    @Test
    void throwingFilter_isAnErrorStage_andStillThrows() {
        var filter = new TimedFilter(StepStatus.PASS, new IllegalStateException("boom"));

        assertThrows(
                IllegalStateException.class,
                () -> filter.doFilter(pushRequest(true), mock(HttpServletResponse.class), mock(FilterChain.class)));
        assertEquals(1, count(StageOutcome.ERROR));
    }

    @Test
    void withoutTelemetryOnTheRequest_nothingIsTimed() throws Exception {
        new TimedFilter(StepStatus.PASS)
                .doFilter(pushRequest(false), mock(HttpServletResponse.class), mock(FilterChain.class));

        assertTrue(otel.spans().isEmpty());
        assertFalse(otel.hasPoints("fogwall.stage.outcomes"));
    }

    @Test
    void plumbingWithNoStepIdentity_isNotTimed() throws Exception {
        HttpServletRequest req = pushRequest(true);
        when(req.getHeader("User-Agent")).thenReturn("git/2.47.0");

        new ForceGitClientFilter().doFilter(req, mock(HttpServletResponse.class), mock(FilterChain.class));

        assertTrue(otel.spans().isEmpty(), "got " + otel.spanNames());
    }

    private HttpServletRequest pushWithBody(byte[] body) {
        HttpServletRequest req = pushRequest(true);
        when(req.getMethod()).thenReturn("POST");
        when(req.getPathInfo()).thenReturn("/owner/repo.git/git-receive-pack");
        when(req.getContentLengthLong()).thenReturn((long) body.length);
        try {
            when(req.getInputStream()).thenReturn(new ByteArrayServletInputStream(body));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return req;
    }

    @Test
    void parseRequest_timesItself() throws Exception {
        byte[] body;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("push-sample-01-body.bin")) {
            body = in.readAllBytes();
        }

        new ParseGitRequestFilter(new GitHubProvider("/proxy"), 0)
                .doFilter(pushWithBody(body), mock(HttpServletResponse.class), mock(FilterChain.class));

        assertEquals(
                1,
                otel.stageCount(
                        PushStepKind.PARSE_REQUEST.key(), FogwallTelemetry.MODE_PROXY, "github", StageOutcome.PASS));
    }

    @Test
    void parseRequest_overTheSizeLimit_isABlockStage() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getOutputStream()).thenReturn(mock(ServletOutputStream.class));

        new ParseGitRequestFilter(new GitHubProvider("/proxy"), 10)
                .doFilter(pushWithBody(new byte[100]), response, mock(FilterChain.class));

        assertEquals(
                1,
                otel.stageCount(
                        PushStepKind.PARSE_REQUEST.key(), FogwallTelemetry.MODE_PROXY, "github", StageOutcome.BLOCK));
    }

    /** A request body served from memory. */
    private static final class ByteArrayServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream in;

        ByteArrayServletInputStream(byte[] body) {
            this.in = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return in.read();
        }

        @Override
        public boolean isFinished() {
            return in.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {}
    }

    @Test
    void fetch_isNotTimed() throws Exception {
        HttpServletRequest req = pushRequest(true);
        when(req.getContentType()).thenReturn("application/x-git-upload-pack-request");
        when(req.getRequestURI()).thenReturn("/proxy/github.com/owner/repo.git/git-upload-pack");

        new TimedFilter(StepStatus.PASS).doFilter(req, mock(HttpServletResponse.class), mock(FilterChain.class));

        assertTrue(otel.spans().isEmpty());
    }
}
