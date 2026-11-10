package com.rbc.fogwall.dashboard.controller;

import com.rbc.fogwall.dashboard.activity.ActivityWindow;
import com.rbc.fogwall.dashboard.activity.ContributionActivity;
import com.rbc.fogwall.dashboard.activity.ContributionActivityService;
import com.rbc.fogwall.dashboard.activity.ContributionActivityService.Filters;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Contribution activity: what people contributed through fogwall and where it ended up. Everyone sees their own;
 * auditors and admins see anyone's, and the aggregate. Counts only what passed through fogwall.
 */
@Tag(name = "Activity", description = "Contribution activity through fogwall")
@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ContributionActivityService service;
    private final Clock clock = Clock.systemUTC();

    private static final String WINDOW_DESCRIPTION =
            " The window is from (inclusive) to (exclusive), ISO-8601; to defaults to now and from to 90 days before it. It is split into days up to 45 days, weeks up to a year, and months beyond, in UTC, at most 3660 days. provider, owner and repo narrow it to exact matches. Pull/merge request and issue figures are absent (proposalsAvailable false) when no provider takes that traffic through fogwall.";

    @Operation(
            operationId = "getMyActivity",
            summary = "The signed-in user's contribution activity",
            description =
                    "Pushes by status, and pull/merge requests and issues opened, edited, commented on and merged through fogwall, in total, by period and by repository. Each person's edits to one pull request count once, however many requests carried them."
                            + WINDOW_DESCRIPTION)
    @GetMapping("/me")
    public ContributionActivity mine(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String repo) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return service.forUser(auth.getName(), window(from, to), filters(provider, owner, repo));
    }

    @Operation(
            operationId = "getUserActivity",
            summary = "A user's contribution activity",
            description =
                    "The same report as getMyActivity, for the named fogwall user. Requires ROLE_AUDITOR or ROLE_ADMIN."
                            + WINDOW_DESCRIPTION)
    @GetMapping("/users/{username}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public ContributionActivity user(
            @PathVariable String username,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String repo) {
        return service.forUser(username, window(from, to), filters(provider, owner, repo));
    }

    @Operation(
            operationId = "getActivity",
            summary = "Contribution activity across everyone",
            description =
                    "Everyone's activity, in total, by period, by provider and by repository, with the number of distinct contributors: people with a push in any state or a forwarded pull/merge request or issue action. Requires ROLE_AUDITOR or ROLE_ADMIN."
                            + WINDOW_DESCRIPTION)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public ContributionActivity aggregate(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) String repo) {
        return service.aggregate(window(from, to), filters(provider, owner, repo));
    }

    @Operation(
            operationId = "getActivitySummary",
            summary = "Contribution activity across everyone, for every signed-in user",
            description =
                    "The aggregate report without any per-person filter: totals, periods, per-provider rows and the "
                            + ContributionActivityService.SUMMARY_REPOS
                            + " most active repositories. Names no person."
                            + WINDOW_DESCRIPTION)
    @GetMapping("/summary")
    public ContributionActivity summary(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return service.summary(window(from, to));
    }

    /** One repository's pushes by period and status. */
    public record RepoPushActivity(ActivityWindow window, List<ContributionActivity.Period> periods) {}

    @Operation(
            operationId = "getRepoPushActivity",
            summary = "A repository's pushes by period and status",
            description =
                    "Push counts for one repository, by period and status, read from the push records. provider, owner and repo are required."
                            + WINDOW_DESCRIPTION)
    @GetMapping("/repo-pushes")
    public RepoPushActivity repoPushes(
            @RequestParam String provider,
            @RequestParam String owner,
            @RequestParam String repo,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        ActivityWindow window = window(from, to);
        return new RepoPushActivity(window, service.repoPushes(window, provider, owner, repo));
    }

    private ActivityWindow window(String from, String to) {
        try {
            return ActivityWindow.of(
                    ListingParams.instant("from", from), ListingParams.instant("to", to), clock.instant());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    private static Filters filters(String provider, String owner, String repo) {
        return new Filters(ListingParams.text(provider), ListingParams.text(owner), ListingParams.text(repo));
    }
}
