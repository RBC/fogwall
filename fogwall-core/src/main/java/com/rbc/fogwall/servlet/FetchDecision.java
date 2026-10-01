package com.rbc.fogwall.servlet;

import com.rbc.fogwall.db.model.FetchActivity;
import com.rbc.fogwall.db.model.FetchRefusal;
import jakarta.servlet.ServletRequest;
import java.util.Optional;

/**
 * fogwall's decision on a clone or fetch request, carried on the request by whichever component made it and counted by
 * {@code FetchActivityFilter} once the chain has run. A refusal always replaces what came before it; an allow never
 * replaces a refusal, so a request a later check refused is never counted as allowed.
 *
 * @param refusal why the request was refused; null for an allowed one
 * @param ruleId the URL rule that matched; null when no rule matched or a refusal was not a rule's
 */
public record FetchDecision(FetchActivity.Result result, FetchRefusal refusal, String ruleId) {

    private static final String ATTRIBUTE = "com.rbc.fogwall.fetchDecision";

    public static void allowed(ServletRequest request, String ruleId) {
        if (of(request).map(d -> d.result() == FetchActivity.Result.BLOCKED).orElse(false)) {
            return;
        }
        request.setAttribute(ATTRIBUTE, new FetchDecision(FetchActivity.Result.ALLOWED, null, ruleId));
    }

    public static void blocked(ServletRequest request, FetchRefusal refusal, String ruleId) {
        request.setAttribute(ATTRIBUTE, new FetchDecision(FetchActivity.Result.BLOCKED, refusal, ruleId));
    }

    public static Optional<FetchDecision> of(ServletRequest request) {
        return request.getAttribute(ATTRIBUTE) instanceof FetchDecision d ? Optional.of(d) : Optional.empty();
    }
}
