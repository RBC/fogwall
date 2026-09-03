package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.provider.FogwallProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class ServerRepositoryResolverTest {

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
        var resolver = new ServerRepositoryResolver(cache, provider);

        assertThrows(RepositoryNotFoundException.class, () -> resolver.open(req, name));

        verifyNoInteractions(cache);
    }

    /**
     * The credential header is base64 of UTF-8 bytes. Decoding it with whatever charset the JVM happens to default to
     * mangles any password outside US-ASCII, so the upstream fetch is attempted with a different secret than the
     * developer typed — and the failure looks like a permissions problem rather than an encoding one.
     */
    @Test
    void aNonAsciiPasswordSurvivesTheDecode() throws Exception {
        String user = "dev";
        String password = "pässwörd-çå";
        String header =
                "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));

        LocalRepositoryCache cache = mock(LocalRepositoryCache.class);
        FogwallProvider provider = mock(FogwallProvider.class);
        when(provider.getUri()).thenReturn(URI.create("https://upstream.example"));
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn(header);

        new ServerRepositoryResolver(cache, provider).open(req, "owner/repo.git");

        ArgumentCaptor<String> principal = ArgumentCaptor.forClass(String.class);
        verify(cache).getOrClone(eq("https://upstream.example/owner/repo.git"), any(), isNull(), principal.capture());
        assertEquals(user + ":" + password, principal.getValue());
    }

    /**
     * Userinfo embedded in a remote URL reaches fogwall as a Basic header once git has been challenged; it never
     * appears in the request line, and the servlet API strips it from getRequestURL() regardless. Nothing may be
     * inferred from the URL, or an unauthenticated request would resolve a principal it never proved.
     */
    @Test
    void credentialsAreNeverReadOutOfTheRequestUrl() throws Exception {
        LocalRepositoryCache cache = mock(LocalRepositoryCache.class);
        FogwallProvider provider = mock(FogwallProvider.class);
        when(provider.getUri()).thenReturn(URI.create("https://upstream.example"));
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn(null);
        when(req.getRequestURL())
                .thenReturn(new StringBuffer("https://sneaky:token@fogwall.example/push/gh/owner/repo.git"));

        new ServerRepositoryResolver(cache, provider).open(req, "owner/repo.git");

        verify(cache).getOrClone(anyString(), isNull(), isNull(), isNull());
        verify(req, never()).setAttribute(eq(ServerRepositoryResolver.CREDENTIALS_ATTRIBUTE), any());
    }
}
