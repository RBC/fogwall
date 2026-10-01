package com.rbc.fogwall.dashboard.controller;

import com.rbc.fogwall.db.FetchStore;
import com.rbc.fogwall.db.model.FetchActivity;
import com.rbc.fogwall.db.model.FetchActivityQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
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
                    "Returns hourly fetch counts, most recent hour first. Each row counts the clones and fetches in one hour for one repository, transport, proxy mode, result, refusal reason and matching rule. Filter by result (ALLOWED, BLOCKED), transport (HTTP, SSH), provider, owner/repo name, or free-text search over owner and repo name. Paginate with limit/offset.")
    @GetMapping
    public List<FetchActivity> list(
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String transport,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String repoName,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "true") boolean newestFirst) {

        return buildQuery(result, transport, provider, owner, repoName, search, limit, offset, newestFirst)
                .map(fetchStore::find)
                .orElse(List.of());
    }

    /**
     * Empty when the caller named a result or transport that does not exist — the listing answers with no rows rather
     * than an error, since the filter came from a query string.
     */
    private static Optional<FetchActivityQuery> buildQuery(
            String result,
            String transport,
            String provider,
            String owner,
            String repoName,
            String search,
            int limit,
            int offset,
            boolean newestFirst) {

        FetchActivityQuery.FetchActivityQueryBuilder query = FetchActivityQuery.builder()
                .provider(provider)
                .owner(owner)
                .repoName(repoName)
                .search(search)
                .limit(limit)
                .offset(offset)
                .newestFirst(newestFirst);

        try {
            if (result != null && !result.isBlank()) {
                query.result(FetchActivity.Result.valueOf(result.toUpperCase(Locale.ROOT)));
            }
            if (transport != null && !transport.isBlank()) {
                query.transport(FetchActivity.Transport.valueOf(transport.toUpperCase(Locale.ROOT)));
            }
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return Optional.of(query.build());
    }
}
