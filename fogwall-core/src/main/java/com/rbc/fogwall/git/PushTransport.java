package com.rbc.fogwall.git;

import com.rbc.fogwall.approval.ClientLivenessCheck;
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

    /**
     * Passive check for whether the client on this transport is still connected — see {@link ClientLivenessCheck}. Used
     * by the approval-wait poll loop to detect a client disconnect (e.g. Ctrl+C) without depending solely on an
     * outbound sideband write failing.
     *
     * <p>HTTP has no reliable signal here: Jetty doesn't watch a connection's read side while the request thread is
     * blocked in the approval-wait poll loop, so a Jetty-{@code EndPoint}-backed check was tried and empirically
     * confirmed to never fire (see #385) — {@link Http} always reports connected. SSH's MINA {@code ServerSession} is
     * watched by its own independent read loop regardless of the command thread, so {@link Ssh} has a real signal.
     */
    ClientLivenessCheck livenessCheck();

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

        @Override
        public ClientLivenessCheck livenessCheck() {
            return ClientLivenessCheck.alwaysConnected();
        }
    }

    /**
     * SSH store-and-forward: user resolved from public-key auth, upstream auth via forwarded agent.
     *
     * @param user the proxy user resolved from the connecting key at MINA auth time
     * @param connectingFingerprint SHA-256 fingerprint of the key the client authenticated with — used by
     *     {@link com.rbc.fogwall.service.SshScmIdentityEnricher} to match against SCM-registered keys
     * @param transportConfig per-push SSH session factory injected into the upstream JGit transport
     * @param livenessCheck backed by the MINA {@code ServerSession}'s close state
     */
    record Ssh(
            UserEntry user,
            String connectingFingerprint,
            TransportConfigCallback transportConfig,
            ClientLivenessCheck livenessCheck)
            implements PushTransport {
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

    static PushTransport ssh(
            UserEntry user,
            String connectingFingerprint,
            TransportConfigCallback transportConfig,
            ClientLivenessCheck livenessCheck) {
        return new Ssh(user, connectingFingerprint, transportConfig, livenessCheck);
    }
}
