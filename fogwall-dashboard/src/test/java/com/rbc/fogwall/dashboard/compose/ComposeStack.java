package com.rbc.fogwall.dashboard.compose;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * The stack {@code compose.sh} brought up, addressed from outside: fogwall on 8080 and Gitea on 3000, with whatever
 * database and auth overlay the run chose.
 *
 * <p>Black-box: nothing here starts or configures the stack, so the suite only ever asserts on what the packaged image
 * does with the shipped config. The one thing it does create is a Gitea token for itself, a credential rather than
 * configuration.
 *
 * <p>Every test skips when no stack is up, so {@code ./gradlew composeTest} on a fresh clone is quiet, not red.
 */
final class ComposeStack {

    /** Seeded by {@code docker/gitea-seed.sh}; owns the orgs and repos, and is not mapped to a fogwall user. */
    static final String GITEA_ADMIN = "fogwalladmin";

    static final String GITEA_ADMIN_PASSWORD = "Admin1234!";

    /** Seeded by {@code docker/gitea-seed.sh} and mapped to the fogwall user of the same name. */
    static final String TEST_USER = "test-user";

    static final String TEST_USER_PASSWORD = "Test1234!";

    /** {@code test-user} holds a LITERAL push grant on this one, and nothing on any other. */
    static final String TEST_ORG = "test-owner";

    static final String TEST_REPO = "test-repo";

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String fogwallUrl;
    private final String giteaUrl;
    private final String giteaHostPort;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private ComposeStack(String fogwallUrl, String giteaUrl, String giteaHostPort) {
        this.fogwallUrl = fogwallUrl;
        this.giteaUrl = giteaUrl;
        this.giteaHostPort = giteaHostPort;
    }

    /**
     * Resolves the stack's addresses and checks it is answering, skipping the calling test when it is not.
     *
     * @return the stack, never null — an unreachable stack aborts the test instead
     */
    static ComposeStack requireRunning() {
        var stack = new ComposeStack(
                property("fogwall.url", "FOGWALL_URL", "http://localhost:8080"),
                property("fogwall.gitea.url", "GITEA_URL", "http://localhost:3000"),
                property("fogwall.gitea.host", "GITEA_HOST", "gitea:3000"));
        assumeTrue(
                stack.healthy(),
                "no compose stack answering at " + stack.fogwallUrl + " — run: bash compose.sh -- up -d");
        return stack;
    }

    private static String property(String systemProperty, String envVar, String fallback) {
        String value = System.getProperty(systemProperty, "");
        if (value.isBlank()) {
            value = System.getenv().getOrDefault(envVar, "");
        }
        return (value.isBlank() ? fallback : value).replaceAll("/+$", "");
    }

