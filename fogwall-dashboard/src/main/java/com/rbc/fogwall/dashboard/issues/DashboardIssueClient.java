package com.rbc.fogwall.dashboard.issues;

import com.rbc.fogwall.net.FogwallHttpExecutor;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.provider.ForgejoProvider;
import com.rbc.fogwall.provider.GitHubProvider;
import com.rbc.fogwall.provider.GitLabProvider;
import com.rbc.fogwall.scmapi.ScmApiUserAgent;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Performs issue writes (create, edit title/body, comment, close/reopen) against a provider on a user's behalf, using
 * the user's own linked OAuth token (passed per call). Unlike the SCM API (CLI) proxy, which relays a request the CLI
 * already built, this constructs the request itself — so it uses each provider's plain REST issue API
 * (owner/repo/number), not the GraphQL shape a specific CLI happens to send, and needs no node-ID or project-ID
 * resolution.
 *
 * <p>A dashboard-only concern: the standalone proxy server has no UI and manages no OAuth tokens, so this lives in the
 * dashboard module rather than {@code fogwall-core}. Supports the same providers as the proxy: GitHub, GitLab and
 * Forgejo/Gitea. The OAuth access token is sent as a bearer credential on every call.
 */
public class DashboardIssueClient {

    private static final JsonMapper MAPPER = new JsonMapper();

    /** Inline on a user action; bound so an upstream that stalls cannot hold the request thread indefinitely. */
    private static final Timeout TIMEOUT = Timeout.ofSeconds(15);

    /** The created or edited issue's number on the provider and its browsable web URL (for linking the user to it). */
    public record IssueResult(int number, String url) {}

    /** Non-2xx from the upstream (or an unreachable provider), carrying the status for auditing and a clean reason. */
    public static class IssueApiException extends RuntimeException {
        private final int upstreamStatus;

        public IssueApiException(int upstreamStatus, String message) {
            super(message);
            this.upstreamStatus = upstreamStatus;
        }

        public IssueApiException(int upstreamStatus, String message, Throwable cause) {
            super(message, cause);
            this.upstreamStatus = upstreamStatus;
        }

        /** The upstream HTTP status, or {@code 0} when the request never got a response. */
        public int upstreamStatus() {
            return upstreamStatus;
        }
    }

    public IssueResult createIssue(
            FogwallProvider provider, String owner, String repo, String title, String body, String token) {
        return switch (dialect(provider)) {
            case GITHUB ->
                asIssue(
                        post(githubIssuesUrl(provider, owner, repo), both(title, body, "title", "body"), token),
                        "number",
                        "html_url");
            case GITLAB ->
                asIssue(
                        post(gitlabIssuesUrl(provider, owner, repo), both(title, body, "title", "description"), token),
                        "iid",
                        "web_url");
            case FORGEJO ->
                asIssue(
                        post(forgejoIssuesUrl(provider, owner, repo), both(title, body, "title", "body"), token),
                        "number",
                        "html_url");
        };
    }

    public IssueResult editIssue(
            FogwallProvider provider, String owner, String repo, int number, String title, String body, String token) {
        return switch (dialect(provider)) {
            case GITHUB ->
                asIssue(
                        patch(
                                githubIssuesUrl(provider, owner, repo) + "/" + number,
                                present(title, body, "title", "body"),
                                token),
                        "number",
                        "html_url",
                        number);
            case GITLAB ->
                asIssue(
                        put(
                                gitlabIssuesUrl(provider, owner, repo) + "/" + number,
                                present(title, body, "title", "description"),
                                token),
                        "iid",
                        "web_url",
                        number);
            case FORGEJO ->
                asIssue(
                        patch(
                                forgejoIssuesUrl(provider, owner, repo) + "/" + number,
                                present(title, body, "title", "body"),
                                token),
                        "number",
                        "html_url",
                        number);
        };
    }

    public IssueResult comment(
            FogwallProvider provider, String owner, String repo, int number, String body, String token) {
        Dialect dialect = dialect(provider);
        String url =
                switch (dialect) {
                    case GITHUB -> githubIssuesUrl(provider, owner, repo) + "/" + number + "/comments";
                    case GITLAB -> gitlabIssuesUrl(provider, owner, repo) + "/" + number + "/notes";
                    case FORGEJO -> forgejoIssuesUrl(provider, owner, repo) + "/" + number + "/comments";
                };
        JsonNode r = post(url, Map.of("body", body), token);
        String urlField = dialect == Dialect.GITLAB ? "web_url" : "html_url";
        return new IssueResult(number, r.path(urlField).asString(""));
    }

