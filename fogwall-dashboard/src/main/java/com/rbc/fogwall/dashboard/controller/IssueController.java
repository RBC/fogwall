package com.rbc.fogwall.dashboard.controller;

import com.rbc.fogwall.dashboard.issues.DashboardIssueClient;
import com.rbc.fogwall.dashboard.issues.DashboardIssueService;
import com.rbc.fogwall.dashboard.issues.DashboardIssueService.IssueDetailsOutcome;
import com.rbc.fogwall.dashboard.issues.DashboardIssueService.IssueOutcome;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Files and follows up on issues through the dashboard — create, edit title/body, comment — on a permitted repository,
 * performed by fogwall on the user's behalf with their linked OAuth token. The first dashboard SCM write path; the
 * {@link com.rbc.fogwall.dashboard.issues.DashboardIssueService} enforces the {@code ISSUE}/{@code PROPOSE} grant,
 * content inspection and auditing.
 */
@Tag(name = "Issues", description = "File and follow up on issues through the dashboard")
@RestController
@RequestMapping("/api/issues")
@RequiredArgsConstructor
public class IssueController {

    private final DashboardIssueService issueService;

    public record CreateIssueRequest(String provider, String owner, String repo, String title, String body) {}

    public record EditIssueRequest(String provider, String owner, String repo, int number, String title, String body) {}

    public record CommentRequest(String provider, String owner, String repo, int number, String body) {}

    public record StateRequest(String provider, String owner, String repo, int number, boolean close) {}

    @Operation(operationId = "listIssueProviders", summary = "Providers the current user may file issues on")
    @GetMapping("/providers")
    public List<Map<String, String>> providers() {
        return issueService.eligibleProviders(currentUsername()).stream()
                .map(name -> Map.of("name", name))
                .toList();
    }

    @Operation(operationId = "createIssue", summary = "Create an issue on a permitted repository")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateIssueRequest req) {
        if (isBlank(req.title())) {
            return ResponseEntity.badRequest().body(Map.of("error", "A title is required"));
        }
        return respond(issueService.createIssue(
                currentUsername(), req.provider(), req.owner(), req.repo(), req.title(), req.body()));
    }

    @Operation(operationId = "editIssue", summary = "Edit an issue's title or body")
    @PatchMapping
    public ResponseEntity<?> edit(@RequestBody EditIssueRequest req) {
        if (isBlank(req.title()) && isBlank(req.body())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Provide a new title or body"));
        }
        return respond(issueService.editIssue(
                currentUsername(),
                req.provider(),
                req.owner(),
                req.repo(),
                req.number(),
                blankToNull(req.title()),
                blankToNull(req.body())));
    }

    @Operation(operationId = "commentIssue", summary = "Comment on an issue")
    @PostMapping("/comment")
    public ResponseEntity<?> comment(@RequestBody CommentRequest req) {
        if (isBlank(req.body())) {
            return ResponseEntity.badRequest().body(Map.of("error", "A comment body is required"));
        }
        return respond(issueService.comment(
                currentUsername(), req.provider(), req.owner(), req.repo(), req.number(), req.body()));
    }

    @Operation(operationId = "setIssueState", summary = "Close or reopen an issue")
    @PostMapping("/state")
    public ResponseEntity<?> setState(@RequestBody StateRequest req) {
        return respond(issueService.setState(
                currentUsername(), req.provider(), req.owner(), req.repo(), req.number(), req.close()));
    }

    @Operation(operationId = "getCurrentIssue", summary = "Read an issue's current title, body and state")
    @GetMapping("/current")
    public ResponseEntity<?> current(
            @RequestParam String provider,
            @RequestParam String owner,
            @RequestParam String repo,
            @RequestParam int number) {
        IssueDetailsOutcome outcome = issueService.current(currentUsername(), provider, owner, repo, number);
        if (outcome.ok()) {
            DashboardIssueClient.IssueDetails d = outcome.details();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("number", d.number());
            body.put("url", d.url());
            body.put("title", d.title());
            body.put("body", d.body());
            body.put("state", d.state());
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.status(outcome.httpStatus()).body(Map.of("error", outcome.error()));
    }

    private static ResponseEntity<?> respond(IssueOutcome outcome) {
        if (outcome.ok()) {
            DashboardIssueClient.IssueResult result = outcome.result();
            return ResponseEntity.status(outcome.httpStatus())
                    .body(Map.of("number", result.number(), "url", result.url()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", outcome.error());
        if (!outcome.violations().isEmpty()) {
            body.put("violations", outcome.violations());
        }
        return ResponseEntity.status(outcome.httpStatus()).body(body);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s;
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
