package com.rbc.fogwall.dashboard.compose;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The real {@code git} binary, run against a working copy in a temporary directory.
 *
 * <p>The compose suite uses the real client rather than JGit, so it exercises the packaged stack's behaviour towards
 * git's own protocol negotiation.
 */
final class Git {

    private final Path workspace;
    private final Map<String, String> extraEnv;

    Git(Path workspace) {
        this(workspace, Map.of());
    }

    /** Extra environment for every git invocation — e.g. {@code GIT_SSH_COMMAND} and {@code SSH_AUTH_SOCK} for SSH. */
    Git(Path workspace, Map<String, String> extraEnv) {
        this.workspace = workspace;
        this.extraEnv = extraEnv;
    }

    /** Clones through the given URL and returns the working copy. */
    Path clone(String url, String directory) throws IOException, InterruptedException {
        Cli.Result result = run(workspace, "git", "clone", url, directory);
        if (!result.succeeded()) {
            throw new IOException("clone failed: " + result.output());
        }
        Path repo = workspace.resolve(directory);
        run(repo, "git", "config", "user.name", "Compose Suite");
        run(repo, "git", "config", "user.email", "testuser@example.com");
        // No signing: the runner has no key.
        run(repo, "git", "config", "commit.gpgsign", "false");
        return repo;
    }

    /** Creates and checks out a branch. */
    void branch(Path repo, String name) throws IOException, InterruptedException {
        Cli.Result result = run(repo, "git", "checkout", "-b", name);
        if (!result.succeeded()) {
            throw new IOException("branch failed: " + result.output());
        }
    }

    /** Writes a file, stages it and commits, all in one step. */
    void commit(Path repo, String file, String content, String message) throws IOException, InterruptedException {
        Path target = repo.resolve(file);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
        run(repo, "git", "add", file);
        Cli.Result result = run(repo, "git", "commit", "-m", message);
        if (!result.succeeded()) {
            throw new IOException("commit failed: " + result.output());
        }
    }

    /** Pushes the current branch, returning the outcome rather than throwing, since the outcome is the assertion. */
    Cli.Result push(Path repo) throws IOException, InterruptedException {
        return run(repo, "git", "push", "origin", "HEAD");
    }

    private Cli.Result run(Path directory, String... command) throws IOException, InterruptedException {
        var env = new HashMap<String, String>();
        env.put("GIT_TERMINAL_PROMPT", "0");
        env.putAll(extraEnv);
        return Cli.run(directory, env, command);
    }
}
