package com.rbc.fogwall.git;

import java.text.MessageFormat;
import java.util.regex.Pattern;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;

/**
 * Why a mirror clone or fetch from the upstream failed, read from the exception JGit threw. The developer is told which
 * of these it was, so an expired token does not read as a typo in the remote URL.
 *
 * <p>JGit reports the upstream's HTTP status only through exception type and message text: a 404 is a
 * {@link NoRemoteRepositoryException}, while 401 and 403 are {@link TransportException}s told apart by message. The
 * messages are compared against JGit's own resource bundle, so they match in whatever locale JGit formatted them.
 */
public sealed interface UpstreamFailure {

    /** The upstream asked for a credential and none was sent (HTTP 401). */
    record CredentialRequired() implements UpstreamFailure {}

    /** The upstream did not accept the credential that was sent (HTTP 401). */
    record NotAuthorized() implements UpstreamFailure {}

    /** The upstream accepted the credential but refused it this repository (HTTP 403). */
    record Forbidden() implements UpstreamFailure {}

    /** The upstream has no such repository, or will not say that it has one to this credential (HTTP 404). */
    record NotFound() implements UpstreamFailure {}

    /** The upstream could not be reached, or answered with an error of its own. */
    record Unavailable() implements UpstreamFailure {}

    /** The failure was local, not in talking to the upstream. */
    record Internal() implements UpstreamFailure {}

    String CREDENTIAL_REQUIRED_MESSAGE = "The upstream needs a credential for this repository.";
    String NOT_AUTHORIZED_MESSAGE =
            "The upstream did not accept your credential. Check that it is valid and has not expired.";
    String FORBIDDEN_MESSAGE =
            "The upstream refused your credential access to this repository. Check its permissions and scopes.";
    String NOT_FOUND_MESSAGE = "Repository not found upstream, or your credential cannot see it.";
    String UNAVAILABLE_MESSAGE = "The upstream could not be reached. Try again later.";
    String INTERNAL_MESSAGE = "fogwall could not open this repository.";

    /** The line shown to the developer. Names no host, path or configuration. */
    default String message() {
        return switch (this) {
            case CredentialRequired _ -> CREDENTIAL_REQUIRED_MESSAGE;
            case NotAuthorized _ -> NOT_AUTHORIZED_MESSAGE;
            case Forbidden _ -> FORBIDDEN_MESSAGE;
            case NotFound _ -> NOT_FOUND_MESSAGE;
            case Unavailable _ -> UNAVAILABLE_MESSAGE;
            case Internal _ -> INTERNAL_MESSAGE;
        };
    }

    /** Classifies whatever a mirror clone or fetch threw, by the first JGit transport error in its cause chain. */
    static UpstreamFailure classify(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof NoRemoteRepositoryException) {
                return new NotFound();
            }
            if (t instanceof TransportException transport) {
                return classifyTransport(transport.getMessage());
            }
        }
        return new Internal();
    }

    private static UpstreamFailure classifyTransport(String message) {
        if (message == null) {
            return new Unavailable();
        }
        if (message.endsWith(JGitText.get().noCredentialsProvider)) {
            return new CredentialRequired();
        }
        if (message.endsWith(JGitText.get().notAuthorized)
                || message.endsWith(JGitText.get().authenticationNotSupported)) {
            return new NotAuthorized();
        }
        if (serviceNotPermitted().matcher(message).find()) {
            return new Forbidden();
        }
        return new Unavailable();
    }

    /**
     * JGit's 403 text, {@code "{1} not permitted on '{0}'"}, with each argument matching anything. Built per call: this
     * only runs once a request has already failed.
     */
    private static Pattern serviceNotPermitted() {
        String slot = "\u0000";
        return Pattern.compile(Pattern.quote(MessageFormat.format(JGitText.get().serviceNotPermitted, slot, slot))
                .replace(slot, "\\E.*\\Q"));
    }
}
