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
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.servlet.RequestBodyWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
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

    public EnrichPushCommitsFilter(FogwallProvider provider, LocalRepositoryCache repositoryCache) {
        super(ORDER, Set.of(HttpOperation.PUSH), provider);
        this.repositoryCache = repositoryCache;
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
            Repository repository = repositoryCache.getOrClone(remoteUrl, credentials, null, principal);
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
                    peeled = repository.resolve(toCommit + "^{commit}");
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
                List<Commit> commits = CommitInspectionService.getCommitRange(repository, fromCommit, peeledSha);
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
                }
                return;
            }

            log.debug("Extracting commits from {} to {}", fromCommit, toCommit);

            List<Commit> commits = CommitInspectionService.getCommitRange(repository, fromCommit, toCommit);

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
}
