package com.rbc.fogwall.git;

import java.util.regex.Pattern;

/**
 * Validates repository owner/name path segments before they are used to construct upstream URLs or cache keys. The
 * allowlist is deliberately strict: traversal sequences, path separators, whitespace, and control characters must never
 * survive to URL construction, regardless of how the servlet container normalizes the request path.
 */
public final class RepoSlugValidator {

    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9._-]+");

    private RepoSlugValidator() {}

    /**
     * Returns true if {@code segment} is a safe owner or repository name segment: non-empty, not a dot-only traversal
     * segment, and containing only letters, digits, {@code .}, {@code _} and {@code -}.
     */
    public static boolean isValidSegment(String segment) {
        return segment != null
                && !segment.equals(".")
                && !segment.equals("..")
                && SEGMENT.matcher(segment).matches();
    }
}
