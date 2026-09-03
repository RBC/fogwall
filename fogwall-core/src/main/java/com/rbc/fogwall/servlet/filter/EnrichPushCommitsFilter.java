package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.git.GitClientUtils.AnsiColor.RED;
import static com.rbc.fogwall.git.GitClientUtils.SymbolCodes.NO_ENTRY;
import static com.rbc.fogwall.git.GitClientUtils.format;
import static com.rbc.fogwall.git.GitClientUtils.sym;
import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;

import com.rbc.fogwall.git.Commit;
import com.rbc.fogwall.git.CommitInspectionService;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.git.LocalRepositoryCache;
import com.rbc.fogwall.git.QuarantineObjectStore;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.servlet.RequestBodyWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.transport.PacketLineIn;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

/**
 * Filter that enriches push requests with full commit information. Replicates fogwall's {@code writePack} approach:
 *
 * <ol>
 *   <li>Clone/fetch the upstream repo into a local cache
 *   <li>Unpack the inflight push's pack data into the local clone (the objects don't exist upstream yet)
 *   <li>Use {@link CommitInspectionService} to walk the commit range with full details
 * </ol>
 *
 * <p>This gives downstream filters (author email, commit message, etc.) access to full commit metadata - author,
 * message, signature - rather than just the basic SHA/ref from the packet line header.
 */
@Slf4j
public class EnrichPushCommitsFilter extends ProviderAwareFogwallFilter<FogwallProvider> {

    // Order 60 — runs after AllowApprovedPushFilter (50) so that re-pushes of approved pushes are
    // short-circuited by FogwallFilter before enrichment runs again. Must stay before content
    // validation filters (200+) which depend on localRepository and pushedCommits being set.
    private static final int ORDER = 60;
    private final LocalRepositoryCache repositoryCache;
    private final long maxObjectSizeBytes;

    public EnrichPushCommitsFilter(FogwallProvider provider, LocalRepositoryCache repositoryCache) {
        this(provider, repositoryCache, 0);
    }

    /** @param maxObjectSizeBytes largest decompressed size of any single pushed object; 0 = unlimited */
    public EnrichPushCommitsFilter(
            FogwallProvider provider, LocalRepositoryCache repositoryCache, long maxObjectSizeBytes) {
        super(ORDER, Set.of(HttpOperation.PUSH), provider);
        this.repositoryCache = repositoryCache;
        this.maxObjectSizeBytes = maxObjectSizeBytes;
    }

