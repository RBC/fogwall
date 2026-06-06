package org.finos.gitproxy.jetty.config;

import lombok.Data;

/** Binds the {@code dashboard:} block in git-proxy.yml. Controls optional dashboard UI features. */
@Data
public class DashboardConfig {

    /**
     * When {@code true}, reviewers can select multiple PENDING pushes and approve or reject them together. Defaults to
     * {@code false} — bulk actions are hidden until explicitly enabled. Enable only after confirming that your team's
     * review process supports bulk sign-off (e.g. a shared reason field is acceptable for audit purposes).
     */
    private boolean bulkReview = false;
}
