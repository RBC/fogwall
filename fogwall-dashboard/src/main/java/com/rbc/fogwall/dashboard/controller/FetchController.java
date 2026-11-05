package com.rbc.fogwall.dashboard.controller;

import com.rbc.fogwall.dashboard.export.ExportFields;
import com.rbc.fogwall.dashboard.export.ExportFormat;
import com.rbc.fogwall.db.FetchStore;
import com.rbc.fogwall.db.model.FetchActivity;
import com.rbc.fogwall.db.model.FetchActivityQuery;
import com.rbc.fogwall.db.model.RecordCursor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only view of fetch activity: hourly counts of fogwall's decisions on clones and fetches. */
@Tag(name = "Fetches", description = "Hourly fetch activity")
@RestController
@RequestMapping("/api/fetches")
@RequiredArgsConstructor
public class FetchController {

    private final FetchStore fetchStore;

    @Operation(
            operationId = "listFetchActivity",
            summary = "List hourly fetch activity",
            description =
                    "Returns hourly fetch counts, most recent hour first. Each row counts the clones and fetches in one hour for one repository, transport, proxy mode, result, refusal reason and matching rule. Filter by result (ALLOWED, BLOCKED), transport (HTTP, SSH), provider, owner, repo (formerly repoName), free-text search over owner and repo name, or an hour window (from inclusive, to exclusive, ISO-8601). A fetch carries no actor, so user is not a filter here. Paginate with limit/offset, or pass the X-Next-Cursor response header back as after to read the next page.")
    @GetMapping
    public ResponseEntity<List<FetchActivity>> list(
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String transport,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String repo,
            @RequestParam(required = false) String repoName,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String after,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "true") boolean newestFirst) {

        ListingParams.checkPage(limit, offset);
        FetchActivityQuery.FetchActivityQueryBuilder query = query(
                        result, transport, provider, owner, repo, repoName, user, search, from, to, newestFirst)
                .limit(limit)
                .offset(offset);
        ListingParams.cursor(after).ifPresent(query::after);

        return ListingParams.page(
                fetchStore.find(query.build()), limit, r -> new RecordCursor(r.getBucketStart(), r.getId()));
    }

    @Operation(
            operationId = "exportFetchActivity",
            summary = "Export hourly fetch activity",
            description =
                    "Every fetch activity row matching the same filters listFetchActivity takes, as a zip of parts of at most 100,000 rows and a manifest naming the filters, the generation time, and each part's row count and SHA-256. format is CSV (default) or JSON (JSON Lines). columns chooses and orders CSV columns. Requires ROLE_AUDITOR or ROLE_ADMIN.")
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public void export(
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String transport,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String repo,
            @RequestParam(required = false) String repoName,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "true") boolean newestFirst,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String columns,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        FetchActivityQuery.FetchActivityQueryBuilder query =
                query(result, transport, provider, owner, repo, repoName, user, search, from, to, newestFirst);
        ExportFormat exportFormat = ExportResponses.format(format);
        List<String> chosen = ExportFields.columns(columns, ExportFields.FETCH_ACTIVITY, "fetch activity");

        ExportResponses.stream(
                request,
                response,
                ExportSources.FETCH_ACTIVITY,
                List.of(ExportSources.fetchActivity(fetchStore, query, chosen)),
                exportFormat);
    }

    /** The filters the listing and the export share. */
    static FetchActivityQuery.FetchActivityQueryBuilder query(
            String result,
            String transport,
            String provider,
            String owner,
            String repo,
            String repoName,
            String user,
            String search,
            String from,
            String to,
            boolean newestFirst) {
        ListingParams.unsupported("user", user, "fetch activity");
        FetchActivityQuery.FetchActivityQueryBuilder query =
                FetchActivityQuery.builder().newestFirst(newestFirst);
        ListingParams.enumValue("result", result, FetchActivity.Result.class).ifPresent(query::result);
        ListingParams.enumValue("transport", transport, FetchActivity.Transport.class)
                .ifPresent(query::transport);
        ListingParams.text(provider).ifPresent(query::provider);
        ListingParams.text(owner).ifPresent(query::owner);
        ListingParams.aliased("repo", repo, "repoName", repoName).ifPresent(query::repoName);
        ListingParams.text(search).ifPresent(query::search);
        ListingParams.instant("from", from).ifPresent(query::from);
        ListingParams.instant("to", to).ifPresent(query::to);
        return query;
    }
}