    /**
     * Wraps the rest of the chain so the quarantine opened here outlives the filters that read from it — content
     * validation runs downstream of this filter — but never outlives the request.
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            super.doFilter(request, response, chain);
        } finally {
            if (request.getAttribute(QuarantineObjectStore.REQUEST_ATTRIBUTE)
                    instanceof QuarantineObjectStore quarantine) {
                request.removeAttribute(QuarantineObjectStore.REQUEST_ATTRIBUTE);
                quarantine.close();
            }
        }
    }

    @Override
    public void doHttpFilter(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var requestDetails = (GitRequestDetails) request.getAttribute(GIT_REQUEST_ATTR);
        if (requestDetails == null) {
            log.warn("GitRequestDetails not found in request attributes");
            return;
        }

        // Use the packet line SHAs (the ref update range), not the pack data's commit parent
        String fromCommit = requestDetails.getCommitFrom();
        String toCommit = requestDetails.getCommitTo();
        if (toCommit == null || toCommit.isEmpty()) {
            log.debug("No commit range available from packet line");
            return;
        }

        // Ref deletions have no new objects to inspect — skip enrichment entirely.
        if (requestDetails.isRefDeletion()) {
            log.debug("Ref deletion push — skipping commit enrichment");
            return;
        }

        try {
            String remoteUrl = constructRemoteUrl(requestDetails);
            log.info("Enriching push commits from repository: {}", remoteUrl);

            // Step 1: Get or clone the upstream repo, then publish it on the request so downstream
            // filters can use it without needing their own LocalRepositoryCache reference. The
            // pushing client's own credentials are reused for the upstream clone/fetch so that
            // private repos can be inspected — without them this silently degrades to an
            // anonymous clone/fetch, which fails for any private repo.
            //
            // The principal must be supplied alongside the credentials: the cache's fetch cooldown is
            // keyed by (repository, principal), and passing credentials without an identity would leave
            // every proxy caller sharing the anonymous entry — making one user's successful fetch
            // satisfy the next user's request, which is the transferability the cooldown keying exists
            // to prevent. Built from the full credential, not the username, because providers such as
            // GitHub ignore the HTTP Basic username entirely.
            String[] userPass = extractBasicAuth(request);
            CredentialsProvider credentials =
                    userPass == null ? null : new UsernamePasswordCredentialsProvider(userPass[0], userPass[1]);
            String principal = userPass == null ? null : userPass[0] + ":" + userPass[1];
            Repository mirror = repositoryCache.getOrClone(remoteUrl, credentials, null, principal);

            // Everything this push writes goes to a scratch store that is deleted when the request ends, so
            // content fogwall rejects never lands in the shared mirror. Reads still see the mirror, which is
            // registered as an alternate. If the quarantine can't be opened, fall back to the mirror rather
            // than failing the push: the loss is disk hygiene, not a validation result.
            Repository repository = mirror;
            QuarantineObjectStore quarantine = QuarantineObjectStore.createOrNull(
                    mirror,
                    requestDetails.getId() != null ? requestDetails.getId().toString() : null);
            if (quarantine != null) {
                request.setAttribute(QuarantineObjectStore.REQUEST_ATTRIBUTE, quarantine);
                repository = quarantine.getRepository();
            }
            requestDetails.setLocalRepository(repository);

            // Step 2: Unpack the inflight push's pack data into the local clone.
            // The pushed objects don't exist upstream yet - this is the equivalent of
            // fogwall's writePack processor that pipes the request body into git receive-pack.
            unpackPushData(request, repository);

            if (requestDetails.isTagPush()) {
                // Peel the tag ref to its target commit so CheckHiddenCommitsFilter can detect
                // any commits smuggled in the pack (see #337). An empty range is normal — the
                // tag's commits were already validated when the branch was pushed. A non-empty
                // range means new commits arrived exclusively via a tag ref, bypassing branch
                // validation — reject outright.
                ObjectId peeled;
                try {
                    peeled = peelRefreshingIfStale(repository, toCommit, remoteUrl, credentials, principal);
                } catch (Exception e) {
                    log.warn(
                            "Tag push ({}) — could not peel {} to a commit: {}",
                            requestDetails.getBranch(),
                            toCommit,
                            e.getMessage());
                    errorAndSendError(
                            requestDetails,
                            request,
                            response,
                            "Push rejected: tag object could not be resolved. Ensure the tag object is included in"
                                    + " the push.");
                    return;
                }
                if (peeled == null) {
                    log.warn("Tag push ({}) — {} did not resolve to a commit", requestDetails.getBranch(), toCommit);
                    errorAndSendError(
                            requestDetails,
                            request,
                            response,
                            "Push rejected: tag object could not be resolved. Ensure the tag object is included in"
                                    + " the push.");
                    return;
                }
                String peeledSha = peeled.getName();
                log.info("Tag push ({}) — peeled {} to commit {}", requestDetails.getBranch(), toCommit, peeledSha);
                List<Commit> commits = commitRangeRefreshingIfStale(
                        repository, fromCommit, peeledSha, remoteUrl, credentials, principal);
                if (!commits.isEmpty()) {
                    // A non-empty range has three possible explanations, and only one of them is smuggling:
                    //   1. the commits really did arrive via this tag, bypassing branch validation;
                    //   2. the mirror has not re-fetched since they were forwarded upstream (fetch cooldown);
                    //   3. the mirror is a shallow clone and they sit beyond its boundary.
                    // The walk excludes commits reachable from refs/heads/* *in the mirror*, so (2) and (3) look
                    // exactly like (1). Tagging a commit that has been upstream for months is an ordinary release
                    // action and must not be reported as smuggling, so eliminate (2) and (3) — refresh and deepen —
                    // before concluding (1). This costs a fetch only on the path that was about to reject.
                    log.info(
                            "Tag push ({}) — {} commit(s) look unvalidated; refreshing mirror before deciding",
                            requestDetails.getBranch(),
                            commits.size());
                    repositoryCache.refreshNow(remoteUrl, credentials, null, principal);
                    commits = CommitInspectionService.getCommitRange(repository, fromCommit, peeledSha);
                }
                if (!commits.isEmpty()) {
                    log.warn(
                            "Tag push {} introduces {} unvalidated commit(s) — rejecting",
                            requestDetails.getBranch(),
                            commits.size());
                    errorAndSendError(
                            requestDetails,
                            request,
                            response,
                            "Push rejected: the tag references commits that were not validated through a branch"
                                    + " push. Push the branch first, then re-create the tag.");
                    return;
                }

                // The tag's target commit was validated when its branch was pushed; the tag's own annotation message
                // was not. Expose it so the message-content filters (checkCommitMessages, scanContentPatternsMessages)
                // validate it exactly as they do a commit message (#474). Lightweight tags carry no message.
                ObjectId tagObjectId = repository.resolve(toCommit);
                CommitInspectionService.getAnnotatedTagMessage(repository, tagObjectId)
                        .ifPresent(requestDetails::setTagMessage);
                return;
            }

            log.debug("Extracting commits from {} to {}", fromCommit, toCommit);

            List<Commit> commits =
                    commitRangeRefreshingIfStale(repository, fromCommit, toCommit, remoteUrl, credentials, principal);

            if (commits.isEmpty()) {
                // Not an inspection failure: the walk succeeded and the range is genuinely empty, which is
                // what a branch pushed with no new commits looks like. Leave the result untouched and let
                // CheckEmptyBranchFilter report it — it names the condition accurately and its rejection
                // carries the push-record link. Claiming it here as an error would replace a precise
                // message with a vague one and drop that link.
                log.debug(
                        "No commits in range {}..{} — leaving the empty-branch check to report it",
                        fromCommit,
                        toCommit);
                return;
            }

            log.info("Extracted {} commits from repository", commits.size());
            requestDetails.getPushedCommits().addAll(commits);

        } catch (Exception e) {
            log.error("Failed to enrich push commits", e);
            errorAndSendError(
                    requestDetails,
                    request,
                    response,
                    "Push error: commit inspection failed (" + e.getMessage() + "). Please retry or contact your"
                            + " administrator.");
        }
    }

    /**
     * Mark the push as {@link GitRequestDetails.GitResult#ERROR} and commit an error response to the client.
     *
     * <p>Forwarding is not the risk here: {@link PushFinalizerFilter} returns early on {@code ERROR} without changing
     * the result, and {@code FogwallServlet.service()} proxies only when the result is {@code ALLOWED}, so an errored
     * push is never sent upstream whether or not a response was written. The reason for committing a response is that
     * nothing else will — the remaining filters skip an errored push, the finalizer returns without writing, and the
     * proxy servlet declines to run — leaving the client with an empty reply and no explanation of why the push failed.
     * Writing it here is what turns a silent failure into a diagnosable one.
     */
    private void errorAndSendError(
            GitRequestDetails requestDetails, HttpServletRequest request, HttpServletResponse response, String reason)
            throws IOException {
        requestDetails.setResult(GitRequestDetails.GitResult.ERROR);
        requestDetails.setReason(reason);
        String title = sym(NO_ENTRY) + "  Push Blocked - Commit Inspection Failed";
        sendGitError(request, response, format(title, reason, RED, null));
    }

