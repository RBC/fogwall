package com.rbc.fogwall.scmapi;

import java.util.List;
import java.util.Optional;

/**
 * Pure-logic evaluator for {@link ScmApiAccessRule}s — provider-level allow/deny for SCM API proxy traffic. Mirrors
 * {@code UrlRuleEvaluator}'s fail-closed posture but at coarser (provider-only) granularity; see
 * {@link ScmApiAccessRule}'s javadoc for why the two aren't the same mechanism.
 *
 * <p>Deny takes precedence over allow when both match; if nothing matches, the request is fail-closed
 * ({@link Result.NotAllowed}).
 */
public class ScmApiAccessEvaluator {

    /** Outcome of a single rule evaluation pass. */
    public sealed interface Result permits Result.Allowed, Result.Denied, Result.NotAllowed {

        /** An allow rule matched — request may proceed. */
        record Allowed(String ruleId) implements Result {}

        /** A deny rule matched — request must be rejected. */
        record Denied(String ruleId) implements Result {}

        /** No rule matched — request must be rejected (fail-closed). */
        record NotAllowed() implements Result {}
    }

    private final ScmApiAccessRuleStore store;

    public ScmApiAccessEvaluator(ScmApiAccessRuleStore store) {
        this.store = store;
    }

    public Result evaluate(String provider, ScmApiAccessRule.Operation operation) {
        List<ScmApiAccessRule> matches = store.findByProvider(provider).stream()
                .filter(r -> matchesOperation(r.getOperation(), operation))
                .toList();

        Optional<ScmApiAccessRule> deny = matches.stream()
                .filter(r -> r.getAccess() == ScmApiAccessRule.Access.DENY)
                .findFirst();
        if (deny.isPresent()) {
            return new Result.Denied(deny.get().getId());
        }

        Optional<ScmApiAccessRule> allow = matches.stream()
                .filter(r -> r.getAccess() == ScmApiAccessRule.Access.ALLOW)
                .findFirst();
        return allow.<Result>map(r -> new Result.Allowed(r.getId())).orElseGet(Result.NotAllowed::new);
    }

    private static boolean matchesOperation(ScmApiAccessRule.Operation ruleOp, ScmApiAccessRule.Operation requested) {
        return ruleOp == requested || ruleOp == ScmApiAccessRule.Operation.BOTH;
    }
}
