package com.rbc.fogwall.db.model;

import lombok.Builder;
import lombok.Data;

/** Query parameters for filtering fetch activity. All fields are optional; null means "don't filter on this". */
@Data
@Builder
public class FetchActivityQuery {
    private FetchActivity.Result result;
    private FetchActivity.Transport transport;
    private String provider;
    private String owner;
    private String repoName;

    /** Free-text search: matches rows where owner OR repo name contains this value (case-insensitive). */
    private String search;

    /** Maximum number of results to return. */
    @Builder.Default
    private int limit = 100;

    /** Number of results to skip (for pagination). */
    @Builder.Default
    private int offset = 0;

    /** Order results by hour descending (newest first). Defaults to true. */
    @Builder.Default
    private boolean newestFirst = true;
}
