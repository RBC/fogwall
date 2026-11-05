package com.rbc.fogwall.dashboard.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.db.FetchStore;
import com.rbc.fogwall.db.model.FetchActivity;
import com.rbc.fogwall.db.model.FetchActivityQuery;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

class FetchControllerTest {

    private final FetchStore fetchStore = mock(FetchStore.class);
    private final FetchController controller = new FetchController(fetchStore);

    /** Calls the listing with only the named parameters set, as {@code name, value} pairs; the rest default. */
    private ResponseEntity<List<FetchActivity>> list(String... nameValues) {
        Map<String, String> p = new HashMap<>();
        for (int i = 0; i < nameValues.length; i += 2) {
            p.put(nameValues[i], nameValues[i + 1]);
        }
        return controller.list(
                p.get("result"),
                p.get("transport"),
                p.get("provider"),
                p.get("owner"),
                p.get("repo"),
                p.get("repoName"),
                p.get("user"),
                p.get("search"),
                p.get("from"),
                p.get("to"),
                p.get("after"),
                Integer.parseInt(p.getOrDefault("limit", "50")),
                Integer.parseInt(p.getOrDefault("offset", "0")),
                Boolean.parseBoolean(p.getOrDefault("newestFirst", "true")));
    }

    private FetchActivityQuery captured() {
        var query = ArgumentCaptor.forClass(FetchActivityQuery.class);
        verify(fetchStore).find(query.capture());
        return query.getValue();
    }

    private static void assertBadRequest(Runnable call) {
        var ex = assertThrows(ResponseStatusException.class, call::run);
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void list_passesEveryFilterToTheStore() {
        when(fetchStore.find(any())).thenReturn(List.of());

        list(
                "result", "blocked",
                "transport", "ssh",
                "provider", "github",
                "owner", "acme",
                "repo", "widgets",
                "search", "wid",
                "from", "2026-11-01T00:00:00Z",
                "to", "2026-11-02T00:00:00Z",
                "limit", "10",
                "offset", "20",
                "newestFirst", "false");

        FetchActivityQuery q = captured();
        assertEquals(FetchActivity.Result.BLOCKED, q.getResult());
        assertEquals(FetchActivity.Transport.SSH, q.getTransport());
        assertEquals("github", q.getProvider());
        assertEquals("acme", q.getOwner());
        assertEquals("widgets", q.getRepoName());
        assertEquals("wid", q.getSearch());
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), q.getFrom());
        assertEquals(Instant.parse("2026-11-02T00:00:00Z"), q.getTo());
        assertEquals(10, q.getLimit());
        assertEquals(20, q.getOffset());
        assertFalse(q.isNewestFirst());
    }

    @Test
    void list_acceptsTheOlderRepoNameParameter() {
        when(fetchStore.find(any())).thenReturn(List.of());

        list("repoName", "widgets");

        assertEquals("widgets", captured().getRepoName());
    }

    @Test
    void list_anUnknownResultOrTransport_isA400() {
        assertBadRequest(() -> list("result", "maybe"));
        assertBadRequest(() -> list("transport", "ftp"));
        verifyNoInteractions(fetchStore);
    }

    @Test
    void list_byUser_isA400_sinceAFetchCarriesNoActor() {
        assertBadRequest(() -> list("user", "alice"));
        verifyNoInteractions(fetchStore);
    }
}
