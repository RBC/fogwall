package com.rbc.fogwall.servlet.filter;

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
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.transport.PacketLineIn;

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
public class EnrichPushCommitsFilter extends AbstractProviderAwareFogwallFilter {

    private static final int ORDER = Integer.MIN_VALUE + 2; // Run after ParseGitRequestFilter
    private final LocalRepositoryCache repositoryCache;

    public EnrichPushCommitsFilter(FogwallProvider provider, LocalRepositoryCache repositoryCache) {
        super(ORDER, Set.of(HttpOperation.PUSH), provider);
        this.repositoryCache = repositoryCache;
    }

    public EnrichPushCommitsFilter(FogwallProvider provider, LocalRepositoryCache repositoryCache, String pathPrefix) {
        super(ORDER, Set.of(HttpOperation.PUSH), provider, pathPrefix);
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
            // filters can use it without needing their own LocalRepositoryCache reference.
            Repository repository = repositoryCache.getOrClone(remoteUrl);
            requestDetails.setLocalRepository(repository);

            // Step 2: Unpack the inflight push's pack data into the local clone.
            // The pushed objects don't exist upstream yet - this is the equivalent of
            // fogwall's writePack processor that pipes the request body into git receive-pack.
            unpackPushData(request, repository);

            log.debug("Extracting commits from {} to {}", fromCommit, toCommit);

            List<Commit> commits = CommitInspectionService.getCommitRange(repository, fromCommit, toCommit);

            if (commits.isEmpty()) {
                log.warn("No commits found in range {}..{} — erroring push", fromCommit, toCommit);
                requestDetails.setResult(GitRequestDetails.GitResult.ERROR);
                requestDetails.setReason("Push error: the proxy could not inspect any commits in this push. "
                        + "Please retry or contact your administrator.");
                return;
            }

            log.info("Extracted {} commits from repository", commits.size());
            requestDetails.getPushedCommits().addAll(commits);

        } catch (Exception e) {
            log.error("Failed to enrich push commits", e);
            requestDetails.setResult(GitRequestDetails.GitResult.ERROR);
            requestDetails.setReason("Push error: commit inspection failed (" + e.getMessage() + "). "
                    + "Please retry or contact your administrator.");
        }
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

    private String constructRemoteUrl(GitRequestDetails requestDetails) {
        String slug = requestDetails.getRepoRef().getSlug();
        String base = provider.getUri().toString();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + slug + ".git";
    }
}
