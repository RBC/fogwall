package com.rbc.fogwall.servlet.filter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.git.CredentialAuthentication;
import com.rbc.fogwall.git.ScmOAuthCredentialsProvider;
import com.rbc.fogwall.git.ServerRepositoryResolver;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.provider.GitLabProvider;
import com.rbc.fogwall.service.GitCredentialService;
import com.rbc.fogwall.service.ScmOAuthTokenService;
import com.rbc.fogwall.service.ScmOAuthTokenService.Access;
import com.rbc.fogwall.service.ScmOAuthTokenService.Reason;
import com.rbc.fogwall.user.GitCredential;
import com.rbc.fogwall.user.ReadOnlyUserStore;
import com.rbc.fogwall.user.UserEntry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FogwallCredentialFilterTest {

    private static final String VALUE = "fgw_abcdefghijklmnop_secret";
    private static final GitCredential CREDENTIAL =
            new GitCredential("abcdefghijklmnop", "alice", "laptop", "{sha256}h", Instant.EPOCH, null, null);
    private static final UserEntry ALICE = UserEntry.builder()
            .username("alice")
            .emails(List.of())
            .scmIdentities(List.of())
            .build();

    FogwallProvider provider;
    GitCredentialService credentials;
    ScmOAuthTokenService oauthTokens;
    ReadOnlyUserStore users;
    FilterChain chain;
    Map<String, Object> attributes;
    ByteArrayOutputStream body;
    HttpServletResponse resp;

    @BeforeEach
    void setUp() throws Exception {
        provider = new GitLabProvider("/server");
        credentials = mock(GitCredentialService.class);
        oauthTokens = mock(ScmOAuthTokenService.class);
        users = mock(ReadOnlyUserStore.class);
        chain = mock(FilterChain.class);
        attributes = new HashMap<>();
        body = new ByteArrayOutputStream();
        resp = mock(HttpServletResponse.class);
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
    }

    private FogwallCredentialFilter filter(boolean brokeredPush) {
        return new FogwallCredentialFilter(
                provider, brokeredPush, credentials, oauthTokens, users, "https://fogwall.example");
    }

    /** A {@code GET .../info/refs?service=git-receive-pack} request, the shape a refusal is written back to. */
    private HttpServletRequest infoRefsRequest(String authHeader) {
        return infoRefsRequest(authHeader, "git-receive-pack");
    }

    private HttpServletRequest infoRefsRequest(String authHeader, String service) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("GET");
        when(req.getRequestURI()).thenReturn("/server/gitlab/owner/repo.git/info/refs");
        when(req.getParameter("service")).thenReturn(service);
        when(req.getHeader("Authorization")).thenReturn(authHeader);
        doAnswer(inv -> {
                    attributes.put(inv.getArgument(0), inv.getArgument(1));
                    return null;
                })
                .when(req)
                .setAttribute(anyString(), any());
        when(req.getAttribute(anyString())).thenAnswer(inv -> attributes.get(inv.getArgument(0)));
        return req;
    }

    private static String basicAuth(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private String responseBody() {
        return body.toString(StandardCharsets.UTF_8);
    }

    // ---- pass-through ----

    @Test
    void noAuthorization_passesThrough() throws Exception {
        HttpServletRequest req = infoRefsRequest(null);

        filter(true).doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        assertTrue(attributes.isEmpty());
        verifyNoInteractions(credentials, oauthTokens, users);
    }

    @Test
    void scmCredential_passesThroughUntouched() throws Exception {
        HttpServletRequest req = infoRefsRequest(basicAuth("me", "glpat-token"));

        filter(true).doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        assertTrue(attributes.isEmpty());
        verify(resp, never()).sendError(anyInt());
        verify(resp, never()).setStatus(anyInt());
        verifyNoInteractions(credentials, oauthTokens, users);
    }

    @Test
    void undecodableAuthorization_passesThrough() throws Exception {
        HttpServletRequest req = infoRefsRequest("Basic %%%not-base64%%%");

        filter(true).doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verifyNoInteractions(credentials);
    }

    // ---- refusals ----

    @Test
    void fogwallCredential_onAProviderThatDoesNotBrokerPushes_isRefused() throws Exception {
        HttpServletRequest req = infoRefsRequest(basicAuth("me", VALUE));

        filter(false).doFilter(req, resp, chain);

        verifyNoInteractions(chain, credentials, oauthTokens, users);
        verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertTrue(responseBody().contains("fogwall credentials are not accepted for gitlab"));
        assertTrue(attributes.isEmpty());
    }

    @Test
    void invalidCredential_isChallengedAgain() throws Exception {
        when(credentials.authenticate(VALUE)).thenReturn(Optional.empty());
        HttpServletRequest req = infoRefsRequest(basicAuth("me", VALUE));

        filter(true).doFilter(req, resp, chain);

        verify(resp).setHeader("WWW-Authenticate", "Basic realm=\"fogwall\"");
        verify(resp).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(chain, oauthTokens, users);
        assertTrue(attributes.isEmpty());
    }

    @Test
    void validCredential_forAUserNoLongerOnFile_isChallengedAgain() throws Exception {
        when(credentials.authenticate(VALUE)).thenReturn(Optional.of(CREDENTIAL));
        when(users.findByUsername("alice")).thenReturn(Optional.empty());
        HttpServletRequest req = infoRefsRequest(basicAuth("me", VALUE));

        filter(true).doFilter(req, resp, chain);

        verify(resp).setHeader("WWW-Authenticate", "Basic realm=\"fogwall\"");
        verify(resp).sendError(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(chain, oauthTokens);
        assertTrue(attributes.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Reason.class)
    void validCredential_withAnUnusableLinkedToken_isRefusedWithTheRemedy(Reason reason) throws Exception {
        when(credentials.authenticate(VALUE)).thenReturn(Optional.of(CREDENTIAL));
        when(users.findByUsername("alice")).thenReturn(Optional.of(ALICE));
        when(oauthTokens.access("alice", "gitlab")).thenReturn(new Access.Unusable(reason));
        HttpServletRequest req = infoRefsRequest(basicAuth("me", VALUE));

        FogwallCredentialFilter filter = filter(true);
        filter.doFilter(req, resp, chain);

        verifyNoInteractions(chain);
        verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertTrue(responseBody().contains(filter.unusableTokenMessage(reason)));
        assertTrue(attributes.isEmpty());
    }

    @Test
    void unusableTokenMessage_namesTheProfilePageWhenTheUserCanFixIt() {
        FogwallCredentialFilter filter = filter(true);
        String profile = "https://fogwall.example/dashboard/profile";

        assertTrue(filter.unusableTokenMessage(Reason.NOT_LINKED).contains(profile));
        assertTrue(filter.unusableTokenMessage(Reason.NOT_LINKED).contains("gitlab account is not linked"));
        assertTrue(filter.unusableTokenMessage(Reason.EXPIRED).contains(profile));
        assertTrue(filter.unusableTokenMessage(Reason.EXPIRED).contains("Link it again"));
        assertFalse(filter.unusableTokenMessage(Reason.REFRESH_FAILED).contains(profile));
        assertTrue(filter.unusableTokenMessage(Reason.REFRESH_FAILED).contains("Retry shortly"));
        assertFalse(filter.unusableTokenMessage(Reason.KEY_UNAVAILABLE).contains(profile));
        assertTrue(filter.unusableTokenMessage(Reason.KEY_UNAVAILABLE).contains("Contact an administrator"));
    }

    @Test
    void unusableTokenMessage_withoutAServiceUrl_stillPointsAtTheProfile() {
        var filter = new FogwallCredentialFilter(provider, true, credentials, oauthTokens, users, null);

        assertTrue(filter.unusableTokenMessage(Reason.NOT_LINKED).contains("your fogwall profile"));
    }

    // ---- push scope ----

    @Test
    void push_withALinkedTokenThatCannotPush_isRefusedWithTheRemedy() throws Exception {
        when(credentials.authenticate(VALUE)).thenReturn(Optional.of(CREDENTIAL));
        when(users.findByUsername("alice")).thenReturn(Optional.of(ALICE));
        when(oauthTokens.access("alice", "gitlab")).thenReturn(new Access.Usable("tok", "read_user"));
        HttpServletRequest req = infoRefsRequest(basicAuth("me", VALUE));

        FogwallCredentialFilter filter = filter(true);
        filter.doFilter(req, resp, chain);

        verifyNoInteractions(chain);
        verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertTrue(responseBody().contains(filter.missingPushScopeMessage()));
        assertTrue(filter.missingPushScopeMessage().contains("Link it again"));
    }

    @Test
    void fetch_withALinkedTokenThatCannotPush_chains() throws Exception {
        when(credentials.authenticate(VALUE)).thenReturn(Optional.of(CREDENTIAL));
        when(users.findByUsername("alice")).thenReturn(Optional.of(ALICE));
        when(oauthTokens.access("alice", "gitlab")).thenReturn(new Access.Usable("tok", "read_user"));
        HttpServletRequest req = infoRefsRequest(basicAuth("me", VALUE), "git-upload-pack");

        filter(true).doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }

    @Test
    void push_withALinkedTokenThatCanPush_chains() throws Exception {
        when(credentials.authenticate(VALUE)).thenReturn(Optional.of(CREDENTIAL));
        when(users.findByUsername("alice")).thenReturn(Optional.of(ALICE));
        when(oauthTokens.access("alice", "gitlab")).thenReturn(new Access.Usable("tok", "read_user write_repository"));
        HttpServletRequest req = infoRefsRequest(basicAuth("me", VALUE));

        filter(true).doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }

    // ---- success ----

    @Test
    void validCredential_withAUsableLinkedToken_chainsWithTheUserAndTheTokenInPlace() throws Exception {
        when(credentials.authenticate(VALUE)).thenReturn(Optional.of(CREDENTIAL));
        when(users.findByUsername("alice")).thenReturn(Optional.of(ALICE));
        when(oauthTokens.access("alice", "gitlab")).thenReturn(new Access.Usable("tok", null));
        HttpServletRequest req = infoRefsRequest(basicAuth("me", VALUE));

        filter(true).doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verify(resp, never()).sendError(anyInt());
        verify(resp, never()).setStatus(anyInt());

        var authentication =
                (CredentialAuthentication) attributes.get(FogwallCredentialFilter.AUTHENTICATION_ATTRIBUTE);
        assertNotNull(authentication);
        assertEquals(ALICE, authentication.user());
        assertEquals("abcdefghijklmnop", authentication.credentialId());
        assertEquals("laptop", authentication.credentialName());

        var linked = (ScmOAuthCredentialsProvider) attributes.get(ServerRepositoryResolver.CREDENTIALS_ATTRIBUTE);
        assertNotNull(linked);
        assertEquals("alice", linked.username());
        assertEquals(new Access.Usable("tok", null), linked.access());
    }
}
