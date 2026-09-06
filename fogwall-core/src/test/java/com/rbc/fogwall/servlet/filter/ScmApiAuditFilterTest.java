package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.ScmApiRequestContext.SCM_API_REQUEST_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.db.ScmApiActionStore;
import com.rbc.fogwall.db.model.ScmApiActionRecord;
import com.rbc.fogwall.db.model.ScmApiActionStatus;
import com.rbc.fogwall.servlet.ScmApiRequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ScmApiAuditFilterTest {

    @Test
    void mutationContext_writesRecordAfterChain() throws Exception {
        ScmApiActionStore store = mock(ScmApiActionStore.class);
        ScmApiRequestContext context = new ScmApiRequestContext();
        context.setProvider("github");
        context.setResolvedUser("alice");
        context.setMutationField("createIssue");
        context.setNodeId("R_1");
        context.setRepoOwner("acme");
        context.setRepoName("widgets");
        context.setStatus(ScmApiActionStatus.FORWARDED);

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute(SCM_API_REQUEST_ATTR)).thenReturn(context);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        new ScmApiAuditFilter(store).doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        ArgumentCaptor<ScmApiActionRecord> captor = ArgumentCaptor.forClass(ScmApiActionRecord.class);
        verify(store).save(captor.capture());
        assertEquals("github", captor.getValue().getProvider());
        assertEquals("createIssue", captor.getValue().getMutationField());
        assertEquals(ScmApiActionStatus.FORWARDED, captor.getValue().getStatus());
    }

    @Test
    void readContext_noMutationField_doesNotWriteRecord() throws Exception {
        ScmApiActionStore store = mock(ScmApiActionStore.class);
        ScmApiRequestContext context = new ScmApiRequestContext();
        context.setProvider("github");
        context.setResolvedUser("alice");
        // mutationField left null: a read

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute(SCM_API_REQUEST_ATTR)).thenReturn(context);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        new ScmApiAuditFilter(store).doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verifyNoInteractions(store);
    }

    @Test
    void noContextAttribute_doesNotWriteRecord() throws Exception {
        ScmApiActionStore store = mock(ScmApiActionStore.class);
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute(SCM_API_REQUEST_ATTR)).thenReturn(null);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        new ScmApiAuditFilter(store).doFilter(req, resp, chain);

        verifyNoInteractions(store);
    }

    @Test
    void chainThrows_stillWritesRecord_thenRethrows() throws Exception {
        ScmApiActionStore store = mock(ScmApiActionStore.class);
        ScmApiRequestContext context = new ScmApiRequestContext();
        context.setMutationField("createIssue");
        context.setStatus(ScmApiActionStatus.ERROR);
        context.setReason("boom");

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute(SCM_API_REQUEST_ATTR)).thenReturn(context);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        doThrow(new RuntimeException("downstream failure"))
                .when(chain)
                .doFilter(any(ServletRequest.class), any(ServletResponse.class));

        assertThrows(RuntimeException.class, () -> new ScmApiAuditFilter(store).doFilter(req, resp, chain));

        verify(store).save(any(ScmApiActionRecord.class));
    }

    @Test
    void storeThrows_doesNotPropagate() throws Exception {
        ScmApiActionStore store = mock(ScmApiActionStore.class);
        doThrow(new RuntimeException("db down")).when(store).save(any());
        ScmApiRequestContext context = new ScmApiRequestContext();
        context.setMutationField("createIssue");
        context.setStatus(ScmApiActionStatus.FORWARDED);

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute(SCM_API_REQUEST_ATTR)).thenReturn(context);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        assertDoesNotThrow(() -> new ScmApiAuditFilter(store).doFilter(req, resp, chain));
    }
}
