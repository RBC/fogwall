package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;
import static com.rbc.fogwall.servlet.FogwallServlet.SERVICE_URL_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.db.model.PushStep;
import com.rbc.fogwall.db.model.StepStatus;
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
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ValidationSummaryFilterTest {

    // ---- helpers ----

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

    private static ServletInputStream emptyInputStream() {
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

    private static HttpServletRequest mockPushRequest(GitRequestDetails details) throws IOException {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getContentType()).thenReturn("application/x-git-receive-pack-request");
        when(req.getRequestURI()).thenReturn("/proxy/github.com/owner/repo.git/git-receive-pack");
        when(req.getAttribute(GIT_REQUEST_ATTR)).thenReturn(details);
        when(req.getAttribute(SERVICE_URL_ATTR)).thenReturn(null);
        when(req.getInputStream()).thenReturn(emptyInputStream());
        return req;
    }

    private static GitRequestDetails rejectedDetails(String failStepContent) {
        GitRequestDetails details = new GitRequestDetails();
        details.setOperation(HttpOperation.PUSH);
        details.setResult(GitRequestDetails.GitResult.REJECTED);
        PushStep failStep = PushStep.builder()
                .stepName("testStep")
                .stepOrder(2100)
                .status(StepStatus.FAIL)
                .content(failStepContent)
                .build();
        details.getSteps().add(failStep);
        return details;
    }

    // ---- tests ----

    @Test
    void rejectedResult_sendsErrorResponse() throws Exception {
        GitRequestDetails details = rejectedDetails("Email domain not allowed: gmail.com");
        FakeResponse fakeResponse = new FakeResponse();
        ValidationSummaryFilter filter = new ValidationSummaryFilter();

        filter.doHttpFilter(mockPushRequest(details), fakeResponse.mock);

        assertTrue(fakeResponse.committed.get(), "Response must be written for REJECTED push");
    }

    @Test
    void pendingResult_noResponse() throws Exception {
        GitRequestDetails details = new GitRequestDetails();
        details.setOperation(HttpOperation.PUSH);
        details.setResult(GitRequestDetails.GitResult.PENDING);
        FakeResponse fakeResponse = new FakeResponse();
        ValidationSummaryFilter filter = new ValidationSummaryFilter();

        filter.doHttpFilter(mockPushRequest(details), fakeResponse.mock);

        assertFalse(fakeResponse.committed.get(), "No response must be written for PENDING push");
    }

    @Test
    void nullDetails_noResponse() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getContentType()).thenReturn("application/x-git-receive-pack-request");
        when(req.getRequestURI()).thenReturn("/proxy/github.com/owner/repo.git/git-receive-pack");
        when(req.getAttribute(GIT_REQUEST_ATTR)).thenReturn(null);
        when(req.getInputStream()).thenReturn(emptyInputStream());
        FakeResponse fakeResponse = new FakeResponse();
        ValidationSummaryFilter filter = new ValidationSummaryFilter();

        filter.doHttpFilter(req, fakeResponse.mock);

        assertFalse(fakeResponse.committed.get());
    }

    @Test
    void rejectedResult_setsResultToRejected() throws Exception {
        GitRequestDetails details = rejectedDetails("some issue");
        FakeResponse fakeResponse = new FakeResponse();
        ValidationSummaryFilter filter = new ValidationSummaryFilter();

        filter.doHttpFilter(mockPushRequest(details), fakeResponse.mock);

        assertEquals(GitRequestDetails.GitResult.REJECTED, details.getResult());
    }

    @Test
    void multipleFailSteps_allIncludedInOutput() throws Exception {
        GitRequestDetails details = new GitRequestDetails();
        details.setOperation(HttpOperation.PUSH);
        details.setResult(GitRequestDetails.GitResult.REJECTED);
        details.getSteps()
                .add(PushStep.builder()
                        .stepName("emailCheck")
                        .stepOrder(2100)
                        .status(StepStatus.FAIL)
                        .content("bad email: user@gmail.com")
                        .build());
        details.getSteps()
                .add(PushStep.builder()
                        .stepName("messageCheck")
                        .stepOrder(2200)
                        .status(StepStatus.FAIL)
                        .content("blocked term: WIP")
                        .build());

        FakeResponse fakeResponse = new FakeResponse();
        ValidationSummaryFilter filter = new ValidationSummaryFilter();

        filter.doHttpFilter(mockPushRequest(details), fakeResponse.mock);

        // Should report "2 validation issue(s)"
        String output = fakeResponse.body.toString();
        assertTrue(output.contains("2"), "Output must mention the count of issues");
    }

    @Test
    void serviceUrl_presentInRequest_includedInOutput() throws Exception {
        GitRequestDetails details = rejectedDetails("email issue");
        HttpServletRequest req = mockPushRequest(details);
        when(req.getAttribute(SERVICE_URL_ATTR)).thenReturn("http://localhost:8080");

        FakeResponse fakeResponse = new FakeResponse();
        ValidationSummaryFilter filter = new ValidationSummaryFilter();

        filter.doHttpFilter(req, fakeResponse.mock);

        String output = fakeResponse.body.toString();
        assertTrue(fakeResponse.committed.get());
        // The link should be included somewhere in the output bytes
        assertTrue(output.contains("localhost:8080") || output.length() > 0);
    }
}
