package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletRequest;
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
        // JGit surfaces this message to the git client as `fatal: remote error: <message>` via
        // SmartServiceInfoRefs / UploadPackServlet + SmartHttpErrorFilter.
        assertEquals(DisabledFetchUploadPackFactory.MESSAGE, ex.getMessage());
    }

    @Test
    void message_readsAsAGatewayRefusalNotAMissingRepository() {
        // The message must not read as a 404/missing repo — it explains the gateway refuses fetches.
        assertEquals("fetches are not served through this gateway", DisabledFetchUploadPackFactory.MESSAGE);
    }
}
