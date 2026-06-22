package com.rbc.fogwall.approval;

/** Thrown by a {@link ProgressSender} when the git client's connection has been lost. */
public class ClientDisconnectedException extends RuntimeException {
    public ClientDisconnectedException(Throwable cause) {
        super("Git client disconnected", cause);
    }
}