    public IssueResult setState(
            FogwallProvider provider, String owner, String repo, int number, boolean close, String token) {
        return switch (dialect(provider)) {
            case GITHUB ->
                asIssue(
                        patch(
                                githubIssuesUrl(provider, owner, repo) + "/" + number,
                                Map.of("state", close ? "closed" : "open"),
                                token),
                        "number",
                        "html_url",
                        number);
            case GITLAB ->
                asIssue(
                        put(
                                gitlabIssuesUrl(provider, owner, repo) + "/" + number,
                                Map.of("state_event", close ? "close" : "reopen"),
                                token),
                        "iid",
                        "web_url",
                        number);
            case FORGEJO ->
                asIssue(
                        patch(
                                forgejoIssuesUrl(provider, owner, repo) + "/" + number,
                                Map.of("state", close ? "closed" : "open"),
                                token),
                        "number",
                        "html_url",
                        number);
        };
    }

    private enum Dialect {
        GITHUB,
        GITLAB,
        FORGEJO
    }

    private static Dialect dialect(FogwallProvider provider) {
        if (provider instanceof GitHubProvider) return Dialect.GITHUB;
        if (provider instanceof GitLabProvider) return Dialect.GITLAB;
        if (provider instanceof ForgejoProvider) return Dialect.FORGEJO;
        throw new IssueApiException(
                0, "provider '" + provider.getName() + "' does not support dashboard issue operations");
    }

    private static IssueResult asIssue(JsonNode r, String numberField, String urlField) {
        return new IssueResult(r.path(numberField).asInt(), r.path(urlField).asString(""));
    }

    private static IssueResult asIssue(JsonNode r, String numberField, String urlField, int fallbackNumber) {
        return new IssueResult(
                r.path(numberField).asInt(fallbackNumber), r.path(urlField).asString(""));
    }

    private static String githubIssuesUrl(FogwallProvider p, String owner, String repo) {
        return ((GitHubProvider) p).getApiUrl() + "/repos/" + owner + "/" + repo + "/issues";
    }

    private static String forgejoIssuesUrl(FogwallProvider p, String owner, String repo) {
        return ((ForgejoProvider) p).getApiUrl() + "/repos/" + owner + "/" + repo + "/issues";
    }

    private static String gitlabIssuesUrl(FogwallProvider p, String owner, String repo) {
        String projectId = URLEncoder.encode(owner + "/" + repo, StandardCharsets.UTF_8);
        return ((GitLabProvider) p).getApiUrl() + "/projects/" + projectId + "/issues";
    }

    /** Both fields, always present (create). */
    private static Map<String, String> both(String a, String b, String keyA, String keyB) {
        var m = new LinkedHashMap<String, String>();
        m.put(keyA, a);
        m.put(keyB, b);
        return m;
    }

    /** Only the non-null fields (edit — an omitted field is left unchanged upstream). */
    private static Map<String, String> present(String a, String b, String keyA, String keyB) {
        var m = new LinkedHashMap<String, String>();
        if (a != null) m.put(keyA, a);
        if (b != null) m.put(keyB, b);
        return m;
    }

    private JsonNode post(String url, Map<String, String> body, String token) {
        return send(Request.post(url), body, token);
    }

    private JsonNode patch(String url, Map<String, String> body, String token) {
        return send(Request.patch(url), body, token);
    }

    private JsonNode put(String url, Map<String, String> body, String token) {
        return send(Request.put(url), body, token);
    }

    private JsonNode send(Request request, Map<String, String> body, String token) {
        try {
            String json = MAPPER.writeValueAsString(body);
            request.addHeader("Authorization", "Bearer " + token)
                    .addHeader("Accept", "application/json")
                    .bodyString(json, ContentType.APPLICATION_JSON)
                    .connectTimeout(TIMEOUT)
                    .responseTimeout(TIMEOUT);
            ScmApiUserAgent.self(request);
            try (ClassicHttpResponse response = (ClassicHttpResponse)
                    request.execute(FogwallHttpExecutor.instance()).returnResponse()) {
                int status = response.getCode();
                String responseBody = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
                if (status < 200 || status >= 300) {
                    throw new IssueApiException(status, "the provider returned HTTP " + status);
                }
                return responseBody.isEmpty() ? MAPPER.createObjectNode() : MAPPER.readTree(responseBody);
            }
        } catch (IssueApiException e) {
            throw e;
        } catch (Exception e) {
            throw new IssueApiException(0, "could not reach the provider: " + e.getMessage(), e);
        }
    }
}
