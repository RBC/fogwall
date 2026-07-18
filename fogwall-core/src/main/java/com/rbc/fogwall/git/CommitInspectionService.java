package com.rbc.fogwall.git;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

/**
 * Service for extracting commit information from a JGit repository. Provides utilities to get commit details, diffs,
 * and other git data using JGit primitives.
 */
@Slf4j
public class CommitInspectionService {

    /**
     * Extract commit details from a repository.
     *
     * @param repository The JGit repository
     * @param commitId The commit ID (SHA)
     * @return The commit details
     * @throws IOException If git operations fail
     */
    public static Commit getCommitDetails(Repository repository, String commitId) throws IOException {
        try (RevWalk revWalk = new RevWalk(repository)) {
            // Use "^{commit}" to dereference annotated tags to their target commit
            ObjectId objectId = repository.resolve(commitId + "^{commit}");
            if (objectId == null) {
                throw new IOException("Commit not found: " + commitId);
            }

            RevCommit revCommit = revWalk.parseCommit(objectId);
            return convertToCommit(revCommit);
        }
    }

    /**
     * Get a range of commits between two commit IDs.
     *
     * @param repository The JGit repository
     * @param fromCommit The starting commit (exclusive)
     * @param toCommit The ending commit (inclusive)
     * @return List of commits in the range
     * @throws IOException If git operations fail
     * @throws GitAPIException If git API operations fail
     */
    public static List<Commit> getCommitRange(Repository repository, String fromCommit, String toCommit)
            throws IOException, GitAPIException {
        List<Commit> commits = new ArrayList<>();

        try (Git git = new Git(repository)) {
            ObjectId fromId = repository.resolve(fromCommit);
            // Use "^{commit}" to dereference annotated tags to their target commit
            ObjectId toId = repository.resolve(toCommit + "^{commit}");

            if (toId == null) {
                throw new IOException("Commit not found: " + toCommit);
            }

            // Get commits from toCommit back to (but not including) fromCommit
            Iterable<RevCommit> revCommits;
            if (fromId != null && !isNullCommit(fromCommit)) {
                revCommits = git.log().addRange(fromId, toId).call();
            } else {
                // New branch - exclude commits reachable from any existing ref so we only
                // validate commits that are genuinely new in this push.  The local cache is a
                // bare clone, so existing branch tips live under refs/heads/ (not refs/remotes/).
                var logCmd = git.log().add(toId);
                Collection<Ref> existingRefs = repository.getRefDatabase().getRefsByPrefix("refs/heads/");
                for (Ref ref : existingRefs) {
                    if (ref.getObjectId() != null) {
                        logCmd.not(ref.getObjectId());
                    }
                }
                revCommits = logCmd.call();
            }

            for (RevCommit revCommit : revCommits) {
                commits.add(convertToCommit(revCommit));
            }
        }

        return commits;
    }

    /**
     * Returns all commits reachable from {@code toCommit} with no lower-bound exclusion. Used when a branch is being
     * re-pushed to S&F mode after a prior push was canceled or rejected — the local cache already has the branch tip
     * but the upstream has nothing, so the full ancestor chain must be enumerated.
     *
     * <p>Unlike {@link #getCommitRange}, this does NOT exclude commits reachable from existing local refs, so it
     * correctly returns commits that are cached locally but not yet forwarded upstream.
     */
    public static List<Commit> getCommitRangeUpTo(Repository repository, String toCommit)
            throws IOException, GitAPIException {
        List<Commit> commits = new ArrayList<>();
        try (Git git = new Git(repository)) {
            ObjectId toId = repository.resolve(toCommit + "^{commit}");
            if (toId == null) {
                throw new IOException("Commit not found: " + toCommit);
            }
            for (RevCommit revCommit : git.log().add(toId).call()) {
                commits.add(convertToCommit(revCommit));
            }
        }
        return commits;
    }

