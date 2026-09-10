package com.rbc.fogwall.git;

/**
 * The two proxy modes a pipeline step can run in. Server mode executes JGit pre-receive hooks; transparent proxy
 * executes servlet filters. A {@link PushStepKind} declares the modes it applies to, so a step present in only one mode
 * is a deliberate, visible fact rather than a silent gap between the two chains.
 */
public enum ProxyMode {
    /** Server mode — the JGit {@code ReceivePack} hook chain (both HTTP and SSH transports). */
    SERVER,
    /** Transparent proxy — the servlet filter chain. */
    TRANSPARENT
}
