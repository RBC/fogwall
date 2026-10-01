package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.git.GitClientUtils.SymbolCodes.*;
import static com.rbc.fogwall.git.GitClientUtils.sym;

import com.rbc.fogwall.db.UrlRuleRegistry;
import com.rbc.fogwall.db.model.FetchRefusal;
import com.rbc.fogwall.git.GitClientUtils;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.git.LifecycleStage;
import com.rbc.fogwall.git.PushStepKind;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.servlet.FetchDecision;
import com.rbc.fogwall.servlet.FogwallServlet;
import com.rbc.fogwall.servlet.GitDenialResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * HTTP adapter that applies URL allow/deny rules to git proxy requests. Rule evaluation is delegated entirely to
 * {@link UrlRuleEvaluator}; this class only handles extracting the request context and writing the HTTP response.
 *
 * <p>For push/fetch operations: evaluates rules and either passes the request down the chain or sends a git-protocol
 * error response. For {@code /info/refs} discovery: evaluates rules and sends the provider's denial status (default
 * 403).
 */
@Slf4j
@ToString
public final class UrlRuleAggregateFilter extends ProviderAwareFogwallFilter<FogwallProvider> {

    private final UrlRuleEvaluator evaluator;

    public UrlRuleAggregateFilter(FogwallProvider provider, UrlRuleRegistry urlRuleRegistry) {
        super(LifecycleStage.MANDATORY_PROCESSING, ALL_OPERATIONS, provider);
        this.evaluator = new UrlRuleEvaluator(urlRuleRegistry, provider);
    }

    @Override
    public Optional<PushStepKind> stepKind() {
        return Optional.of(PushStepKind.URL_RULE);
    }

    @Override
    public boolean terminatesChainOnFailure() {
        // A repository the pusher may not reach: refuse now, before any content is inspected.
        return true;
    }

    @Override
    public boolean skipForRefDeletion() {
        return false; // Deletions must still match an allow rule
    }

    @Override
    public boolean skipWhenPreApproved() {
        return false; // Re-pushes must still match an allow rule
    }

    @Override
    public void doHttpFilter(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var operation = determineOperation(request);

        if (operation == HttpOperation.INFO) {
            applyInfoRefsRules(request, response);
            return;
        }

        var details = (GitRequestDetails) request.getAttribute(FogwallServlet.GIT_REQUEST_ATTR);
        String slug = details != null ? details.getRepoRef().getSlug() : null;
        String owner = details != null ? details.getRepoRef().getOwner() : null;
        String name = details != null ? details.getRepoRef().getName() : null;

        UrlRuleEvaluator.Result result = evaluator.evaluate(slug, owner, name, operation);

        switch (result) {
            case UrlRuleEvaluator.Result.Denied d -> {
                log.debug("Blocked by deny rule: {}", d.ruleId());
                if (operation == HttpOperation.FETCH)
                    FetchDecision.blocked(request, FetchRefusal.DENY_RULE, d.ruleId());
                String action = operation == HttpOperation.PUSH ? "Push" : "Fetch";
                String title = sym(NO_ENTRY) + "  " + action + " Blocked - Repository Denied";
                String verb = operation == HttpOperation.PUSH ? "Pushes to" : "Fetches from";
                String message = verb + " this repository are not permitted.\n"
                        + "\n"
                        + "This repository has been explicitly denied by an administrator.";
                recordIssue(
                        request,
                        "Repository blocked by deny rule",
                        GitClientUtils.formatForOperation(title, message, GitClientUtils.AnsiColor.RED, operation));
            }
            case UrlRuleEvaluator.Result.Allowed a -> {
                log.debug("Allowed by rule: {}", a.ruleId());
                if (operation == HttpOperation.FETCH) FetchDecision.allowed(request, a.ruleId());
            }
            case UrlRuleEvaluator.Result.NotAllowed _ -> {
                log.debug("Blocked — no rule matched");
                if (operation == HttpOperation.FETCH)
                    FetchDecision.blocked(request, FetchRefusal.NOT_IN_ALLOW_LIST, null);
                sendNotAllowed(request, operation);
            }
        }
    }

    private void sendNotAllowed(HttpServletRequest request, HttpOperation operation) {
        String action = operation == HttpOperation.PUSH ? "Push" : "Fetch";
        String title = sym(NO_ENTRY) + "  " + action + " Blocked - Repository Not Allowed";
        String verb = operation == HttpOperation.PUSH ? "Pushes to" : "Fetches from";
        String message = verb + " this repository are not permitted.\n"
                + "\n"
                + "Contact an administrator to add this repository to the allow rules.";
        recordIssue(
                request,
                "Repository not in allow list",
                GitClientUtils.formatForOperation(title, message, GitClientUtils.AnsiColor.RED, operation));
    }

    /**
     * Applies URL allow/deny rules to an {@code /info/refs} discovery request. The effective operation (FETCH or PUSH)
     * is derived from the {@code service} query parameter. When blocked, responds with the provider-configured HTTP
     * status (default 403) and commits the response, so a refused repository is never opened or fetched from upstream.
     */
    private void applyInfoRefsRules(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String service = request.getParameter("service");
        HttpOperation effectiveOp =
                switch (service) {
                    case "git-upload-pack" -> HttpOperation.FETCH;
                    case "git-receive-pack" -> HttpOperation.PUSH;
                    default -> null; // unrecognised service — pass through
                };
        if (effectiveOp == null) return;

        var details = (GitRequestDetails) request.getAttribute(FogwallServlet.GIT_REQUEST_ATTR);
        String slug = details != null ? details.getRepoRef().getSlug() : null;
        String owner = details != null ? details.getRepoRef().getOwner() : null;
        String name = details != null ? details.getRepoRef().getName() : null;

        UrlRuleEvaluator.Result result = evaluator.evaluate(slug, owner, name, effectiveOp);

        switch (result) {
            case UrlRuleEvaluator.Result.Denied d -> {
                log.debug("Blocking /info/refs — matched deny rule: {}", d.ruleId());
                if (effectiveOp == HttpOperation.FETCH)
                    FetchDecision.blocked(request, FetchRefusal.DENY_RULE, d.ruleId());
                setResult(request, GitRequestDetails.GitResult.REJECTED, "Repository blocked by deny rule");
                GitDenialResponse.send(
                        request,
                        response,
                        provider.getBlockedInfoRefsStatus(),
                        "Repository access denied: this repository has been explicitly blocked by an administrator.");
            }
            case UrlRuleEvaluator.Result.NotAllowed _ -> {
                log.debug("Blocking /info/refs — no rule matched");
                if (effectiveOp == HttpOperation.FETCH)
                    FetchDecision.blocked(request, FetchRefusal.NOT_IN_ALLOW_LIST, null);
                setResult(request, GitRequestDetails.GitResult.REJECTED, "Repository not in allow rules");
                GitDenialResponse.send(
                        request,
                        response,
                        provider.getBlockedInfoRefsStatus(),
                        "Repository access denied: this repository is not in the allow list."
                                + " Contact an administrator to add it.");
            }
            case UrlRuleEvaluator.Result.Allowed a -> {
                if (effectiveOp == HttpOperation.FETCH) FetchDecision.allowed(request, a.ruleId());
            }
        }
    }
}
