package com.rbc.fogwall.git;

import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;

/**
 * {@link UploadPackFactory} mounted in place of {@link ServerUploadPackFactory} when clone/fetch serving is turned off
 * for a provider (globally or per-provider — see {@code server.serve-fetch} / {@code providers.<name>.serve-fetch},
 * fogwall#478). It refuses to create an {@link UploadPack}, so no local mirror is ever served over this endpoint.
 *
 * <p>The refusal is a {@link ServiceNotEnabledException} carrying {@link #MESSAGE}. JGit's smart-HTTP handlers
 * translate that to a {@code 403} with the message written as a git protocol {@code ERR} pkt-line, and
 * {@code SmartHttpErrorFilter} rewrites the status to {@code 200} so the git client surfaces it as {@code fatal: remote
 * error: <message>} rather than an opaque {@code 403} or a {@code 404} that would read as a missing repository. The SSH
 * transport refuses {@code git-upload-pack} separately with the same {@link #MESSAGE}.
 */
public class DisabledFetchUploadPackFactory implements UploadPackFactory<HttpServletRequest> {

    /** Client-facing refusal message, shared with the SSH transport so both transports refuse identically. */
    public static final String MESSAGE = "fetches are not served through this gateway";

    @Override
    public UploadPack create(HttpServletRequest req, Repository db) throws ServiceNotEnabledException {
        throw new ServiceNotEnabledException(MESSAGE);
    }
}
