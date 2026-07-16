package com.rbc.fogwall.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * Binds the {@code auth:} block in fogwall.yml. Selects the active authentication provider and holds its settings.
 *
 * <p>Supported providers:
 *
 * <ul>
 *   <li>{@code local} (default) — usernames and BCrypt password hashes configured directly in {@code users:}
 *   <li>{@code ldap} — bind authentication against a generic LDAP directory
 *   <li>{@code ad} — bind authentication against Active Directory using UPN ({@code user@domain})
 *   <li>{@code oidc} — OpenID Connect authorization code flow (e.g. Keycloak, Dex, Entra ID)
 * </ul>
 *
 * <p>Example YAML:
 *
 * <pre>
 * auth:
 *   provider: ldap
 *   ldap:
 *     url: ldap://localhost:389/dc=example,dc=com
 *     user-dn-patterns: cn={0},ou=users
 * </pre>
 */
@Data
public class AuthConfig {

    /** Active authentication provider. Accepted values: {@code local}, {@code ldap}, {@code ad}, {@code oidc}. */
    private String provider = "local";

    /**
     * Maps fogwall role names to lists of IdP group names. When a user belongs to any listed group, the corresponding
     * role is granted. Applies to OIDC (via {@code groups-claim}) and LDAP (via group search).
     *
     * <p>Example:
     *
     * <pre>
     * auth:
     *   role-mappings:
     *     APPROVER:
     *       - "git-approvers"
     *       - "security-team"
     *     ADMIN:
     *       - "git-admins"
     * </pre>
     */
    private Map<String, List<String>> roleMappings = new HashMap<>();

    /**
     * When {@code role-mappings} is non-empty, controls whether a user whose IdP groups match none of the mappings is
     * denied access. Defaults to {@code true} — deny-by-default, the correct posture for regulated environments.
     *
     * <p>Set to {@code false} to restore open-access behaviour: any user who authenticates successfully against the IdP
     * is granted {@code ROLE_USER} unconditionally, and {@code role-mappings} (if present) only grant additional roles
     * on top. This is a no-op when {@code role-mappings} is empty, since open mode is already the behaviour in that
     * case.
     */
    private boolean requireRoleMapping = true;

    /**
     * OIDC claim name that contains the user's group memberships. Defaults to {@code groups}, which is standard for
     * Keycloak, Okta, and most Entra ID configurations. Override if your IdP uses a different claim (e.g.
     * {@code roles}, {@code memberOf}).
     */
    private String groupsClaim = "groups";

    /**
     * Maximum inactive interval for authenticated sessions, in seconds. Once a session has been idle for this duration
     * the user is required to re-authenticate. Defaults to {@code 86400} (24 hours); tighten to {@code 28800} (8 hours)
     * or less for stricter compliance environments.
     */
    private long sessionTimeoutSeconds = 86400;

    private LdapAuthConfig ldap = new LdapAuthConfig();
    private AdAuthConfig ad = new AdAuthConfig();
    private OidcAuthConfig oidc = new OidcAuthConfig();
}
