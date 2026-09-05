package com.rbc.fogwall.servlet;

import java.util.Locale;

/**
 * Structural check on the raw sub-path of a REST dialect request, applied before the allowlist and again before the
 * request is forwarded.
 *
 * <p>Two things are rejected outright on every dialect:
 *
 * <ul>
 *   <li><b>Traversal segments</b> — a {@code .} or {@code ..} segment. Jetty already resolves these while parsing, so
 *       the path reaching a filter is canonical (still percent-encoded, but with dot segments gone) and the allowlist
 *       and the forwarder necessarily agree on it. This check is the second line: it keeps the guarantee attached to
 *       the path itself rather than to that container behaviour, which is what the forwarding servlet relies on.
 *   <li><b>Encoded path separators</b> — {@code %2F} and {@code %5C}, in either case — everywhere except the one place
 *       a dialect genuinely needs them.
 * </ul>
 *
 * <p>GitLab is the only dialect with such a place: it addresses a project as a single URL-encoded {@code owner/repo}
 * segment, so {@code /projects/acme%2Fwidgets/issues} is ordinary traffic. That exception is confined to the segment
 * immediately after {@code projects} — an encoded separator anywhere else in a GitLab path, or anywhere at all in a
 * Gitea/Forgejo path, is refused. GitHub needs no exception at all: its dialect is a single fixed GraphQL path.
 *
 * <p>Confining it matters because the encoded separator is what the connector's relaxed {@code UriCompliance} exists to
 * permit. Jetty rejects an ambiguous path by default; each place fogwall accepts one is a place where the path the
 * permission engine matched and the path the upstream resolves could be read differently. Restricting the relaxation to
 * the segment that needs it keeps that gap as small as the dialect allows.
 */
public final class ScmApiRestPathPolicy {

    /** Where, if anywhere, an encoded path separator is legitimate for a dialect. */
    public enum EncodedSeparators {
        /** No encoded separator is ever valid — Gitea/Forgejo, and any dialect without an encoded-path identifier. */
        REJECTED,
        /** Valid only in the segment following {@code projects} — GitLab's URL-encoded {@code owner/repo}. */
        GITLAB_PROJECT_SEGMENT
    }

    private static final String PROJECTS_SEGMENT = "projects";
    private static final String ENCODED_SLASH = "%2f";
    private static final String ENCODED_BACKSLASH = "%5c";

    private ScmApiRestPathPolicy() {}

    /**
     * Whether {@code rawSubPath} (still encoded, from {@link ScmApiRestPath#rawSubPath}) may be matched and forwarded.
     * An empty path is acceptable — it addresses the dialect root, which the allowlist then refuses on its own terms.
     */
    public static boolean isForwardable(String rawSubPath, EncodedSeparators encodedSeparators) {
        if (rawSubPath == null) return false;
        if (rawSubPath.isEmpty()) return true;
        if (!rawSubPath.startsWith("/")) return false;

        String[] segments = rawSubPath.substring(1).split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i].toLowerCase(Locale.ROOT);
            if (isTraversal(segment)) return false;
            // An encoded backslash is refused unconditionally: no dialect addresses anything with one, and some path
            // handling treats it as a separator, so the only thing permitting it could do is create a second reading.
            if (segment.contains(ENCODED_BACKSLASH)) return false;
            if (segment.contains(ENCODED_SLASH) && !isProjectSegment(segments, i, encodedSeparators)) return false;
        }
        return true;
    }

    /**
     * A traversal segment in either literal or percent-encoded form. {@code %2e%2e} decodes to {@code ..} and is the
     * same instruction to whatever normalises the path downstream, so matching only the literal form would leave the
     * check trivially bypassable. {@code segment} is already lower-cased by the caller.
     */
    private static boolean isTraversal(String segment) {
        String decoded = segment.replace("%2e", ".");
        return decoded.equals(".") || decoded.equals("..");
    }

    /** The project segment is the one right after a leading {@code projects} segment, and only there. */
    private static boolean isProjectSegment(String[] segments, int index, EncodedSeparators policy) {
        return policy == EncodedSeparators.GITLAB_PROJECT_SEGMENT && index == 1 && PROJECTS_SEGMENT.equals(segments[0]);
    }
}
