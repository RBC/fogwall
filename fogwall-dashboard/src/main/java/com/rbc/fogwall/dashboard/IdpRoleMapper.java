package com.rbc.fogwall.dashboard;

import com.rbc.fogwall.config.AuthConfig;
import com.rbc.fogwall.user.Roles;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Maps the groups an IdP reports for a user to fogwall roles through {@code auth.role-mappings}, for LDAP, AD and OIDC
 * alike.
 *
 * <p>A mapping grants the role it names, and {@code ADMIN} brings {@code USER} with it. A user whose groups yield no
 * session role ({@link Roles#SESSION_ROLES}) receives {@code auth.default-role} on top of what they matched, or is
 * refused when there is none.
 */
@Slf4j
final class IdpRoleMapper {

    private static final String NONE = "NONE";

    private IdpRoleMapper() {}

    /**
     * Returns the roles, without the {@code ROLE_} prefix, for a user in {@code groups}, or empty when the login is
     * refused.
     */
    static Optional<Set<String>> rolesFor(
            Collection<String> groups, Map<String, List<String>> roleMappings, Optional<String> defaultRole) {
        Set<String> roles = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : roleMappings.entrySet()) {
            if (groups.stream().anyMatch(entry.getValue()::contains)) {
                roles.add(entry.getKey().toUpperCase(Locale.ROOT));
            }
        }
        if (roles.contains(Roles.ADMIN)) {
            roles.add(Roles.USER);
        }
        if (Roles.admitsSession(roles)) {
            return Optional.of(roles);
        }
        return defaultRole.map(role -> {
            roles.add(role);
            return roles;
        });
    }

    /**
     * Resolves {@code auth.default-role}, reading the deprecated {@code auth.require-role-mapping} when it is the only
     * one set. Empty means {@code NONE}: a user whose mappings admit no session is refused.
     *
     * @throws IllegalStateException when both keys are set, the value is not {@code NONE}, {@code READER}, {@code USER}
     *     or {@code AUDITOR}, or it resolves to {@code NONE} with no {@code role-mappings}, so that no one could sign
     *     in
     */
    static Optional<String> defaultRole(AuthConfig auth) {
        String configured = auth.getDefaultRole();
        Boolean legacy = auth.getRequireRoleMapping();
        if (configured != null && legacy != null) {
            throw new IllegalStateException(
                    "auth.require-role-mapping is replaced by auth.default-role; remove auth.require-role-mapping");
        }
        Optional<String> role;
        if (configured != null) {
            String value = configured.strip().toUpperCase(Locale.ROOT);
            role = switch (value) {
                case NONE -> Optional.empty();
                case Roles.READER, Roles.USER, Roles.AUDITOR -> Optional.of(value);
                default ->
                    throw new IllegalStateException(
                            "auth.default-role must be NONE, READER, USER or AUDITOR, not '" + configured + "'");
            };
        } else if (legacy != null) {
            role = legacy ? Optional.empty() : Optional.of(Roles.READER);
            log.warn(
                    "auth.require-role-mapping is deprecated: replace it with auth.default-role: {}{}",
                    role.orElse(NONE),
                    legacy
                            ? ""
                            : " (or USER to let users matching no mapping act, as require-role-mapping: false did)");
        } else {
            role = Optional.empty();
        }
        if (role.isEmpty() && auth.getRoleMappings().isEmpty()) {
            throw new IllegalStateException(
                    "auth.role-mappings is empty and auth.default-role is NONE, so no one can"
                            + " sign in. Map IdP groups under auth.role-mappings, or set auth.default-role to READER, USER or AUDITOR.");
        }
        return role;
    }

    /** Mapped roles that admit no session alone, such as {@code SELF_CERTIFY}. */
    static List<String> rolesAdmittingNoSession(Map<String, List<String>> roleMappings) {
        return roleMappings.keySet().stream()
                .map(r -> r.toUpperCase(Locale.ROOT))
                .filter(r -> !Roles.SESSION_ROLES.contains(r))
                .sorted()
                .toList();
    }
}
