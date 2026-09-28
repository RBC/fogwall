package com.rbc.fogwall.config;

import lombok.Data;

/** Binds {@code auth.git-credentials:} — limits on the git credentials fogwall issues to its users. */
@Data
public class GitCredentialsConfig {

    /**
     * How long a credential works after it is issued or rotated, as an ISO-8601 duration such as {@code P90D}; empty
     * for no limit. Lowering it also retires existing credentials older than the new limit.
     */
    private String maxLifetime = "";
}