    /**
     * Unpack the push's pack data from the cached request body into the local repository. The request body contains git
     * protocol packet lines followed by pack data (starting with the "PACK" signature). We extract the pack portion and
     * feed it to JGit's {@link PackParser} to insert the objects into the local object store.
     *
     * <p>This is the JGit equivalent of fogwall's {@code writePack} processor which runs {@code git receive-pack} with
     * the request body as stdin.
     */
    private void unpackPushData(HttpServletRequest request, Repository repository) throws IOException {
        byte[] body = getRequestBody(request);
        if (body == null || body.length == 0) {
            log.debug("No request body to unpack");
            return;
        }

        // Walk past pkt-lines to find the PACK data boundary
        int packOffset = findPackDataOffset(body);
        if (packOffset < 0) {
            log.debug("No PACK signature found in request body");
            return;
        }

        log.debug("Found PACK data at offset {} ({} bytes)", packOffset, body.length - packOffset);

        // Unpack the objects into the local repo's object store
        try (ObjectInserter inserter = repository.newObjectInserter()) {
            PackParser parser =
                    inserter.newPackParser(new ByteArrayInputStream(body, packOffset, body.length - packOffset));
            parser.setAllowThin(true); // Allow thin packs (deltas against objects already in the repo)
            // The wire size was already capped at parse; this bounds what those bytes may inflate to.
            // A violation throws from parse() and surfaces as a commit-inspection error to the client.
            if (maxObjectSizeBytes > 0) {
                parser.setMaxObjectSizeLimit(maxObjectSizeBytes);
            }
            parser.parse(NullProgressMonitor.INSTANCE);
            inserter.flush();
            log.debug("Successfully unpacked push objects into local repository");
        }
    }

    /** Extract the cached request body from the {@link RequestBodyWrapper}. */
    private byte[] getRequestBody(HttpServletRequest request) {
        if (request instanceof RequestBodyWrapper) {
            return ((RequestBodyWrapper) request).getBody();
        }
        // Try unwrapping
        if (request instanceof jakarta.servlet.http.HttpServletRequestWrapper wrapper) {
            var wrapped = wrapper.getRequest();
            if (wrapped instanceof RequestBodyWrapper bodyWrapper) {
                return bodyWrapper.getBody();
            }
        }
        log.warn("Request is not a RequestBodyWrapper - cannot extract cached body");
        return null;
    }