    /** Whether fogwall's health endpoint answers. The packaging check: the image booted and mounted its config. */
    boolean healthy() {
        try {
            return get(fogwallUrl + "/api/health").statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    String fogwallUrl() {
        return fogwallUrl;
    }

    String giteaUrl() {
        return giteaUrl;
    }

    /**
     * {@code host:port} of Gitea <em>as fogwall addresses it</em>, which is the segment a proxy URL carries.
     *
     * <p>Not the same as {@link #giteaUrl()}: inside the compose network fogwall reaches Gitea at {@code gitea:3000},
     * while the suite reaches it on a published port. A proxy URL built from the published one routes to a provider
     * fogwall has never heard of, and comes back as a missing repository.
     */
    String giteaHostPort() {
        return giteaHostPort;
    }

    /**
     * Issues a Gitea access token for {@code username}. A token, not the password: fogwall resolves identity by asking
     * Gitea whose credential this is, and pushes forward the same credential upstream.
     */
    String accessToken(String username, String password) throws IOException, InterruptedException {
        return accessToken(username, password, List.of("read:user", "write:repository"));
    }

    /**
     * A token wide enough for the SCM API surface. Opening a pull request reads and writes issues, because Gitea models
     * a pull request as one — a push-scoped token gets as far as the listener and is then refused upstream.
     */
    String scmApiToken(String username, String password) throws IOException, InterruptedException {
        return accessToken(
                username,
                password,
                List.of("read:user", "read:repository", "write:repository", "read:issue", "write:issue"));
    }

    private String accessToken(String username, String password, List<String> scopes)
            throws IOException, InterruptedException {
        String body = new JsonMapper()
                .writeValueAsString(Map.of("name", "compose-suite-" + System.nanoTime(), "scopes", scopes));
        var request = HttpRequest.newBuilder(URI.create(giteaUrl + "/api/v1/users/" + username + "/tokens"))
                .timeout(TIMEOUT)
                .header("Authorization", "Basic " + basic(username, password))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("Gitea refused a token for " + username + ": " + response.statusCode());
        }
        return new JsonMapper().readTree(response.body()).get("sha1").asText();
    }

    /** A push/clone URL through fogwall, in the given mode ({@code proxy} or {@code server}). */
    String proxyUrl(String mode, String username, String token, String org, String repo) {
        String creds = encode(username) + ":" + encode(token);
        URI uri = URI.create(fogwallUrl);
        return uri.getScheme() + "://" + creds + "@" + uri.getHost() + ":" + uri.getPort() + "/" + mode + "/"
                + giteaHostPort() + "/" + org + "/" + repo + ".git";
    }

    /** {@code host:port} where fogwall's SSH transport is published; only exists under the {@code ssh} overlay. */
    String sshHostPort() {
        return property("fogwall.ssh.host", "FOGWALL_SSH_HOST", "localhost:2222");
    }

    /** Whether fogwall's SSH server is answering — the {@code ssh} overlay is in play. */
    boolean sshReachable() {
        String hostPort = sshHostPort();
        int sep = hostPort.lastIndexOf(':');
        try (var socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(hostPort.substring(0, sep), Integer.parseInt(hostPort.substring(sep + 1))),
                    2000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * A git-over-SSH URL through fogwall, routed to the gitea provider by the same host:port segment the proxy uses.
     */
    String sshUrl(String org, String repo) {
        return "ssh://" + sshHostPort() + "/" + giteaHostPort() + "/" + org + "/" + repo + ".git";
    }

    /** The gitea provider's SCM API listener, which the contributions overlay publishes over TLS. */
    String scmApiUrl() {
        return property("fogwall.scmapi.url", "FOGWALL_SCM_API_URL", "https://localhost:8484");
    }

    /** Whether that listener is answering at all — it only exists when the contributions overlay is in play. */
    boolean scmApiReachable() {
        try {
            // 401 is the healthy answer to an unauthenticated call; anything that connects proves the listener is up.
            return tlsClient()
                            .send(
                                    HttpRequest.newBuilder(URI.create(scmApiUrl() + "/api/v1/version"))
                                            .timeout(TIMEOUT)
                                            .GET()
                                            .build(),
                                    HttpResponse.BodyHandlers.ofString())
                            .statusCode()
                    > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * POSTs to the SCM API listener as a developer's CLI would: the provider's own REST dialect, the provider's own
     * token header, over the listener's TLS.
     */
    HttpResponse<String> scmApiPost(String path, String token, String body) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(scmApiUrl() + path))
                .timeout(TIMEOUT)
                .header("Authorization", "token " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return tlsClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * A client that trusts the CA {@code test/make-certs.sh} generated, and nothing else.
     *
     * <p>Trusting only that CA, rather than everything, checks the listener is presenting the certificate it was
     * configured with; skipping verification would pass a listener serving anything at all.
     */
    private HttpClient tlsClient() {
        try {
            Path caFile = Path.of(property("fogwall.tls.ca", "FOGWALL_TLS_CA", "../docker/tls/ca.crt"));
            var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            try (var in = Files.newInputStream(caFile)) {
                var factory = CertificateFactory.getInstance("X.509");
                keyStore.setCertificateEntry("fogwall-test-ca", factory.generateCertificate(in));
            }
            var trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagers.init(keyStore);
            var context = SSLContext.getInstance("TLS");
            context.init(null, trustManagers.getTrustManagers(), null);
            return HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .sslContext(context)
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException(
                    "could not trust the SCM API listener's CA — run test/make-certs.sh, or set fogwall.tls.ca", e);
        }
    }

    /**
     * Whether the LDAP overlay is part of this stack, decided by whether its directory is listening.
     *
     * <p>Probing the socket, not a sign-in: an auth regression should fail the test, not turn it into a silent skip.
     */
    boolean ldapConfigured() {
        String hostPort = property("fogwall.ldap.host", "LDAP_HOST", "localhost:3389");
        int separator = hostPort.lastIndexOf(':');
        try (var socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(
                            hostPort.substring(0, separator), Integer.parseInt(hostPort.substring(separator + 1))),
                    2000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Signs in through the form login a browser uses and keeps the session.
     *
     * <p>A session rather than the API key, because the API key is always an admin and would prove nothing about the
     * directory. Empty when the credentials are refused.
     */
    Optional<Session> signIn(String username, String password) throws IOException, InterruptedException {
        var cookies = new CookieManager();
        var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        var form = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&password="
                + URLEncoder.encode(password, StandardCharsets.UTF_8);
        var request = HttpRequest.newBuilder(URI.create(fogwallUrl + "/login"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        // Spring Security answers a form login with a redirect either way; the destination is what differs.
        String location = response.headers().firstValue("location").orElse("");
        if (location.contains("error") || cookies.getCookieStore().getCookies().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Session(fogwallUrl, client, cookies));
    }

    /**
     * A signed-in browser session, for the calls that have to be made as a person rather than as an API key.
     *
     * <p>Writes carry the CSRF token back as a header, the way the dashboard's own frontend does: the server hands it
     * out in a readable cookie and requires it echoed. An API key request is exempt, so this is the only path that
     * exercises the check at all.
     */
    record Session(String baseUrl, HttpClient client, CookieManager cookies) {
        HttpResponse<String> get(String path) throws IOException, InterruptedException {
            var request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
            var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json");
            csrfToken().ifPresent(value -> builder.header("X-XSRF-TOKEN", value));
            return client.send(
                    builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        private Optional<String> csrfToken() {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()))
                    .map(HttpCookie::getValue)
                    .findFirst();
        }
    }

    /** The collector's Prometheus endpoint, where it re-exposes every metric it has received. */
    String collectorUrl() {
        return property("fogwall.otel.url", "OTEL_COLLECTOR_URL", "http://localhost:8889");
    }

    /** Jaeger, which the collector forwards traces to — the far end of the pipeline. */
    String jaegerUrl() {
        return property("fogwall.jaeger.url", "JAEGER_URL", "http://localhost:16686");
    }

    /** Whether the otel overlay is part of this stack, decided by whether the collector is answering. */
    boolean collectorReachable() {
        try {
            return plainGet(collectorUrl() + "/metrics").statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /** An unauthenticated GET, for the stack's other services — they have no notion of fogwall's API key. */
    HttpResponse<String> plainGet(String url) throws IOException, InterruptedException {
        return get(url);
    }

    /** A DELETE against fogwall's REST API authenticated with the stack's API key. */
    HttpResponse<String> apiDelete(String path) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(fogwallUrl + path))
                .timeout(TIMEOUT)
                .header("X-Api-Key", apiKey())
                .DELETE()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** A GET against fogwall's REST API authenticated with the stack's API key. */
    HttpResponse<String> api(String path) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(fogwallUrl + path))
                .timeout(TIMEOUT)
                .header("X-Api-Key", apiKey())
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** A POST against fogwall's REST API authenticated with the stack's API key. */
    HttpResponse<String> apiPost(String path, String body) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(fogwallUrl + path))
                .timeout(TIMEOUT)
                .header("X-Api-Key", apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String apiKey() {
        return System.getenv().getOrDefault("FOGWALL_API_KEY", "change-me-in-production");
    }

    private HttpResponse<String> get(String url) throws IOException, InterruptedException {
        var request =
                HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String basic(String user, String password) {
        return Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
