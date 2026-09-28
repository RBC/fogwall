package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.service.ScmOAuthTokenService;
import com.rbc.fogwall.service.ScmOAuthTokenService.Access;
import com.rbc.fogwall.service.ScmOAuthTokenService.Reason;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScmOAuthCredentialsProviderTest {

    ScmOAuthTokenService oauthTokens;
    ScmOAuthCredentialsProvider provider;
    URIish uri;

    @BeforeEach
    void setUp() throws Exception {
        oauthTokens = mock(ScmOAuthTokenService.class);
        provider = new ScmOAuthCredentialsProvider(oauthTokens, "alice", "gitlab");
        uri = new URIish("https://gitlab.example/owner/repo.git");
    }

    @Test
    void get_usableToken_fillsOauth2UsernameAndTheToken() {
        when(oauthTokens.access("alice", "gitlab")).thenReturn(new Access.Usable("tok-1", null));
        var username = new CredentialItem.Username();
        var password = new CredentialItem.Password();

        assertTrue(provider.get(uri, username, password));

        assertEquals("oauth2", username.getValue());
        assertEquals("tok-1", new String(password.getValue()));
    }

    @Test
    void get_unusableToken_declines() {
        when(oauthTokens.access("alice", "gitlab")).thenReturn(new Access.Unusable(Reason.EXPIRED));
        var username = new CredentialItem.Username();
        var password = new CredentialItem.Password();

        assertFalse(provider.get(uri, username, password));

        assertNull(username.getValue());
        assertNull(password.getValue());
    }

    @Test
    void get_looksTheTokenUpEveryTime() {
        when(oauthTokens.access("alice", "gitlab"))
                .thenReturn(new Access.Usable("tok-1", null))
                .thenReturn(new Access.Usable("tok-2", null));

        var first = new CredentialItem.Password();
        provider.get(uri, first);
        var second = new CredentialItem.Password();
        provider.get(uri, second);

        assertEquals("tok-1", new String(first.getValue()));
        assertEquals("tok-2", new String(second.getValue()));
        verify(oauthTokens, times(2)).access("alice", "gitlab");
    }

    @Test
    void supports_onlyUsernameAndPassword() {
        assertTrue(provider.supports(new CredentialItem.Username(), new CredentialItem.Password()));
        assertFalse(provider.supports(new CredentialItem.YesNoType("continue?")));
        assertFalse(provider.supports(new CredentialItem.Username(), new CredentialItem.StringType("x", false)));
    }

    @Test
    void isNotInteractive_andNamesItsUser() {
        assertFalse(provider.isInteractive());
        assertEquals("alice", provider.username());
    }

    @Test
    void access_asksTheTokenService() {
        when(oauthTokens.access("alice", "gitlab")).thenReturn(new Access.Unusable(Reason.NOT_LINKED));

        assertEquals(new Access.Unusable(Reason.NOT_LINKED), provider.access());
    }
}
