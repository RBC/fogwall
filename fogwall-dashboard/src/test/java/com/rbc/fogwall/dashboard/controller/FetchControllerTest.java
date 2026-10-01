package com.rbc.fogwall.dashboard.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.db.FetchStore;
import com.rbc.fogwall.db.model.FetchActivity;
import com.rbc.fogwall.db.model.FetchActivityQuery;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FetchControllerTest {

    private final FetchStore fetchStore = mock(FetchStore.class);
    private final FetchController controller = new FetchController(fetchStore);

    @Test
    void list_passesEveryFilterToTheStore() {
        when(fetchStore.find(any())).thenReturn(List.of());

        controller.list("blocked", "ssh", "github", "acme", "widgets", "wid", 10, 20, false);

        var query = ArgumentCaptor.forClass(FetchActivityQuery.class);
        verify(fetchStore).find(query.capture());
        assertEquals(FetchActivity.Result.BLOCKED, query.getValue().getResult());
        assertEquals(FetchActivity.Transport.SSH, query.getValue().getTransport());
        assertEquals("github", query.getValue().getProvider());
        assertEquals("acme", query.getValue().getOwner());
        assertEquals("widgets", query.getValue().getRepoName());
        assertEquals("wid", query.getValue().getSearch());
        assertEquals(10, query.getValue().getLimit());
        assertEquals(20, query.getValue().getOffset());
        assertFalse(query.getValue().isNewestFirst());
    }

    @Test
    void list_withAnUnknownResultOrTransport_answersNoRows() {
        assertTrue(controller
                .list("maybe", null, null, null, null, null, 50, 0, true)
                .isEmpty());
        assertTrue(controller
                .list(null, "ftp", null, null, null, null, 50, 0, true)
                .isEmpty());
        verifyNoInteractions(fetchStore);
    }
}
