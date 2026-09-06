package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.ScmApiRequestContext.SCM_API_REQUEST_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.db.model.ScmApiActionStatus;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.provider.GitHubProvider;
import com.rbc.fogwall.scmapi.MutationNodeIdRef;
import com.rbc.fogwall.scmapi.NodeIdResolver;
import com.rbc.fogwall.scmapi.OwnerRepo;
import com.rbc.fogwall.scmapi.ScmApiAccessEvaluator;
import com.rbc.fogwall.scmapi.ScmApiAccessRule;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScmApiGitHubGateFilterTest {

    private final GitHubProvider provider = new GitHubProvider("/scm-api/github.com");
    private ScmApiAccessEvaluator accessEvaluator;
    private NodeIdResolver nodeIdResolver;
    private RepoPermissionService repoPermissionService;

    @BeforeEach
    void setUp() {
        accessEvaluator = mock(ScmApiAccessEvaluator.class);
        nodeIdResolver = mock(NodeIdResolver.class);
        repoPermissionService = mock(RepoPermissionService.class);
    }

    private ScmApiGitHubGateFilter filter() {
        return new ScmApiGitHubGateFilter(provider, accessEvaluator, nodeIdResolver, repoPermissionService);
    }

    private static HttpServletRequest mockRequest(String body, ScmApiRequestContext context) throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        when(req.getInputStream()).thenReturn(streamOf(bytes));
        when(req.getContentLength()).thenReturn(bytes.length);
        when(req.getAttribute(SCM_API_REQUEST_ATTR)).thenReturn(context);
        when(req.getHeader("Authorization")).thenReturn("Bearer caller-token");
        return req;
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
            public void setReadListener(ReadListener l) {}
        };
    }

    private static HttpServletResponse mockResponse(ByteArrayOutputStream body) throws Exception {
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override
            public void write(int b) {
                body.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener l) {}
        });
        return resp;
    }

    @Test
    void malformedBody_returns400_doesNotCallChain() throws Exception {
        var context = new ScmApiRequestContext();
        HttpServletRequest req = mockRequest("not json", context);
        HttpServletResponse resp = mockResponse(new ByteArrayOutputStream());
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(req, resp, chain);

        verify(resp).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(chain);
    }

    @Test
    void readDeniedByAccessRule_returns403() throws Exception {
        when(accessEvaluator.evaluate("github", ScmApiAccessRule.Operation.READ))
                .thenReturn(new ScmApiAccessEvaluator.Result.NotAllowed());
        var context = new ScmApiRequestContext();
        HttpServletRequest req = mockRequest("{\"query\":\"query { viewer { login } }\"}", context);
        HttpServletResponse resp = mockResponse(new ByteArrayOutputStream());
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(req, resp, chain);

        verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verifyNoInteractions(chain);
    }

    @Test
    void readAllowedByAccessRule_continuesChain() throws Exception {
        when(accessEvaluator.evaluate("github", ScmApiAccessRule.Operation.READ))
                .thenReturn(new ScmApiAccessEvaluator.Result.Allowed("rule-1"));
        var context = new ScmApiRequestContext();
        HttpServletRequest req = mockRequest("{\"query\":\"query { viewer { login } }\"}", context);
        HttpServletResponse resp = mockResponse(new ByteArrayOutputStream());
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(req, resp, chain);

        verify(chain).doFilter(any(), eq(resp));
        assertNull(context.getMutationField());
    }

    @Test
    void unallowlistedMutation_returns403_deniesWithReason() throws Exception {
        var context = new ScmApiRequestContext();
        String body =
                "{\"query\":\"mutation { deleteRepository(input: {repositoryId: \\\"R_1\\\"}) { clientMutationId } }\"}";
        HttpServletRequest req = mockRequest(body, context);
        HttpServletResponse resp = mockResponse(new ByteArrayOutputStream());
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(req, resp, chain);

        verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verifyNoInteractions(chain);
        assertEquals(ScmApiActionStatus.DENIED, context.getStatus());
        assertEquals("deleteRepository", context.getMutationField());
    }

    @Test
    void allowlistedMutation_deniedByAccessRule_returns403() throws Exception {
        when(accessEvaluator.evaluate("github", ScmApiAccessRule.Operation.MUTATE))
                .thenReturn(new ScmApiAccessEvaluator.Result.NotAllowed());
        var context = new ScmApiRequestContext();
        String body = "{\"query\":\"mutation { createIssue(input: {repositoryId: \\\"R_1\\\"}) { issue { id } } }\"}";
        HttpServletRequest req = mockRequest(body, context);
        HttpServletResponse resp = mockResponse(new ByteArrayOutputStream());
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(req, resp, chain);

        verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verifyNoInteractions(chain);
    }

    @Test
    void mutationMissingNodeIdVariable_returns400() throws Exception {
        when(accessEvaluator.evaluate("github", ScmApiAccessRule.Operation.MUTATE))
                .thenReturn(new ScmApiAccessEvaluator.Result.Allowed("rule-1"));
        var context = new ScmApiRequestContext();
        String body =
                "{\"query\":\"mutation { createIssue(input: {repositoryId: \\\"R_1\\\"}) { issue { id } } }\",\"variables\":{}}";
        HttpServletRequest req = mockRequest(body, context);
        HttpServletResponse resp = mockResponse(new ByteArrayOutputStream());
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(req, resp, chain);

        verify(resp).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(chain);
    }

    @Test
    void nodeIdResolutionFails_returns403() throws Exception {
        when(accessEvaluator.evaluate("github", ScmApiAccessRule.Operation.MUTATE))
                .thenReturn(new ScmApiAccessEvaluator.Result.Allowed("rule-1"));
        when(nodeIdResolver.resolve(eq(provider), any(MutationNodeIdRef.class), eq("caller-token")))
                .thenReturn(Optional.empty());
        var context = new ScmApiRequestContext();
        String body =
                "{\"query\":\"mutation { createIssue(input: {repositoryId: \\\"R_1\\\"}) { issue { id } } }\",\"variables\":{\"input\":{\"repositoryId\":\"R_1\"}}}";
        HttpServletRequest req = mockRequest(body, context);
        HttpServletResponse resp = mockResponse(new ByteArrayOutputStream());
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(req, resp, chain);

        verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verifyNoInteractions(chain);
    }

    @Test
    void resolvedButNotPermitted_returns403() throws Exception {
        when(accessEvaluator.evaluate("github", ScmApiAccessRule.Operation.MUTATE))
                .thenReturn(new ScmApiAccessEvaluator.Result.Allowed("rule-1"));
        when(nodeIdResolver.resolve(eq(provider), any(MutationNodeIdRef.class), eq("caller-token")))
                .thenReturn(Optional.of(new OwnerRepo("acme", "widgets")));
        when(repoPermissionService.isAllowedToPropose("alice", "github", "/acme/widgets"))
                .thenReturn(false);
        var context = new ScmApiRequestContext();
        context.setResolvedUser("alice");
        String body =
                "{\"query\":\"mutation { createIssue(input: {repositoryId: \\\"R_1\\\"}) { issue { id } } }\",\"variables\":{\"input\":{\"repositoryId\":\"R_1\"}}}";
        HttpServletRequest req = mockRequest(body, context);
        HttpServletResponse resp = mockResponse(new ByteArrayOutputStream());
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(req, resp, chain);

        verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verifyNoInteractions(chain);
    }

    @Test
    void allChecksPass_continuesChain_populatesContext() throws Exception {
        when(accessEvaluator.evaluate("github", ScmApiAccessRule.Operation.MUTATE))
                .thenReturn(new ScmApiAccessEvaluator.Result.Allowed("rule-1"));
        when(nodeIdResolver.resolve(eq(provider), any(MutationNodeIdRef.class), eq("caller-token")))
                .thenReturn(Optional.of(new OwnerRepo("acme", "widgets")));
        when(repoPermissionService.isAllowedToPropose("alice", "github", "/acme/widgets"))
                .thenReturn(true);
        var context = new ScmApiRequestContext();
        context.setResolvedUser("alice");
        String body =
                "{\"query\":\"mutation { createIssue(input: {repositoryId: \\\"R_1\\\"}) { issue { id } } }\",\"variables\":{\"input\":{\"repositoryId\":\"R_1\"}}}";
        HttpServletRequest req = mockRequest(body, context);
        HttpServletResponse resp = mockResponse(new ByteArrayOutputStream());
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(req, resp, chain);

        verify(chain).doFilter(any(), eq(resp));
        assertEquals("createIssue", context.getMutationField());
        assertEquals("R_1", context.getNodeId());
        assertEquals("acme", context.getRepoOwner());
        assertEquals("widgets", context.getRepoName());
    }
}