    /**
     * Find the byte offset of the PACK signature in a git receive-pack request body. Uses JGit's {@link PacketLineIn}
     * to walk pkt-line framing, which prevents CVE-2025-54584 (a crafted ref name containing "PACK" could otherwise
     * fool a naive byte scan).
     *
     * @return byte offset of the PACK signature, or -1 if not found
     */
    private static int findPackDataOffset(byte[] data) {
        if (data == null || data.length < 4) return -1;
        if (data[0] == 'P' && data[1] == 'A' && data[2] == 'C' && data[3] == 'K') return 0;
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        PacketLineIn pli = new PacketLineIn(bais);
        try {
            while (true) {
                String line = pli.readString();
                if (PacketLineIn.isEnd(line)) break;
            }
        } catch (IOException e) {
            return -1;
        }
        int pos = data.length - bais.available();
        if (pos + 4 <= data.length
                && data[pos] == 'P'
                && data[pos + 1] == 'A'
                && data[pos + 2] == 'C'
                && data[pos + 3] == 'K') {
            return pos;
        }
        return -1;
    }

    /**
     * Extract the pushing client's HTTP Basic credentials as {@code [username, secret]}, or {@code null} when the
     * request carries none. Returns the parts rather than a {@link CredentialsProvider} so the caller can derive both
     * the provider and the cache principal from a single parse of the header.
     *
     * <p>Decoded as UTF-8 explicitly: the platform default charset would make the outcome of authentication depend on
     * the JVM's locale for any non-ASCII byte in the secret.
     */
    private static String[] extractBasicAuth(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) return null;
        try {
            String decoded = new String(
                    Base64.getDecoder()
                            .decode(authHeader.substring("Basic ".length()).trim()),
                    StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            if (colon < 0) return null;
            return new String[] {decoded.substring(0, colon), decoded.substring(colon + 1)};
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String constructRemoteUrl(GitRequestDetails requestDetails) {
        String slug = requestDetails.getRepoRef().getSlug();
        String base = provider.getUri().toString();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + slug + ".git";
    }

    /**
     * Walks the commit range, refreshing the mirror once if an object the walk needs is not there.
     *
     * <p>{@code fromCommit} is the ref's current tip upstream, and the mirror can legitimately be behind it: the fetch
     * cooldown may not have expired since the previous push was forwarded, and the mirror is a shallow clone whose
     * boundary may sit in front of it. Neither means the push is bad. Refreshing deepens the mirror to full history, so
     * a push whose parent genuinely does not exist upstream still fails — one retry later.
     */
    private List<Commit> commitRangeRefreshingIfStale(
            Repository repository,
            String fromCommit,
            String toCommit,
            String remoteUrl,
            CredentialsProvider credentials,
            String principal)
            throws Exception {
        try {
            return CommitInspectionService.getCommitRange(repository, fromCommit, toCommit);
        } catch (MissingObjectException e) {
            log.info(
                    "Commit range {}..{} is missing {}; refreshing the mirror before failing",
                    fromCommit,
                    toCommit,
                    e.getObjectId() != null ? e.getObjectId().name() : "an object");
            repositoryCache.refreshNow(remoteUrl, credentials, null, principal);
            return CommitInspectionService.getCommitRange(repository, fromCommit, toCommit);
        }
    }

    /**
     * Peels {@code toCommit} to a commit, refreshing the mirror once if it cannot be found.
     *
     * <p>A tag usually points at a commit that is already upstream, and after the push that introduced it the mirror
     * may not have re-fetched yet — the cooldown is measured in seconds and a tag often follows its branch immediately.
     * Unlike the commit walk, an object the mirror has never seen surfaces here as {@code resolve} returning
     * {@code null} rather than as an exception, so it needs its own retry. A tag whose target genuinely is not upstream
     * still fails, one refresh later.
     */
    private ObjectId peelRefreshingIfStale(
            Repository repository, String toCommit, String remoteUrl, CredentialsProvider credentials, String principal)
            throws Exception {
        try {
            ObjectId peeled = repository.resolve(toCommit + "^{commit}");
            if (peeled != null) return peeled;
            log.info("Tag {} did not peel to a known commit; refreshing the mirror before failing", toCommit);
        } catch (IOException e) {
            // A target the mirror has never seen surfaces either way depending on how far the peel got: as a
            // null result, or as a missing-object failure part-way through. Both mean the same thing here.
            log.info("Tag {} could not be peeled ({}); refreshing the mirror before failing", toCommit, e.getMessage());
        }
        repositoryCache.refreshNow(remoteUrl, credentials, null, principal);
        return repository.resolve(toCommit + "^{commit}");
    }
}
