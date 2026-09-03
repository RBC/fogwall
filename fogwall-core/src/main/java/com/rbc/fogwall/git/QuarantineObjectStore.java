package com.rbc.fogwall.git;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/**
 * A scratch object store for one push, discarded when the request ends.
 *
 * <p>Objects arriving on a push have to be readable before fogwall can decide anything about them, but writing them
 * straight into the shared mirror means a rejected push leaves its content there permanently — including the content
 * policy just refused. This gives the request somewhere else to put them.
 *
 * <p>The trick is that the returned {@link Repository} shares the mirror's git directory, so it sees the mirror's refs
 * and can still answer "what does this push actually introduce", while its object directory points at a temporary
 * directory so every write lands there instead. The mirror's own object directory is registered as an alternate, which
 * is what lets thin-pack deltas resolve against objects already in the mirror.
 *
 * <p>The transparent proxy never promotes anything back into the mirror: objects from an accepted push reach it the
 * same way everything else does, by being fetched from upstream once they exist there, which keeps the mirror a
 * reflection of upstream rather than an accumulation of everything anyone attempted. Server mode has to promote,
 * because JGit applies its ref updates to the shared git directory before this store is discarded — see
 * {@link #promote()}.
 */
@Slf4j
public final class QuarantineObjectStore implements Closeable {

    /** Where a request parks its quarantine so whatever owns the request lifecycle can tear it down. */
    public static final String REQUEST_ATTRIBUTE = "com.rbc.fogwall.quarantine";

    @Getter
    private final Repository repository;

    /** The scratch directory backing this store. Exists only while the store is open. */
    @Getter
    private final Path directory;

    private final File mirrorObjects;

    private QuarantineObjectStore(Repository repository, Path directory, File mirrorObjects) {
        this.repository = repository;
        this.directory = directory;
        this.mirrorObjects = mirrorObjects;
    }

    /**
     * Opens a quarantine backed by {@code mirror}.
     *
     * @throws IOException if the temporary directory or repository cannot be created
     */
    public static QuarantineObjectStore create(Repository mirror) throws IOException {
        return create(mirror, null);
    }

    /**
     * Opens a quarantine named after {@code pushId}, so a directory on disk can be tied back to the push record it
     * belongs to without guesswork. Falls back to a random name when no id is available.
     *
     * @throws IOException if the temporary directory or repository cannot be created
     */
    public static QuarantineObjectStore create(Repository mirror, String pushId) throws IOException {
        String prefix = pushId != null && !pushId.isBlank()
                ? "fogwall-quarantine-" + sanitize(pushId) + "-"
                : "fogwall-quarantine-";
        Path dir = Files.createTempDirectory(prefix);
        try {
            Path objects = Files.createDirectories(dir.resolve("objects"));
            Files.createDirectories(objects.resolve("pack"));

            File mirrorObjects = new File(mirror.getDirectory(), Constants.OBJECTS);
            Repository repo = new FileRepositoryBuilder()
                    .setGitDir(mirror.getDirectory())
                    .setObjectDirectory(objects.toFile())
                    .addAlternateObjectDirectory(mirrorObjects)
                    .build();

            log.debug("Opened quarantine {} for mirror {}", dir, mirror.getDirectory());
            return new QuarantineObjectStore(repo, dir, mirrorObjects);
        } catch (IOException | RuntimeException e) {
            deleteTree(dir);
            throw e;
        }
    }

    /**
     * Opens a quarantine, or returns {@code null} if one cannot be created.
     *
     * <p>Callers fall back to writing into the mirror. That is the pre-quarantine behaviour, and failing the push
     * instead would trade a disk-hygiene problem for an outage — no validation result depends on which store the
     * objects landed in.
     */
    public static QuarantineObjectStore createOrNull(Repository mirror) {
        return createOrNull(mirror, null);
    }

    /** As {@link #createOrNull(Repository)}, naming the directory after the push record's id. */
    public static QuarantineObjectStore createOrNull(Repository mirror, String pushId) {
        try {
            return create(mirror, pushId);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not open a quarantine store; unpacking into the shared mirror instead", e);
            return null;
        }
    }

    /**
     * Promotes everything this push wrote into the mirror.
     *
     * <p>Only server mode needs this. There JGit applies the ref updates to the shared git directory once the
     * pre-receive hooks pass, so the objects those refs name have to be in the mirror by then or it would be left
     * pointing at objects that are about to be deleted. Call it only after every check has passed — promoting is what
     * makes a push permanent. The transparent proxy never applies ref updates and so never promotes.
     *
     * <p>Object files are content-addressed and immutable, so a name that already exists in the mirror holds the same
     * bytes and is skipped rather than overwritten. Pack indexes go last: an {@code .idx} without its {@code .pack} is
     * the one ordering a concurrent reader must never see.
     *
     * <p>{@code .keep} files are deliberately left behind. A push large enough to be written as a pack gets one
     * alongside it — JGit's pack lock, which stops the pack being collected while it is still in use — and
     * {@code ReceivePack.release()} deletes it by path once the push finishes. Promoting it moves it out from under
     * that delete, which throws after the response has already begun and leaves the client's {@code git push} waiting
     * forever. It is a lock marker rather than an object, so the mirror has no use for it.
     *
     * <p>Moving the files is safe even though this store's object database is still open and
     * {@code ForwardingPostReceiveHook} reads through it afterwards to build the upstream push. The mirror's object
     * directory is registered as an alternate, so a pack that has moved there is still reachable from this store — the
     * reader finds it at its new location rather than losing it.
     */
    public void promote() throws IOException {
        Path from = directory.resolve(Constants.OBJECTS);
        Path to = mirrorObjects.toPath();
        if (!Files.isDirectory(from)) return;

        List<Path> files;
        try (var walk = Files.walk(from)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(f -> !f.getFileName().toString().endsWith(".keep"))
                    .sorted(Comparator.comparing(f -> f.getFileName().toString().endsWith(".idx")))
                    .toList();
        }
        for (Path file : files) {
            Path target = to.resolve(from.relativize(file).toString());
            if (Files.exists(target)) continue;
            Files.createDirectories(target.getParent());
            Files.move(file, target);
        }
        log.debug("Promoted {} object file(s) from {} into {}", files.size(), from, to);
    }

    /** Closes the repository and deletes everything the push wrote. Safe to call more than once. */
    @Override
    public void close() {
        try {
            repository.close();
        } catch (RuntimeException e) {
            log.warn("Failed to close quarantine repository {}", directory, e);
        }
        deleteTree(directory);
    }

    /**
     * Keeps the directory name to characters that are safe everywhere; ids are UUIDs, so this normally changes nothing.
     */
    private static String sanitize(String pushId) {
        String safe = pushId.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.length() > 64 ? safe.substring(0, 64) : safe;
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Failed to delete quarantine entry {}", p, e);
                }
            });
        } catch (IOException e) {
            // Left behind in the temp directory rather than in the mirror, so this is a cleanup failure
            // and not a return of the problem quarantining exists to solve.
            log.warn("Failed to delete quarantine directory {}", root, e);
        }
    }
}
