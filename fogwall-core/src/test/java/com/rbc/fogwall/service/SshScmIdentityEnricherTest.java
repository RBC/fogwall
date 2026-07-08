package com.rbc.fogwall.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.provider.SshKeyFingerprintLookup;
import com.rbc.fogwall.user.ScmIdentity;
import com.rbc.fogwall.user.UserEntry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SshScmIdentityEnricherTest {

    interface FingerprintProvider extends FogwallProvider, SshKeyFingerprintLookup {}

    FingerprintProvider provider;
    SshFingerprintCache persistentCache;
    SshScmIdentityEnricher enricher;

    UserEntry aliceUser;

    @BeforeEach
    void setUp() {
        provider = mock(FingerprintProvider.class);
        when(provider.getName()).thenReturn("github");
        when(provider.getProviderId()).thenReturn("github");
        persistentCache = mock(SshFingerprintCache.class);
        when(persistentCache.lookup(any(), any())).thenReturn(Set.of());

        enricher = new SshScmIdentityEnricher(Duration.ofMinutes(10), persistentCache);

        aliceUser = UserEntry.builder()
                .username("alice")
                .passwordHash("{noop}pw")
                .emails(List.of())
                .scmIdentities(List.of(ScmIdentity.builder()
                        .provider("github")
                        .username("alice-gh")
                        .build()))
                .build();
    }

    @Test
    void resolve_providerDoesNotSupportSsh_returnsEmpty() {
        FogwallProvider noSsh = mock(FogwallProvider.class);
        when(noSsh.getName()).thenReturn("generic");

        Optional<String> result = enricher.resolveScmLogin(aliceUser, noSsh, "SHA256:abc");

        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_userHasNoScmIdentities_returnsEmpty() {
        UserEntry noIdentities = UserEntry.builder()
                .username("anon")
                .passwordHash("{noop}pw")
                .emails(List.of())
                .scmIdentities(List.of())
                .build();

        Optional<String> result = enricher.resolveScmLogin(noIdentities, provider, "SHA256:abc");

        assertTrue(result.isEmpty());
        verify(provider, never()).fetchSshFingerprints(any());
    }

    @Test
    void resolve_fingerprintMatches_returnsScmLogin() {
        when(persistentCache.lookup("github", "alice-gh")).thenReturn(Set.of());
        when(provider.fetchSshFingerprints("alice-gh")).thenReturn(Set.of("SHA256:abc", "SHA256:def"));

        Optional<String> result = enricher.resolveScmLogin(aliceUser, provider, "SHA256:abc");

        assertEquals(Optional.of("alice-gh"), result);
    }

    @Test
    void resolve_fingerprintDoesNotMatch_returnsEmpty() {
        when(provider.fetchSshFingerprints("alice-gh")).thenReturn(Set.of("SHA256:other"));

        Optional<String> result = enricher.resolveScmLogin(aliceUser, provider, "SHA256:abc");

        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_hitsPersistentCacheOnSecondCall() {
        when(persistentCache.lookup("github", "alice-gh")).thenReturn(Set.of("SHA256:abc"));

        Optional<String> result = enricher.resolveScmLogin(aliceUser, provider, "SHA256:abc");

        assertEquals(Optional.of("alice-gh"), result);
        verify(provider, never()).fetchSshFingerprints(any());
    }

    @Test
    void resolve_hitsMemoryCacheOnSecondCall() {
        when(provider.fetchSshFingerprints("alice-gh")).thenReturn(Set.of("SHA256:abc"));

        enricher.resolveScmLogin(aliceUser, provider, "SHA256:abc");
        enricher.resolveScmLogin(aliceUser, provider, "SHA256:abc");

        // Provider API called only once; second call served from mem cache
        verify(provider, times(1)).fetchSshFingerprints("alice-gh");
    }

    @Test
    void resolve_emptyApiResult_notCached() {
        when(provider.fetchSshFingerprints("alice-gh")).thenReturn(Set.of());

        enricher.resolveScmLogin(aliceUser, provider, "SHA256:abc");
        enricher.resolveScmLogin(aliceUser, provider, "SHA256:abc");

        // Not cached — API called on every miss
        verify(provider, times(2)).fetchSshFingerprints("alice-gh");
        verify(persistentCache, never()).store(any(), any(), any());
    }

    @Test
    void evict_clearsMemoryCacheAndPersistentCache() {
        when(provider.fetchSshFingerprints("alice-gh")).thenReturn(Set.of("SHA256:abc"));
        enricher.resolveScmLogin(aliceUser, provider, "SHA256:abc");

        enricher.evict("github", "alice-gh");

        // Next call should miss mem cache and go to persistent cache
        enricher.resolveScmLogin(aliceUser, provider, "SHA256:abc");
        verify(provider, times(2)).fetchSshFingerprints("alice-gh");
        verify(persistentCache).evict("github", "alice-gh");
    }

    @Test
    void resolve_storesNormalisedFingerprintsToAllCaches() {
        when(provider.fetchSshFingerprints("alice-gh")).thenReturn(Set.of("SHA256:zzz", "SHA256:aaa"));

        enricher.resolveScmLogin(aliceUser, provider, "SHA256:aaa");

        // Sorted set stored to persistent cache
        verify(persistentCache)
                .store(
                        eq("github"),
                        eq("alice-gh"),
                        argThat(fp -> fp.containsAll(Set.of("SHA256:aaa", "SHA256:zzz")) && fp.size() == 2));
    }

    @Test
    void resolve_noPersistentCache_worksWithMemCacheOnly() {
        SshScmIdentityEnricher noDb = new SshScmIdentityEnricher(Duration.ofMinutes(10), null);
        when(provider.fetchSshFingerprints("alice-gh")).thenReturn(Set.of("SHA256:abc"));

        Optional<String> result = noDb.resolveScmLogin(aliceUser, provider, "SHA256:abc");

        assertEquals(Optional.of("alice-gh"), result);
    }
}
