package com.rbc.fogwall.git;

import com.rbc.fogwall.net.FogwallHttpExecutor;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.util.Timeout;

/**
 * Answers one question about an upstream repository: does reading it require credentials?
 *
 * <p>Store-and-forward has to challenge a developer for credentials before it can forward them upstream, but a blanket
 * challenge on every fetch makes genuinely public repositories unclonable by anyone who has no credential to offer —
 * and worse, providers such as GitHub reject an <em>invalid</em> {@code Authorization} header on a public repository
 * that they would have served anonymously. Asking upstream first means the challenge follows the repository's actual
 * visibility rather than a guess.
 *
 * <p>The probe is the git smart-HTTP advertisement itself ({@code GET <repo>/info/refs?service=git-upload-pack}), sent
 * with no credentials. Every git server answers it: {@code 200} means anonymous reads are served,
 * {@code 401}/{@code 403} mean they are not. That keeps this provider-agnostic — no REST API, no per-provider
 * visibility field.
 *
 * <p><b>Cost.</b> Only an <em>unauthenticated</em> fetch reaches here; a request that already carries an
 * {@code Authorization} header is passed straight through and never probes. Results are cached per repository for
 * {@link #DEFAULT_TTL}, so a repository is probed at most once per window however many anonymous clones arrive.
 *
 * <p><b>Failure is a challenge.</b> Anything other than a clear {@code 200} — a timeout, a 404, a 5xx — is treated as
 * "credentials required", which is the behaviour that existed before this class. A probe that cannot reach upstream
 * must never be the reason a repository becomes anonymously readable.
 */
@Slf4j
public class UpstreamAuthProbe {

    /**
     * How long a verdict is reused. Long enough that a burst of clones costs one probe, short enough that a repository
     * made private is not treated as public for long. Staleness here cannot expose private content on its own: a
     * repository wrongly believed public is fetched anonymously from upstream, which then fails.
     */
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private static final Timeout PROBE_TIMEOUT = Timeout.ofSeconds(10);

    private final Duration ttl;
    private final ConcurrentHashMap<String, Verdict> cache = new ConcurrentHashMap<>();

    private record Verdict(boolean requiresAuth, long recordedAt) {}

    public UpstreamAuthProbe() {
        this(DEFAULT_TTL);
    }

    public UpstreamAuthProbe(Duration ttl) {
        this.ttl = ttl;
    }

    /**
     * Whether {@code upstreamRepoUrl} refuses anonymous reads, and therefore whether the client should be challenged
     * for credentials to forward.
     *
     * @param upstreamRepoUrl the clean upstream repository URL, e.g. {@code https://github.com/owner/repo.git}
     */
    public boolean requiresAuthentication(String upstreamRepoUrl) {
        Verdict cached = cache.get(upstreamRepoUrl);
        if (cached != null && !isExpired(cached)) {
            return cached.requiresAuth();
        }
        boolean requiresAuth = probe(upstreamRepoUrl);
        cache.put(upstreamRepoUrl, new Verdict(requiresAuth, System.currentTimeMillis()));
        return requiresAuth;
    }

    private boolean isExpired(Verdict verdict) {
        return System.currentTimeMillis() - verdict.recordedAt() > ttl.toMillis();
    }

    /** Asks upstream directly, bypassing the cache. Overridable so tests can exercise caching without a network. */
    protected boolean probe(String upstreamRepoUrl) {
        String url = upstreamRepoUrl + "/info/refs?service=git-upload-pack";
        try {
            int status = Request.get(url)
                    .connectTimeout(PROBE_TIMEOUT)
                    .responseTimeout(PROBE_TIMEOUT)
                    .execute(FogwallHttpExecutor.instance())
                    .handleResponse(response -> response.getCode());

            if (status == HttpStatus.SC_OK) {
                log.debug("Upstream {} serves anonymous reads; not challenging", upstreamRepoUrl);
                return false;
            }
            log.debug("Upstream {} answered {} to an anonymous read; challenging", upstreamRepoUrl, status);
            return true;
        } catch (Exception e) {
            // Fail closed: challenge, exactly as fogwall did before probing existed.
            log.debug("Could not probe {} for anonymous readability; challenging", upstreamRepoUrl, e);
            return true;
        }
    }
}
