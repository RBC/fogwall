package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.config.ContentPatternConfig;
import com.rbc.fogwall.db.model.StepStatus;
import com.rbc.fogwall.git.Commit;
import com.rbc.fogwall.git.Contributor;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ContentPatternMessageFilterTest {

    private static class FakeResponse {
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        final AtomicBoolean committed = new AtomicBoolean(false);
        final HttpServletResponse mock;

        FakeResponse() throws IOException {
            mock = mock(HttpServletResponse.class);
            when(mock.getOutputStream()).thenReturn(new ServletOutputStream() {
                @Override
                public void write(int b) {
                    body.write(b);
                    committed.set(true);
                }

                @Override
                public void write(byte[] b, int off, int len) {
                    body.write(b, off, len);
                    committed.set(true);
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener l) {}
            });
            when(mock.isCommitted()).thenAnswer(inv -> committed.get());
        }
    }

    private static ServletInputStream emptyStream() {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
        return new ServletInputStream() {
            @Override
            public int read() {
                return bais.read();
            }

            @Override
            public boolean isFinished() {
                return bais.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener l) {}
        };
    }

    private HttpServletRequest mockRequest(GitRequestDetails details) throws IOException {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("POST");
        when(req.getContentType()).thenReturn("application/x-git-receive-pack-request");
        when(req.getRequestURI()).thenReturn("/proxy/github.com/owner/repo.git/git-receive-pack");
        when(req.getAttribute(GIT_REQUEST_ATTR)).thenReturn(details);
        when(req.getInputStream()).thenReturn(emptyStream());
        return req;
    }

    private GitRequestDetails detailsWithCommits(List<Commit> commits) {
        GitRequestDetails details = new GitRequestDetails();
        details.setOperation(HttpOperation.PUSH);
        details.getPushedCommits().addAll(commits);
        return details;
    }

    private Commit commitWithMessage(String message) {
        return Commit.builder()
                .sha("abc123def456")
                .author(Contributor.builder()
                        .name("Dev")
                        .email("dev@example.com")
                        .build())
                .committer(Contributor.builder()
                        .name("Dev")
                        .email("dev@example.com")
                        .build())
                .message(message)
                .build();
    }

    private ContentPatternConfig enabledConfig() {
        return ContentPatternConfig.builder()
                .enabled(true)
                .bundles(List.of("national-id-us"))
                .build();
    }

    @Test
    void disabled_skipsEntirely() throws Exception {
        GitRequestDetails details = detailsWithCommits(List.of(commitWithMessage("ssn: 212-96-7431 was affected")));
        var filter = new ContentPatternMessageFilter(ContentPatternConfig.defaultConfig());
        FakeResponse resp = new FakeResponse();

        filter.doHttpFilter(mockRequest(details), resp.mock);

        assertTrue(details.getSteps().isEmpty());
    }

    @Test
    void scanCommitMessagesDisabled_skipsEntirely_evenWhenEnabled() throws Exception {
        GitRequestDetails details = detailsWithCommits(List.of(commitWithMessage("ssn: 212-96-7431 was affected")));
        ContentPatternConfig config = ContentPatternConfig.builder()
                .enabled(true)
                .bundles(List.of("national-id-us"))
                .scanCommitMessages(false)
                .build();
        var filter = new ContentPatternMessageFilter(config);
        FakeResponse resp = new FakeResponse();

        filter.doHttpFilter(mockRequest(details), resp.mock);

        assertTrue(details.getSteps().isEmpty());
    }

    @Test
    void matchInMessage_recordsWarnStep_doesNotBlock() throws Exception {
        GitRequestDetails details =
                detailsWithCommits(List.of(commitWithMessage("Fixed bug for customer, ssn: 212-96-7431")));
        var filter = new ContentPatternMessageFilter(enabledConfig());
        FakeResponse resp = new FakeResponse();

        filter.doHttpFilter(mockRequest(details), resp.mock);

        assertEquals(GitRequestDetails.GitResult.PENDING, details.getResult(), "WARN must never reject a push");
        assertFalse(resp.committed.get());
        assertFalse(details.getSteps().isEmpty());
        var step = details.getSteps().get(0);
        assertEquals(StepStatus.WARN, step.getStatus());
        assertTrue(step.getContent().contains("Social Security Number"));
        assertFalse(step.getContent().contains("212-96-7431"), "raw matched value must never appear in step content");
    }

    @Test
    void noMatch_recordsPassStep() throws Exception {
        GitRequestDetails details = detailsWithCommits(List.of(commitWithMessage("Fix login bug")));
        var filter = new ContentPatternMessageFilter(enabledConfig());
        FakeResponse resp = new FakeResponse();

        filter.doHttpFilter(mockRequest(details), resp.mock);

        assertFalse(details.getSteps().isEmpty());
        assertEquals(StepStatus.PASS, details.getSteps().get(0).getStatus());
    }

    @Test
    void emptyCommits_doesNotThrow() throws Exception {
        GitRequestDetails details = detailsWithCommits(List.of());
        var filter = new ContentPatternMessageFilter(enabledConfig());
        FakeResponse resp = new FakeResponse();

        filter.doHttpFilter(mockRequest(details), resp.mock);

        assertTrue(details.getSteps().isEmpty());
    }
}
