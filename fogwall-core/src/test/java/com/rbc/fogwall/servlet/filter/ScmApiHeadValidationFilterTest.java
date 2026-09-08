package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.ScmApiRequestContext.SCM_API_REQUEST_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.db.model.ScmApiActionStatus;
import com.rbc.fogwall.scmapi.HeadCommitValidator;
import com.rbc.fogwall.scmapi.JsonBodyField;
import com.rbc.fogwall.servlet.ScmApiRequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScmApiHeadValidationFilterTest {

    private static final String CREATE_OP = "pulls.create";

    private final HeadCommitValidator headCommitValidator = mock(HeadCommitValidator.class);

    private ScmApiHeadValidationFilter filter(boolean requireValidatedHead, Optional<String> resolvedSha) {
        return new ScmApiHeadValidationFilter(
                CREATE_OP,
                body -> JsonBodyField.stringField(body, "head"),
                (req, ctx, headRef) -> resolvedSha,
                headCommitValidator,
                requireValidatedHead);
    }

    @Test
    void settingOff_passesThroughWithoutReadingBody() throws Exception {
        ScmApiRequestContext context = new ScmApiRequestContext();
        context.setMutationField(CREATE_OP);
        HttpServletRequest request = mockRequest("{\"head\":\"feature\"}", context);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter(false, Optional.of("sha")).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(headCommitValidator);
    }

    @Test
    void notTheCreateOperation_passesThrough() throws Exception {
        ScmApiRequestContext context = new ScmApiRequestContext();
        context.setMutationField("pulls.update");
        HttpServletRequest request = mockRequest("{\"head\":\"feature\"}", context);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter(true, Optional.of("sha")).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void validatedHead_passesThrough() throws Exception {
        ScmApiRequestContext context = new ScmApiRequestContext();
        context.setMutationField(CREATE_OP);
        HttpServletRequest request = mockRequest("{\"head\":\"feature\"}", context);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(headCommitValidator.isValidated("resolved-sha")).thenReturn(true);

        filter(true, Optional.of("resolved-sha")).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void unvalidatedHead_isDeniedWithRemedy() throws Exception {
        ScmApiRequestContext context = new ScmApiRequestContext();
        context.setMutationField(CREATE_OP);
        HttpServletRequest request = mockRequest("{\"head\":\"feature\"}", context);
        HttpServletResponse response = mockResponse();
        FilterChain chain = mock(FilterChain.class);
        when(headCommitValidator.isValidated("resolved-sha")).thenReturn(false);

        filter(true, Optional.of("resolved-sha")).doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertEquals(ScmApiActionStatus.DENIED, context.getStatus());
        assertTrue(context.getReason().contains("push it through fogwall"));
    }

    @Test
    void unresolvableHeadRef_isRecordedAsError() throws Exception {
        ScmApiRequestContext context = new ScmApiRequestContext();
        context.setMutationField(CREATE_OP);
        HttpServletRequest request = mockRequest("{\"head\":\"feature\"}", context);
        HttpServletResponse response = mockResponse();
        FilterChain chain = mock(FilterChain.class);

        filter(true, Optional.empty()).doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertEquals(ScmApiActionStatus.ERROR, context.getStatus());
        verifyNoInteractions(headCommitValidator);
    }

    @Test
    void missingHeadField_isRecordedAsError() throws Exception {
        ScmApiRequestContext context = new ScmApiRequestContext();
        context.setMutationField(CREATE_OP);
        HttpServletRequest request = mockRequest("{}", context);
        HttpServletResponse response = mockResponse();
        FilterChain chain = mock(FilterChain.class);

        filter(true, Optional.of("should-not-be-used")).doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertEquals(ScmApiActionStatus.ERROR, context.getStatus());
    }

    private static HttpServletRequest mockRequest(String body, ScmApiRequestContext context) throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        when(req.getInputStream()).thenReturn(streamOf(bytes));
        when(req.getAttribute(SCM_API_REQUEST_ATTR)).thenReturn(context);
        return req;
    }

    private static HttpServletResponse mockResponse() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override
            public void write(int b) {
                out.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {}
        });
        return response;
    }

    private static ServletInputStream streamOf(byte[] bytes) {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
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
            public void setReadListener(ReadListener readListener) {}
        };
    }
}
