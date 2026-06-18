package com.rbc.fogwall.jetty.config;

import lombok.Data;

/** Binds the {@code auth.ldap} block in git-proxy.yml. */
@Data
public class LdapAuthConfig {

    /**
     * LDAP server URL including base DN, e.g. {@code ldap://localhost:389/dc=example,dc=com}. The base DN is appended
     * to all relative DN patterns.
     */
    private String url = "";

    /**
     * User DN pattern relative to the base DN; {@code {0}} is substituted with the login username. Example:
     * {@code cn={0},ou=users} resolves to {@code cn=alice,ou=users,dc=example,dc=com}.
     */
    private String userDnPatterns = "uid={0}";

    /**
     * Optional bind DN used when performing group searches or attribute lookups. Leave blank when anonymous search is
     * sufficient (e.g. bind-only auth with no attribute population).
     */
    private String bindDn = "";

    /** Password for the bind DN. Ignored when {@code bindDn} is blank. */
    private String bindPassword = "";

    /**
     * Base DN for group search, relative to the base DN in {@code url}. When set, group membership is used to derive
     * roles via {@code auth.role-mappings}. Example: {@code ou=groups}.
     */
    private String groupSearchBase = "";

    /**
     * LDAP filter for group membership. {@code {0}} is substituted with the user's full DN. Defaults to the standard
     * member-attribute filter.
     */
    private String groupSearchFilter = "(member={0})";

    /**
     * LDAP search filter to locate the user entry before binding, e.g. {@code (sAMAccountName={0})}. When set, takes
     * precedence over {@code userDnPatterns} — a search-first approach is used instead of constructing a DN directly.
     * Requires bind credentials ({@code bindDn}/{@code bindPassword}) when anonymous search is not permitted.
     */
    private String userSearchFilter = "";

    /**
     * Base DN (relative to the base in {@code url}) to scope the user search. Defaults to {@code ""} (search from the
     * URL base DN). Example: {@code OU=Accounts}.
     */
    private String userSearchBase = "";
}
