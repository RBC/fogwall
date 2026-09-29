package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.service.ScmOAuthTokenService;
import com.rbc.fogwall.servlet.filter.FogwallCredentialFilter;
import com.rbc.fogwall.user.UserEntry;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Base64;
import java.util.List;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.URIish;
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

    /**
     * A request FogwallCredentialFilter authenticated carries the user's linked OAuth token as its credentials. The
     * fogwall credential in the Basic header is what the client authenticated to fogwall with; it never reaches the
     * upstream.
     */
    @Test
    void aCredentialAuthenticatedRequest_syncsWithTheLinkedTokenNotTheHeader() throws Exception {
        String header = "Basic "
                + Base64.getEncoder().encodeToString("me:fgw_abcdefghijklmnop_secret".getBytes(StandardCharsets.UTF_8));
        UserEntry alice = UserEntry.builder()
                .username("alice")
                .emails(List.of())
                .scmIdentities(List.of())
                .build();
        var linked = new ScmOAuthCredentialsProvider(mock(ScmOAuthTokenService.class), "alice", "github");

        LocalRepositoryCache cache = mock(LocalRepositoryCache.class);
        FogwallProvider provider = mock(FogwallProvider.class);
        when(provider.getUri()).thenReturn(URI.create("https://upstream.example"));
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn(header);
        when(req.getAttribute(FogwallCredentialFilter.AUTHENTICATION_ATTRIBUTE))
                .thenReturn(new CredentialAuthentication(alice, "abcdefghijklmnop", "laptop"));
        when(req.getAttribute(ServerRepositoryResolver.CREDENTIALS_ATTRIBUTE)).thenReturn(linked);

        new ServerRepositoryResolver(cache, provider).open(req, "owner/repo.git");

        verify(cache).getOrClone("https://upstream.example/owner/repo.git", linked, null, "fogwall-user:alice");
        verify(req, never()).setAttribute(eq(ServerRepositoryResolver.CREDENTIALS_ATTRIBUTE), any());
        verify(req, never()).setAttribute(eq("com.rbc.fogwall.pushUser"), any());
        verify(req)
                .setAttribute(
                        ServerRepositoryResolver.UPSTREAM_URL_ATTRIBUTE, "https://upstream.example/owner/repo.git");
    }

    private static ServiceMayNotContinueException openFailure(Exception upstreamFailure, int deniedStatus)
            throws Exception {
        LocalRepositoryCache cache = mock(LocalRepositoryCache.class);
        when(cache.getOrClone(anyString(), any(), any(), any())).thenThrow(upstreamFailure);
        FogwallProvider provider = mock(FogwallProvider.class);
        when(provider.getUri()).thenReturn(URI.create("https://upstream.example"));
        when(provider.getBlockedInfoRefsStatus()).thenReturn(deniedStatus);
        HttpServletRequest req = mock(HttpServletRequest.class);

        return assertThrows(
                ServiceMayNotContinueException.class,
                () -> new ServerRepositoryResolver(cache, provider).open(req, "owner/repo.git"));
    }

    private static URIish upstream() throws Exception {
        return new URIish("https://upstream.example/owner/repo.git");
    }

    /** A bad token used to read as a missing repository. It asks git for another credential instead. */
    @Test
    void upstreamRejectsTheCredential_is401() throws Exception {
        var e = openFailure(new TransportException(upstream(), JGitText.get().notAuthorized), 403);

        assertEquals(401, e.getStatusCode());
        assertEquals(UpstreamFailure.NOT_AUTHORIZED_MESSAGE, e.getMessage());
    }

    @Test
    void upstreamWantsACredentialAndNoneWasSent_is401() throws Exception {
        var e = openFailure(new TransportException(upstream(), JGitText.get().noCredentialsProvider), 403);

        assertEquals(401, e.getStatusCode());
        assertEquals(UpstreamFailure.CREDENTIAL_REQUIRED_MESSAGE, e.getMessage());
    }

    @Test
    void upstreamRefusesAccess_followsTheConfiguredDenialStatus() throws Exception {
        var refused = new TransportException(
                upstream(),
                MessageFormat.format(
                        JGitText.get().serviceNotPermitted,
                        "https://upstream.example/owner/repo.git/",
                        "git-upload-pack"));

        var e = openFailure(refused, 403);

        assertEquals(403, e.getStatusCode());
        assertEquals(UpstreamFailure.FORBIDDEN_MESSAGE, e.getMessage());
    }

    /** An operator who answers refusals with 404 is hiding which repositories exist; the message must not tell. */
    @Test
    void upstreamRefusesAccess_under404_readsAsNotFound() throws Exception {
        var refused = new TransportException(
                upstream(),
                MessageFormat.format(
                        JGitText.get().serviceNotPermitted,
                        "https://upstream.example/owner/repo.git/",
                        "git-upload-pack"));

        var e = openFailure(refused, 404);

        assertEquals(404, e.getStatusCode());
        assertEquals(UpstreamFailure.NOT_FOUND_MESSAGE, e.getMessage());
    }

    @Test
    void upstreamHasNoSuchRepository_is404() throws Exception {
        var e = openFailure(new NoRemoteRepositoryException(upstream(), "not found"), 403);

        assertEquals(404, e.getStatusCode());
        assertEquals(UpstreamFailure.NOT_FOUND_MESSAGE, e.getMessage());
    }

    /** An outage used to read as a missing repository too. */
    @Test
    void upstreamUnreachable_is502() throws Exception {
        var e = openFailure(new TransportException(upstream(), JGitText.get().connectionFailed), 403);

        assertEquals(502, e.getStatusCode());
        assertEquals(UpstreamFailure.UNAVAILABLE_MESSAGE, e.getMessage());
    }

    @Test
    void localFailure_is500() throws Exception {
        var e = openFailure(new IOException("disk full"), 403);

        assertEquals(500, e.getStatusCode());
        assertEquals(UpstreamFailure.INTERNAL_MESSAGE, e.getMessage());
    }
}