    /**
     * Get the diff between two commits.
     *
     * @param repository The JGit repository
     * @param fromCommit The starting commit
     * @param toCommit The ending commit
     * @return List of diff entries
     * @throws IOException If git operations fail
     */
    public static List<DiffEntry> getDiff(Repository repository, String fromCommit, String toCommit)
            throws IOException {
        try (ObjectReader reader = repository.newObjectReader()) {
            CanonicalTreeParser oldTreeParser = new CanonicalTreeParser();
            CanonicalTreeParser newTreeParser = new CanonicalTreeParser();

            // For new-branch pushes fromCommit is the all-zeros null object.  Rather than
            // diffing against an empty tree (which would show the entire repo snapshot and
            // trigger false-positive secret findings from existing files like package-lock.json),
            // find the parent of the oldest new commit so only the genuinely new content is scanned.
            ObjectId oldId = isNullCommit(fromCommit)
                    ? findNewBranchBase(repository, toCommit)
                    : repository.resolve(fromCommit + "^{tree}");
            ObjectId newId = repository.resolve(toCommit + "^{tree}");

            if (oldId != null) {
                oldTreeParser.reset(reader, oldId);
            }
            if (newId != null) {
                newTreeParser.reset(reader, newId);
            }

            try (Git git = new Git(repository)) {
                List<DiffEntry> diffs = git.diff()
                        .setOldTree(oldTreeParser)
                        .setNewTree(newTreeParser)
                        .call();

                RenameDetector rd = new RenameDetector(repository);
                rd.addAll(diffs);
                return rd.compute();
            } catch (GitAPIException e) {
                throw new IOException("Failed to get diff", e);
            }
        }
    }

    /**
     * Get a formatted diff string between two commits.
     *
     * @param repository The JGit repository
     * @param fromCommit The starting commit
     * @param toCommit The ending commit
     * @return The formatted diff as a string
     * @throws IOException If git operations fail
     */
    public static String getFormattedDiff(Repository repository, String fromCommit, String toCommit)
            throws IOException {
        return formatDiffEntries(repository, getDiff(repository, fromCommit, toCommit));
    }

