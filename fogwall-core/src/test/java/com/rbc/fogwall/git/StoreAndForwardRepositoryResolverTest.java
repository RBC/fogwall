package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.provider.FogwallProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StoreAndForwardRepositoryResolverTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "owner/../other-org/repo.git",
                "../repo.git",
                "owner/..",
                "owner//repo.git",
                "owner/repo.git/extra/..",
                "...git"
            })
    void open_invalidRepositoryPath_isRejectedBeforeAnyCloneAttempt(String name) {
        LocalRepositoryCache cache = mock(LocalRepositoryCache.class);
        FogwallProvider provider = mock(FogwallProvider.class);
        HttpServletRequest req = mock(HttpServletRequest.class);
        var resolver = new StoreAndForwardRepositoryResolver(cache, provider);

        assertThrows(RepositoryNotFoundException.class, () -> resolver.open(req, name));

        verifyNoInteractions(cache);
    }
}
