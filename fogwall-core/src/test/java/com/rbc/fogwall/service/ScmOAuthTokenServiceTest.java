package com.rbc.fogwall.service;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.crypto.TokenCipher;
import com.rbc.fogwall.crypto.TokenCipherProvider;
import com.rbc.fogwall.provider.BitbucketProvider;
import com.rbc.fogwall.provider.GitLabProvider;
import com.rbc.fogwall.provider.InMemoryProviderRegistry;
import com.rbc.fogwall.service.ScmOAuthTokenService.Access;
import com.rbc.fogwall.service.ScmOAuthTokenService.OAuthClient;
import com.rbc.fogwall.service.ScmOAuthTokenService.Reason;
import com.rbc.fogwall.user.ScmOAuthToken;
import com.rbc.fogwall.user.ScmOAuthTokenStore;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises {@link ScmOAuthTokenService} against a local stub of a provider's OAuth token endpoint. */
class ScmOAuthTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");
    private static final String USER = "alice";
    private static final String PROVIDER = "gitlab";
    private static final String SCOPES = "read_user write_repository";

    @TempDir
    Path tempDir;

    private HttpServer server;
    private final List<Map<String, String>> requests = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile int responseStatus = 200;
    private volatile String responseBody;
    private volatile Runnable beforeResponse = () -> {};

    private final FakeTokenStore store = new FakeTokenStore();
    private TokenCipherProvider cipherProvider;
    private TokenCipher cipher;
    private InMemoryProviderRegistry registry;
    private Map<String, OAuthClient> clients;
    private ScmOAuthTokenService service;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/oauth/token", exchange -> {
            requestCount.incrementAndGet();
            requests.add(parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            beforeResponse.run();
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        cipherProvider = TokenCipherProvider.initialize(Optional.empty(), tempDir.resolve("key"));
        cipher = cipherProvider.cipher().orElseThrow();
        var gitlab = GitLabProvider.builder()
                .name(PROVIDER)
                .uri(URI.create("http://localhost:" + server.getAddress().getPort()))
                .build();
        registry = new InMemoryProviderRegistry(List.of(gitlab));
        clients = Map.of(PROVIDER, new OAuthClient("the-client", "the-secret"));
        service = withMaxLinkAge(Optional.empty());
    }

    private ScmOAuthTokenService withMaxLinkAge(Optional<Duration> maxLinkAge) {
        return new ScmOAuthTokenService(
                store,
                cipherProvider,
                registry,
                clients,
                "https://fogwall.example.com",
                maxLinkAge,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    // ---- Tokens that need no refresh ----

    @Test
    void notLinked_isUnusable() {
        assertEquals(new Access.Unusable(Reason.NOT_LINKED), service.access(USER, PROVIDER));
    }

    @Test
    void tokenWithoutExpiry_isHandedOutWithoutRefreshing() {
        link("access-1", "refresh-1", null);

        assertEquals(new Access.Usable("access-1", SCOPES), service.access(USER, PROVIDER));
        assertEquals(0, requestCount.get());
    }

    @Test
    void currentToken_isHandedOutWithoutRefreshing() {
        link("access-1", "refresh-1", NOW.plus(Duration.ofHours(1)));

        assertEquals(new Access.Usable("access-1", SCOPES), service.access(USER, PROVIDER));
        assertEquals(0, requestCount.get());
    }

    @Test
    void encryptionKeyUnavailable_isUnusable() {
        link("access-1", "refresh-1", null);
        var withoutKey = new ScmOAuthTokenService(
                store,
                TokenCipherProvider.unavailable(),
                new InMemoryProviderRegistry(List.of()),
                Map.of(),
                null,
                Optional.empty(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertEquals(new Access.Unusable(Reason.KEY_UNAVAILABLE), withoutKey.access(USER, PROVIDER));
    }

    // ---- Maximum link age ----

    @Test
    void linkPastTheMaximumAge_isUnusable_evenWithACurrentToken() {
        store.linkedAt = NOW.minus(Duration.ofDays(30));
        link("access-1", "refresh-1", NOW.plus(Duration.ofHours(1)));

        var aged = withMaxLinkAge(Optional.of(Duration.ofDays(30)));

        assertEquals(new Access.Unusable(Reason.LINK_EXPIRED), aged.access(USER, PROVIDER));
        assertTrue(aged.currentLink(USER, PROVIDER).isEmpty());
        assertEquals(0, requestCount.get());
    }

    @Test
    void linkWithinTheMaximumAge_isUsable() {
        store.linkedAt = NOW.minus(Duration.ofDays(29));
        link("access-1", "refresh-1", null);

        var aged = withMaxLinkAge(Optional.of(Duration.ofDays(30)));

        assertEquals(new Access.Usable("access-1", SCOPES), aged.access(USER, PROVIDER));
        assertTrue(aged.currentLink(USER, PROVIDER).isPresent());
    }

    @Test
    void linkStatuses_reportWhenEachLinkAgesOut() {
        store.linkedAt = NOW.minus(Duration.ofDays(10));
        link("access-1", null, null);

        assertEquals(
                List.of(new ScmOAuthTokenService.LinkStatus(
                        PROVIDER, NOW.minus(Duration.ofDays(10)), NOW.plus(Duration.ofDays(20)), false)),
                withMaxLinkAge(Optional.of(Duration.ofDays(30))).linkStatuses(USER));
        assertEquals(
                List.of(new ScmOAuthTokenService.LinkStatus(PROVIDER, NOW.minus(Duration.ofDays(10)), null, false)),
                service.linkStatuses(USER));
        assertTrue(withMaxLinkAge(Optional.of(Duration.ofDays(10)))
                .linkStatuses(USER)
                .get(0)
                .expired());
    }

    @Test
    void refreshing_doesNotRestartTheLinkAge() {
        store.linkedAt = NOW.minus(Duration.ofDays(29));
        link("access-1", "refresh-1", NOW.minus(Duration.ofMinutes(5)));
        responseBody = """
                {"access_token":"access-2","refresh_token":"refresh-2","expires_in":7200}
                """;

        withMaxLinkAge(Optional.of(Duration.ofDays(30))).access(USER, PROVIDER);

        assertEquals(
                NOW.minus(Duration.ofDays(29)),
                store.findToken(USER, PROVIDER).orElseThrow().authorizedAt());
    }

    // ---- Refresh ----

    @Test
    void expiredToken_isRefreshed_andTheRotatedTokensStored() {
        link("access-1", "refresh-1", NOW.minus(Duration.ofMinutes(5)));
        responseBody = """
                {"access_token":"access-2","refresh_token":"refresh-2","expires_in":7200,"token_type":"Bearer"}
                """;

        assertEquals(new Access.Usable("access-2", SCOPES), service.access(USER, PROVIDER));

        Map<String, String> form = requests.get(0);
        assertEquals("refresh_token", form.get("grant_type"));
        assertEquals("refresh-1", form.get("refresh_token"));
        assertEquals("the-client", form.get("client_id"));
        assertEquals("the-secret", form.get("client_secret"));
        assertEquals("https://fogwall.example.com/api/scm-oauth/gitlab/callback", form.get("redirect_uri"));

        ScmOAuthToken stored = store.findToken(USER, PROVIDER).orElseThrow();
        assertEquals("access-2", decrypt(stored.encryptedAccessToken()));
        assertEquals("refresh-2", decrypt(stored.encryptedRefreshToken()));
        assertEquals(NOW.plusSeconds(7200), stored.expiresAt());
        assertEquals("read_user write_repository", stored.scopes(), "a refresh does not re-authorize the link");
    }

    @Test
    void tokenExpiringWithinTheSkew_isRefreshed() {
        link("access-1", "refresh-1", NOW.plus(Duration.ofSeconds(30)));
        responseBody = """
                {"access_token":"access-2","refresh_token":"refresh-2","expires_in":7200}
                """;

        assertEquals(new Access.Usable("access-2", SCOPES), service.access(USER, PROVIDER));
    }

    @Test
    void refreshWithoutARotatedRefreshToken_keepsTheOldOne() {
        link("access-1", "refresh-1", NOW.minus(Duration.ofMinutes(5)));
        responseBody = """
                {"access_token":"access-2","expires_in":3600}
                """;

        service.access(USER, PROVIDER);

        assertEquals(
                "refresh-1",
                decrypt(store.findToken(USER, PROVIDER).orElseThrow().encryptedRefreshToken()));
    }

    @Test
    void expiredTokenWithoutRefreshToken_isExpired_withoutCallingTheProvider() {
        link("access-1", null, NOW.minus(Duration.ofMinutes(5)));

        assertEquals(new Access.Unusable(Reason.EXPIRED), service.access(USER, PROVIDER));
        assertEquals(0, requestCount.get());
    }

    @Test
    void refusedRefresh_isExpired() {
        link("access-1", "refresh-1", NOW.minus(Duration.ofMinutes(5)));
        responseStatus = 400;
        responseBody = """
                {"error":"invalid_grant","error_description":"The provided authorization grant is invalid"}
                """;

        assertEquals(new Access.Unusable(Reason.EXPIRED), service.access(USER, PROVIDER));
    }

    @Test
    void refusalReportedWithHttp200_isExpired() {
        // GitHub answers a bad refresh token with 200 and an error field.
        link("access-1", "refresh-1", NOW.minus(Duration.ofMinutes(5)));
        responseBody = """
                {"error":"bad_refresh_token","error_description":"The refresh token passed is incorrect or expired."}
                """;

        assertEquals(new Access.Unusable(Reason.EXPIRED), service.access(USER, PROVIDER));
    }

    @Test
    void providerError_isARefreshFailure_notAnExpiry() {
        link("access-1", "refresh-1", NOW.minus(Duration.ofMinutes(5)));
        responseStatus = 503;
        responseBody = "unavailable";

        assertEquals(new Access.Unusable(Reason.REFRESH_FAILED), service.access(USER, PROVIDER));
        assertEquals(
                "access-1",
                decrypt(store.findToken(USER, PROVIDER).orElseThrow().encryptedAccessToken()),
                "a failed refresh leaves the stored token as it was");
    }

    @Test
    void refusedRefresh_afterAnotherInstanceRefreshed_usesTheirToken() {
        link("access-1", "refresh-1", NOW.minus(Duration.ofMinutes(5)));
        // Another instance sharing the database rotated the refresh token between our read and our request.
        beforeResponse = () -> link("access-other", "refresh-other", NOW.plus(Duration.ofHours(2)));
        responseStatus = 400;
        responseBody = """
                {"error":"invalid_grant"}
                """;

        assertEquals(new Access.Usable("access-other", SCOPES), service.access(USER, PROVIDER));
    }

    @Test
    void providerWithoutOAuthClient_cannotRefresh() {
        link("access-1", "refresh-1", NOW.minus(Duration.ofMinutes(5)));
        var unconfigured = new ScmOAuthTokenService(
                store,
                cipherProvider,
                new InMemoryProviderRegistry(List.of(new BitbucketProvider("/proxy"))),
                Map.of(),
                null,
                Optional.empty(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertEquals(new Access.Unusable(Reason.EXPIRED), unconfigured.access(USER, PROVIDER));
        assertEquals(0, requestCount.get());
    }

    @Test
    void concurrentAccess_refreshesOnce() throws Exception {
        // Providers rotate the refresh token, so a second concurrent refresh with the old one would be refused.
        link("access-1", "refresh-1", NOW.minus(Duration.ofMinutes(5)));
        CountDownLatch requestSeen = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        beforeResponse = () -> {
            requestSeen.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        responseBody = """
                {"access_token":"access-2","refresh_token":"refresh-2","expires_in":7200}
                """;

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Access>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                results.add(pool.submit(() -> service.access(USER, PROVIDER)));
            }
            requestSeen.await();
            release.countDown();
            for (Future<Access> result : results) {
                assertEquals(new Access.Usable("access-2", SCOPES), result.get());
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, requestCount.get());
    }

    @Test
    void linkedProviders_comeFromTheStore() {
        link("access-1", null, null);

        assertEquals(List.of(PROVIDER), service.linkedProviders(USER));
    }

    // ---- Helpers ----

    private void link(String accessToken, String refreshToken, Instant expiresAt) {
        store.save(
                USER,
                PROVIDER,
                encrypt(accessToken),
                refreshToken != null ? encrypt(refreshToken) : null,
                SCOPES,
                expiresAt);
    }

    private byte[] encrypt(String value) {
        return cipher.encrypt(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decrypt(byte[] value) {
        return new String(cipher.decrypt(value), StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> form = new HashMap<>();
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            form.put(
                    URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return form;
    }

    /** Holds tokens in memory, with the same replace-only-if-present semantics as the database stores. */
    private static final class FakeTokenStore implements ScmOAuthTokenStore {

        private final Map<String, ScmOAuthToken> tokens = new ConcurrentHashMap<>();

        /** When the next {@link #save} records the user as having linked the account. */
        Instant linkedAt = NOW;

        @Override
        public void save(
                String username,
                String provider,
                byte[] encryptedAccessToken,
                byte[] encryptedRefreshToken,
                String scopes,
                Instant expiresAt) {
            tokens.put(
                    key(username, provider),
                    new ScmOAuthToken(encryptedAccessToken, encryptedRefreshToken, scopes, expiresAt, linkedAt));
        }

        @Override
        public Optional<byte[]> findAccessToken(String username, String provider) {
            return findToken(username, provider).map(ScmOAuthToken::encryptedAccessToken);
        }

        @Override
        public Optional<ScmOAuthToken> findToken(String username, String provider) {
            return Optional.ofNullable(tokens.get(key(username, provider)));
        }

        @Override
        public void replaceTokens(
                String username,
                String provider,
                byte[] encryptedAccessToken,
                byte[] encryptedRefreshToken,
                Instant expiresAt) {
            tokens.computeIfPresent(
                    key(username, provider),
                    (k, old) -> new ScmOAuthToken(
                            encryptedAccessToken, encryptedRefreshToken, old.scopes(), expiresAt, old.authorizedAt()));
        }

        @Override
        public void remove(String username, String provider) {
            tokens.remove(key(username, provider));
        }

        @Override
        public List<String> findLinkedProviders(String username) {
            return tokens.keySet().stream()
                    .filter(k -> k.startsWith(username + "\n"))
                    .map(k -> k.substring(username.length() + 1))
                    .sorted()
                    .toList();
        }

        private static String key(String username, String provider) {
            return username + "\n" + provider;
        }
    }
}
