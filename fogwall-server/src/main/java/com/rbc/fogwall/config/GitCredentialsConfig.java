package com.rbc.fogwall.config;

import lombok.Data;

/** Binds {@code auth.git-credentials:} — limits on the git credentials fogwall issues to its users. */
@Data
public class GitCredentialsConfig {

    /**
     * Days a credential works after it is issued or rotated; {@code 0} for no limit. Lowering it also retires existing
     * credentials older than the new limit.
     */
    private int maxLifetimeDays = 0;
}
