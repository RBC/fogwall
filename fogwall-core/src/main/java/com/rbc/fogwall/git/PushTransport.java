package com.rbc.fogwall.git;

import com.rbc.fogwall.user.UserEntry;
import java.util.Optional;
import org.eclipse.jgit.api.TransportConfigCallback;

/**
 * Encapsulates the transport-specific authentication context for a single push request. Passed on {@link PushContext}
 * so hooks can resolve identity and apply upstream transport configuration without branching on transport method
 * themselves.
 *
 * <p>Two concrete variants: {@link Http} for token-based HTTP pushes and {@link Ssh} for public-key SSH pushes. Sealed
 * so that the set of transports is closed — git has exactly two wire protocols in widespread use.
 */
public sealed interface PushTransport permits PushTransport.Http, PushTransport.Ssh {

    String HTTP = "http";
    String SSH = "ssh";

    /** Short transport name — {@link #HTTP} or {@link #SSH}. */
    String name();

    /**
     * Value to store in the {@code method} audit column, if any. Empty for HTTP (method is implicit and historically
     * null in existing records); present for transports where the method is non-obvious from context.
     */
    Optional<String> auditMethod();

    /**
     * The user pre-authenticated by the transport layer. Non-empty for SSH (resolved from the connecting public key at
     * connection time); empty for HTTP (identity resolved later via token API call).
     */
    Optional<UserEntry> preAuthenticatedUser();

    /**
     * A JGit {@link TransportConfigCallback} to apply when opening upstream transports. Non-null for SSH (injects the
     * per-push forwarded-agent factory); null for HTTP (credentials handle auth).
     */
    TransportConfigCallback transportConfigCallback();

    /** HTTP store-and-forward: no pre-authenticated user, upstream auth via credentials. */
    record Http() implements PushTransport {
        @Override
        public String name() {
            return HTTP;
        }

        @Override
        public Optional<String> auditMethod() {
            return Optional.empty();
        }

        @Override
        public Optional<UserEntry> preAuthenticatedUser() {
            return Optional.empty();
        }

        @Override
        public TransportConfigCallback transportConfigCallback() {
            return null;
        }
    }

    /** SSH store-and-forward: user resolved from public-key auth, upstream auth via forwarded agent. */
    record Ssh(UserEntry user, TransportConfigCallback transportConfig) implements PushTransport {
        @Override
        public String name() {
            return SSH;
        }

        @Override
        public Optional<String> auditMethod() {
            return Optional.of(SSH.toUpperCase());
        }

        @Override
        public Optional<UserEntry> preAuthenticatedUser() {
            return Optional.of(user);
        }

        @Override
        public TransportConfigCallback transportConfigCallback() {
            return transportConfig;
        }
    }

    static PushTransport http() {
        return new Http();
    }

    static PushTransport ssh(UserEntry user, TransportConfigCallback transportConfig) {
        return new Ssh(user, transportConfig);
    }
}
