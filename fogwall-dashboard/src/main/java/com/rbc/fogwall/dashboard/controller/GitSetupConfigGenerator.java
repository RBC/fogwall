package com.rbc.fogwall.dashboard.controller;

import com.rbc.fogwall.provider.FogwallProvider;

/**
 * Generates copy-pasteable git configuration that routes a provider's traffic through this fogwall deployment
 * (fogwall#475), using git's native URL rewriting so no per-repo remote editing is needed for the global forms.
 *
 * <p><b>Push-only by default.</b> The primary config reroutes only <em>pushes</em> ({@code pushInsteadOf} →
 * {@code /server}); clones and fetches keep going straight to the upstream host. fogwall governs the push path, and in
 * most deployments reads are already handled/inspected elsewhere — so leaving fetches direct is both less surprising (a
 * developer who only reads never touches fogwall) and avoids a needless read-time dependency on fogwall. Routing
 * fetches through fogwall ({@code insteadOf} → {@code /proxy}) is offered separately as an opt-in.
 *
 * <p><b>Global vs per-repo.</b> The global form (a {@code ~/.gitconfig} block) is one paste that applies to every repo
 * under the upstream host. The per-repo form ({@code git clone} + {@code git remote set-url --push}) is explicit and
 * visible in {@code git remote -v}, and touches only the repo you run it in — the safer choice when a machine pushes to
 * the upstream both through fogwall and directly.
 *
 * <p>All methods are pure so they are unit-testable without a servlet context.
 */
final class GitSetupConfigGenerator {

    private GitSetupConfigGenerator() {}

    /**
     * Global push-only {@code ~/.gitconfig} block over HTTPS: reroutes pushes to {@code /server}, fetches unchanged.
     */
    static String httpPush(String base, FogwallProvider p) {
        String host = p.getUri().getHost();
        return "# " + p.getName() + " — send your " + host + " pushes through fogwall (clones/fetches are unchanged).\n"
                + "# Add to ~/.gitconfig.\n"
                + "[url \"" + serverUrl(base, p) + "\"]\n"
                + "\tpushInsteadOf = " + upstream(p) + "/\n";
    }

    /** Opt-in global {@code ~/.gitconfig} block over HTTPS to <em>also</em> route fetches through fogwall's proxy. */
    static String httpRead(String base, FogwallProvider p) {
        String host = p.getUri().getHost();
        return "# Optional: also send " + host + " fetches through fogwall (adds a read-time dependency on fogwall).\n"
                + "[url \"" + proxyRoute(base, p) + "\"]\n"
                + "\tinsteadOf = " + upstream(p) + "/\n";
    }

    /** Per-repo HTTPS commands: clone from the upstream as usual, then point only pushes at fogwall. */
    static String httpPerRepo(String base, FogwallProvider p) {
        return "# Per repository: clone from " + p.getUri().getHost()
                + " as usual, then point only pushes at fogwall.\n"
                + "git clone " + upstream(p) + "/<owner>/<repo>.git\n"
                + "cd <repo>\n"
                + "git remote set-url --push origin " + serverUrl(base, p) + "<owner>/<repo>.git\n";
    }

    /** Global push-only {@code ~/.gitconfig} block over SSH: reroutes pushes to fogwall's SSH transport. */
    static String sshPush(String sshHost, int sshPort, FogwallProvider p) {
        String host = p.getUri().getHost();
        return "# " + p.getName() + " — send your " + host
                + " SSH pushes through fogwall (requires SSH agent forwarding: ssh -A / ForwardAgent yes).\n"
                + "[url \"" + sshRoute(sshHost, sshPort, p) + "\"]\n"
                + "\tpushInsteadOf = git@" + host + ":\n"
                + "\tpushInsteadOf = ssh://git@" + host + "/\n";
    }

    /** Per-repo SSH commands: clone from the upstream as usual, then point only pushes at fogwall. */
    static String sshPerRepo(String sshHost, int sshPort, FogwallProvider p) {
        String host = p.getUri().getHost();
        return "# Per repository (SSH): clone from " + host + " as usual, then point only pushes at fogwall.\n"
                + "git clone git@" + host + ":<owner>/<repo>.git\n"
                + "cd <repo>\n"
                + "git remote set-url --push origin " + sshRoute(sshHost, sshPort, p) + "<owner>/<repo>.git\n";
    }

    // ── route helpers (each ends with a trailing slash so git appends <owner>/<repo>.git onto the prefix cleanly) ──

    /**
     * The {@code /server} route prefix — clone/push through fogwall's server mode, e.g.
     * {@code https://fw/server/github.com/}.
     */
    static String serverUrl(String base, FogwallProvider p) {
        return base + "/server" + p.servletPath() + "/";
    }

    private static String proxyRoute(String base, FogwallProvider p) {
        return base + "/proxy" + p.servletPath() + "/";
    }

    private static String sshRoute(String sshHost, int sshPort, FogwallProvider p) {
        return "ssh://" + sshHost + ":" + sshPort + p.servletPath() + "/";
    }

    private static String upstream(FogwallProvider p) {
        String s = p.getUri().toString();
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
