package com.rbc.fogwall.user;

import java.util.Collection;
import java.util.Set;

/**
 * The dashboard roles, as stored on {@link UserEntry#getRoles()} without the {@code ROLE_} prefix.
 *
 * <p>A session holds at least one of {@link #SESSION_ROLES}. {@link #READER} sees pushes, repositories and activity.
 * {@link #USER} acts as well, and {@link #ADMIN} implies it. {@link #AUDITOR} reads the access records and makes the
 * whole session read-only, whatever else it holds. {@link #SELF_CERTIFY} is a modifier that admits no one on its own.
 */
public final class Roles {

    public static final String READER = "READER";
    public static final String USER = "USER";
    public static final String AUDITOR = "AUDITOR";
    public static final String ADMIN = "ADMIN";
    public static final String SELF_CERTIFY = "SELF_CERTIFY";

    /** The roles that admit a session. */
    public static final Set<String> SESSION_ROLES = Set.of(READER, USER, AUDITOR, ADMIN);

    private Roles() {}

    /** Whether {@code roles} admit a session at all. */
    public static boolean admitsSession(Collection<String> roles) {
        return roles.stream().anyMatch(SESSION_ROLES::contains);
    }

    /** Whether {@code roles} permit acting: reviewing, pushing, filing issues, editing a profile. */
    public static boolean canAct(Collection<String> roles) {
        return (roles.contains(USER) || roles.contains(ADMIN)) && !roles.contains(AUDITOR);
    }
}
