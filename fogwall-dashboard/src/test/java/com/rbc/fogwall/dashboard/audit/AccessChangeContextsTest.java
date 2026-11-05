package com.rbc.fogwall.dashboard.audit;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.dashboard.ApiKeyAuthenticationToken;
import com.rbc.fogwall.permission.ChangeContext;
import com.rbc.fogwall.permission.ChangeOrigin;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AccessChangeContextsTest {

    // Cleared on both sides: another test class on this thread may have left an authentication behind.
    @BeforeEach
    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static void signIn(Authentication auth) {
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void aSignedInUser_isADashboardChangeByThem() {
        signIn(new UsernamePasswordAuthenticationToken("alice", null, List.of()));

        assertEquals(ChangeContext.by(ChangeOrigin.DASHBOARD, "alice"), AccessChangeContexts.current());
    }

    @Test
    void theApiKey_isAnApiChange() {
        signIn(new ApiKeyAuthenticationToken(List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        assertEquals(
                ChangeContext.by(ChangeOrigin.API, ApiKeyAuthenticationToken.PRINCIPAL),
                AccessChangeContexts.current());
    }

    @Test
    void aUserNamedLikeTheApiKeyPrincipal_isStillADashboardChange() {
        signIn(new UsernamePasswordAuthenticationToken(ApiKeyAuthenticationToken.PRINCIPAL, null, List.of()));

        assertEquals(ChangeOrigin.DASHBOARD, AccessChangeContexts.current().origin());
    }

    @Test
    void noAuthentication_isRefused() {
        assertThrows(IllegalStateException.class, AccessChangeContexts::current);
    }
}
