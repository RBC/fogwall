package com.rbc.fogwall.dashboard.compose;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * The real {@code tea} binary, pointed at fogwall's SCM API listener instead of at Gitea.
 *
 * <p>Configuration is isolated to a temporary directory through {@code XDG_CONFIG_HOME}, so a run never touches the
 * developer's own logins.
 */
final class Tea {

    private final Path workspace;
    private final Path configHome;
    private final String login;

    Tea(Path workspace) throws IOException {
        this.workspace = workspace;
        this.configHome = Files.createDirectories(workspace.resolve("tea-config"));
        this.login = "compose-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Whether the binary is installed. CI pins a version; a developer may simply not have it. */
    static boolean available() {
        return Cli.available("tea");
    }

    /**
     * Registers a login against the listener.
     *
     * <p>{@code --insecure} because the listener serves a certificate from the CA {@code test/make-certs.sh} generates,
     * which no trust store knows; certificate validation is not what this case tests.
     */
    Cli.Result login(String listenerUrl, String token) throws IOException, InterruptedException {
        return run("login", "add", "--name", login, "--url", listenerUrl, "--token", token, "--insecure");
    }

    /** Opens a pull request, which is several requests through the listener before the create itself. */
    Cli.Result createPullRequest(String repo, String head, String base, String title)
            throws IOException, InterruptedException {
        return run(
                "pr",
                "create",
                "--login",
                login,
                "--repo",
                repo,
                "--head",
                head,
                "--base",
                base,
                "--title",
                title,
                "--description",
                "Opened by the compose suite through fogwall's SCM API listener");
    }

    private Cli.Result run(String... args) throws IOException, InterruptedException {
        String[] command = new String[args.length + 1];
        command[0] = "tea";
        System.arraycopy(args, 0, command, 1, args.length);
        return Cli.run(workspace, Map.of("XDG_CONFIG_HOME", configHome.toString()), command);
    }
}
