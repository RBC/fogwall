package com.rbc.fogwall.user;

import lombok.Builder;
import lombok.Value;

/** An SCM identity linking a proxy user to their username on a specific provider. */
@Value
@Builder
public class ScmIdentity {
    /** Provider name (e.g. {@code github}, {@code gitlab}). */
    String provider;

    /** Username on that provider. */
    String username;

    /**
     * Whether this identity was confirmed via OAuth account linking (#40), as opposed to manually entered by an
     * admin/user via the free-text dashboard form. Used by {@code CheckUserPushPermissionHook} to decide which
     * identities count for push authorization when {@code scm-oauth.identity-mode} is {@code strict}.
     */
    @Builder.Default
    boolean verified = false;
}
