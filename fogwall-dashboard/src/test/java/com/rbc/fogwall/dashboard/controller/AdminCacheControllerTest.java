package com.rbc.fogwall.dashboard.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rbc.fogwall.git.LocalRepositoryCache;
import com.rbc.fogwall.git.LocalRepositoryCache.CacheEntrySummary;
import com.rbc.fogwall.git.LocalRepositoryCache.RefInfo;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AdminCacheControllerTest {

    @Mock
    LocalRepositoryCache serverCache;

    @Mock
    LocalRepositoryCache proxyCache;

    AdminCacheController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminCacheController(serverCache, proxyCache);
    }

    private static CacheEntrySummary entry(String key, String url) {
        return new CacheEntrySummary(key, url, 1_000L, 2_000L, 4_096L, 3, false, false);
    }

    @Test
    void list_groupsEachModesEntries() {
        when(serverCache.listEntries()).thenReturn(List.of(entry("k1", "https://s/a.git")));
        when(proxyCache.listEntries()).thenReturn(List.of(entry("k2", "https://p/b.git")));

        Map<String, List<CacheEntrySummary>> result = controller.list();

        assertEquals(1, result.get("server").size());
        assertEquals("k1", result.get("server").get(0).cacheKey());
        assertEquals(1, result.get("proxy").size());
        assertEquals("k2", result.get("proxy").get(0).cacheKey());
    }

    @Test
    void refs_serverMode_delegatesToServerCache() {
        when(serverCache.listRefs("k1")).thenReturn(List.of(new RefInfo("refs/heads/main", "abc", "branch")));

        var resp = controller.refs("server", "k1");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1, resp.getBody().size());
        assertEquals("branch", resp.getBody().get(0).type());
        verifyNoInteractions(proxyCache);
    }

    @Test
    void refs_unknownMode_returns400() {
        var resp = controller.refs("bogus", "k1");

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        verifyNoInteractions(serverCache, proxyCache);
    }

    @Test
    void invalidate_proxyMode_delegatesAndReportsRemoved() throws Exception {
        when(proxyCache.removeByKey("k2")).thenReturn(true);

        var resp = controller.invalidate("proxy", "k2");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(Boolean.TRUE, resp.getBody().get("removed"));
        verify(proxyCache).removeByKey("k2");
        verifyNoInteractions(serverCache);
    }

    @Test
    void invalidate_missingEntry_reportsRemovedFalse() throws Exception {
        when(serverCache.removeByKey("gone")).thenReturn(false);

        var resp = controller.invalidate("server", "gone");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(Boolean.FALSE, resp.getBody().get("removed"));
    }

    @Test
    void invalidate_unknownMode_returns400() throws Exception {
        var resp = controller.invalidate("bogus", "k1");

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        verifyNoInteractions(serverCache, proxyCache);
    }

    @Test
    void invalidateAll_serverMode_delegatesAndReportsCount() throws Exception {
        when(serverCache.invalidateAll()).thenReturn(4);

        var resp = controller.invalidateAll("server");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(4, resp.getBody().get("invalidated"));
        verify(serverCache).invalidateAll();
        verifyNoInteractions(proxyCache);
    }

    @Test
    void invalidateAll_unknownMode_returns400() throws Exception {
        var resp = controller.invalidateAll("bogus");

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertTrue(resp.getBody() == null);
        verifyNoInteractions(serverCache, proxyCache);
    }
}