    /**
     * Formats already-computed {@link DiffEntry} objects as unified diff text. Split out from {@link #getFormattedDiff}
     * so callers that already hold a {@code List<DiffEntry>} (e.g. {@link #forEachIntroducedCommit}) don't need to
     * recompute the diff a second time just to get formatted text.
     *
     * @param repository The JGit repository
     * @param diffs Diff entries to format, in order
     * @return The formatted diff as a string
     * @throws IOException If git operations fail
     */
    public static String formatDiffEntries(Repository repository, List<DiffEntry> diffs) throws IOException {
        StringBuilder diffText = new StringBuilder();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                DiffFormatter formatter = new DiffFormatter(out)) {
            formatter.setRepository(repository);
            for (DiffEntry diff : diffs) {
                formatter.format(diff);
                diffText.append(out.toString());
                out.reset();
            }
        }
        return diffText.toString();
    }

    /**
     * Walks each commit introduced by a ref update, in chronological order, and invokes {@code consumer} with that
     * commit's diff against its immediate parent — or against {@code baseCommit} for the oldest introduced commit,
     * which has no earlier commit within this push to diff against.
     *
     * <p>Skipped entirely for single-commit pushes: the aggregate diff (computed separately by callers) already covers
     * a single-commit push exactly, so a per-commit pass would only duplicate work for no additional coverage.
     *
     * <p>Content validation checks (diff-scan, binary-blob, etc.) must scan every introduced commit individually, not
     * just the aggregate old..new diff — otherwise content added in one commit and removed by a later commit in the
     * same push produces a clean aggregate diff while still having been present in the pushed history. This is the
     * "intermediate-commit smuggling" bypass (see RBC/fogwall#339).
     *
     * @param repository The JGit repository
     * @param baseCommit The commit the push started from (old id, or the effective upstream base)
     * @param tipCommit The commit the push ends at (new id)
     * @param consumer Invoked once per introduced commit, in chronological (oldest-first) order
     * @throws IOException If git operations fail
     * @throws GitAPIException If git API operations fail
     */
    public static void forEachIntroducedCommit(
            Repository repository, String baseCommit, String tipCommit, CommitDiffConsumer consumer)
            throws IOException, GitAPIException {
        List<Commit> commits = getCommitRange(repository, baseCommit, tipCommit);
        if (commits.size() <= 1) {
            return;
        }

        // getCommitRange returns newest-first; reverse for chronological traversal
        Collections.reverse(commits);

        for (Commit commit : commits) {
            String parent = commit.getParent() != null ? commit.getParent() : baseCommit;
            List<DiffEntry> diffs = getDiff(repository, parent, commit.getSha());
            consumer.accept(commit, diffs);
        }
    }

    /** Callback invoked once per commit by {@link #forEachIntroducedCommit}. */
    @FunctionalInterface
    public interface CommitDiffConsumer {
        void accept(Commit commit, List<DiffEntry> diffs) throws IOException;
    }

    /**
     * Check if a commit ID represents a null commit (all zeros).
     *
     * @param commitId The commit ID to check
     * @return true if the commit is a null commit
     */
    private static boolean isNullCommit(String commitId) {
        return commitId == null || commitId.matches("^0+$");
    }

    /**
     * For a new-branch push, find the tree that should be used as the diff base so that only the genuinely new content
     * is scanned. Walks commits reachable from {@code toCommit} that are NOT reachable from any existing
     * {@code refs/heads/*} branch tip, then returns the tree of the oldest such commit's first parent. Returns
     * {@code null} if the oldest new commit is a root commit (base is the empty tree).
     */
    private static ObjectId findNewBranchBase(Repository repository, String toCommit) throws IOException {
        // Use "^{commit}" to dereference annotated tags to their target commit
        ObjectId toId = repository.resolve(toCommit + "^{commit}");
        if (toId == null) return null;

        try (Git git = new Git(repository)) {
            var logCmd = git.log().add(toId);
            Collection<Ref> existingRefs = repository.getRefDatabase().getRefsByPrefix("refs/heads/");
            for (Ref ref : existingRefs) {
                if (ref.getObjectId() != null) logCmd.not(ref.getObjectId());
            }

            List<RevCommit> newCommits = new ArrayList<>();
            for (RevCommit c : logCmd.call()) {
                newCommits.add(c);
            }

            if (newCommits.isEmpty()) return null;

            // logCmd returns newest-first; last entry is the oldest new commit
            RevCommit oldest = newCommits.get(newCommits.size() - 1);
            if (oldest.getParentCount() == 0) return null; // root commit - empty tree is correct

            String parentSha = oldest.getParent(0).getName();
            return repository.resolve(parentSha + "^{tree}");
        } catch (GitAPIException e) {
            throw new IOException("Failed to find new-branch base commit", e);
        }
    }

    /**
     * Convert a JGit RevCommit to our Commit model.
     *
     * @param revCommit The JGit commit
     * @return Our Commit model
     */
    private static Commit convertToCommit(RevCommit revCommit) {
        PersonIdent author = revCommit.getAuthorIdent();
        PersonIdent committer = revCommit.getCommitterIdent();

        String parentSha = null;
        if (revCommit.getParentCount() > 0) {
            parentSha = revCommit.getParent(0).getName();
        }

        byte[] rawSig = revCommit.getRawGpgSignature();
        String signature = (rawSig != null && rawSig.length > 0) ? new String(rawSig, StandardCharsets.US_ASCII) : null;

        String fullMessage = revCommit.getFullMessage();
        List<String> signedOffBy = parseSignedOffBy(fullMessage);

        return Commit.builder()
                .sha(revCommit.getName())
                .parent(parentSha)
                .author(Contributor.builder()
                        .name(author.getName())
                        .email(author.getEmailAddress())
                        .build())
                .committer(Contributor.builder()
                        .name(committer.getName())
                        .email(committer.getEmailAddress())
                        .build())
                .message(fullMessage)
                .date(committer.getWhenAsInstant())
                .signature(signature)
                .signedOffBy(signedOffBy)
                .build();
    }

    /**
     * Extract all {@code Signed-off-by:} trailers from a commit message. Returns them in order of appearance,
     * preserving the full {@code Name <email>} value.
     */
    static List<String> parseSignedOffBy(String message) {
        if (message == null || message.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (String line : message.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.regionMatches(true, 0, "Signed-off-by:", 0, 14)) {
                String value = trimmed.substring(14).trim();
                if (!value.isEmpty()) {
                    result.add(value);
                }
            }
        }
        return result;
    }
}
