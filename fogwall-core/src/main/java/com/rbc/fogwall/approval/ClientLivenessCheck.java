package com.rbc.fogwall.approval;

/**
 * Passive, read-side check for whether the git client on the other end of a store-and-forward push is still connected.
 * Unlike the write-triggered {@link ClientDisconnectedException} path (which only surfaces a disconnect once a sideband
 * message fails to flush), this is a cheap state read backed by the transport's own connection tracking, where such a
 * signal exists - see {@link com.rbc.fogwall.git.PushTransport#livenessCheck()} for which transports actually have one.
 */
@FunctionalInterface
public interface ClientLivenessCheck {

    /** Returns {@code true} if the client connection is still believed to be open. */
    boolean isConnected();

    /** Always reports connected - used where no transport-level signal is available (e.g. HTTP, tests). */
    static ClientLivenessCheck alwaysConnected() {
        return () -> true;
    }
}
