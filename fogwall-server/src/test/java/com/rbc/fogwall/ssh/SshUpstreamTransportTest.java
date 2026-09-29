package com.rbc.fogwall.ssh;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.git.UpstreamFailure;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.SshException;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.Test;

class SshUpstreamTransportTest {

    private static final URIish UPSTREAM = uri();

    private static URIish uri() {
        try {
            return new URIish("ssh://git@upstream.example/owner/repo.git");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** JGit words an SSH login refusal as its own text; only the disconnect code says the key was refused. */
    @Test
    void noKeyAccepted_isNotAuthorized() {
        var refused = new TransportException(
                UPSTREAM,
                "Cannot log in at upstream.example:22",
                new SshException(SshConstants.SSH2_DISCONNECT_NO_MORE_AUTH_METHODS_AVAILABLE, "no more methods"));

        assertInstanceOf(UpstreamFailure.NotAuthorized.class, SshUpstreamTransport.classify(refused));
    }

    @Test
    void otherSshDisconnect_isUnavailable() {
        var dropped = new TransportException(
                UPSTREAM, "connection lost", new SshException(SshConstants.SSH2_DISCONNECT_CONNECTION_LOST, "lost"));

        assertInstanceOf(UpstreamFailure.Unavailable.class, SshUpstreamTransport.classify(dropped));
    }

    @Test
    void missingRepository_isNotFound() {
        var missing = new NoRemoteRepositoryException(UPSTREAM, "ERROR: Repository not found.");

        assertInstanceOf(UpstreamFailure.NotFound.class, SshUpstreamTransport.classify(missing));
    }
}
