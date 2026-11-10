package com.rbc.fogwall.dashboard.controller;

import com.rbc.fogwall.dashboard.export.ExportFormat;
import com.rbc.fogwall.dashboard.export.ExportSource;
import com.rbc.fogwall.dashboard.export.ExportWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Streams an export to the response as a zip download. */
final class ExportResponses {

    /** Parameters that shape the file rather than select rows, so the manifest's filters leave them out. */
    private static final Set<String> NOT_FILTERS = Set.of("format", "columns");

    /** Suffix of an export of several types' per-type column choice, {@code <type>.columns}; not a filter either. */
    static final String COLUMNS_SUFFIX = ".columns";

    private ExportResponses() {}

    static ExportFormat format(String value) {
        return ListingParams.enumValue("format", value, ExportFormat.class).orElse(ExportFormat.CSV);
    }

    /**
     * Writes {@code sources} as the response body. Everything that can be refused — a bad filter, an unknown column —
     * must be checked before this is called: once it starts, the response is committed.
     */
    static void stream(
            HttpServletRequest request,
            HttpServletResponse response,
            String name,
            List<ExportSource<?>> sources,
            ExportFormat format)
            throws IOException {
        Map<String, String> filters = new TreeMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (!NOT_FILTERS.contains(key)
                    && !key.endsWith(COLUMNS_SUFFIX)
                    && values.length > 0
                    && !values[0].isBlank()) {
                filters.put(key, String.join(",", values));
            }
        });
        String stamp = Instant.now()
                .truncatedTo(ChronoUnit.SECONDS)
                .toString()
                .replace(":", "")
                .replace("-", "");
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"fogwall-" + name + "-" + stamp + ".zip\"");
        response.setHeader("Cache-Control", "no-store");
        new ExportWriter().write(response.getOutputStream(), sources, format, filters);
        response.flushBuffer();
    }
}
