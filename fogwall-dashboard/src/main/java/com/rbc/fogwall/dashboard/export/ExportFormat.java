package com.rbc.fogwall.dashboard.export;

/** How an export writes its rows. */
public enum ExportFormat {
    /** One row per record, a header per part; nested values are JSON text in their column. */
    CSV("csv"),
    /** JSON Lines: one full record per line, nesting kept. */
    JSON("jsonl");

    private final String extension;

    ExportFormat(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }
}
