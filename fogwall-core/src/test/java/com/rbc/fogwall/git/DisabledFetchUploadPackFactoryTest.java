package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.db.model.FetchActivity;
import com.rbc.fogwall.db.model.FetchRefusal;
import com.rbc.fogwall.servlet.FetchDecision;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.junit.jupiter.api.Test;

class DisabledFetchUploadPackFactoryTest {

    @Test
    void create_refusesWithServiceNotEnabledCarryingTheClientMessage() {
        var factory = new DisabledFetchUploadPackFactory();
        var ex = assertThrows(
                ServiceNotEnabledException.class,
                () -> factory.create(mock(HttpServletRequest.class), mock(Repository.class)));
        // JGit's SmartServiceInfoRefs / UploadPackServlet + SmartHttpErrorFilter show this message to the git client.
        assertEquals(DisabledFetchUploadPackFactory.MESSAGE, ex.getMessage());
    }

    @Test
    void create_leavesABlockedFetchDecision() {
        Map<String, Object> attributes = new HashMap<>();
        HttpServletRequest req = mock(HttpServletRequest.class);
        doAnswer(inv -> attributes.put(inv.getArgument(0), inv.getArgument(1)))
                .when(req)
                .setAttribute(anyString(), any());
        when(req.getAttribute(anyString())).thenAnswer(inv -> attributes.get(inv.<String>getArgument(0)));

        assertThrows(
                ServiceNotEnabledException.class,
                () -> new DisabledFetchUploadPackFactory().create(req, mock(Repository.class)));

        FetchDecision decision = FetchDecision.of(req).orElseThrow();
        assertEquals(FetchActivity.Result.BLOCKED, decision.result());
        assertEquals(FetchRefusal.FETCH_DISABLED, decision.refusal());
    }

    @Test
    void message_readsAsAGatewayRefusalNotAMissingRepository() {
        // The message must not read as a 404/missing repo — it explains the gateway refuses fetches.
        assertEquals("fetches are not served through this gateway", DisabledFetchUploadPackFactory.MESSAGE);
    }
}
