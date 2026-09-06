package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScmApiAccessEvaluatorTest {

    private final List<ScmApiAccessRule> rules = new ArrayList<>();
    private ScmApiAccessEvaluator evaluator;

    @BeforeEach
    void setUp() {
        rules.clear();
        ScmApiAccessRuleStore store = new ScmApiAccessRuleStore() {
            @Override
            public void save(ScmApiAccessRule rule) {
                rules.add(rule);
            }

            @Override
            public void delete(String id) {
                rules.removeIf(r -> r.getId().equals(id));
            }

            @Override
            public List<ScmApiAccessRule> findAll() {
                return List.copyOf(rules);
            }

            @Override
            public List<ScmApiAccessRule> findByProvider(String provider) {
                return rules.stream()
                        .filter(r -> r.getProvider().equals(provider))
                        .toList();
            }

            @Override
            public void initialize() {}
        };
        evaluator = new ScmApiAccessEvaluator(store);
    }

    @Test
    void noRules_failsClosed() {
        assertInstanceOf(
                ScmApiAccessEvaluator.Result.NotAllowed.class,
                evaluator.evaluate("github", ScmApiAccessRule.Operation.READ));
    }

    @Test
    void allowRuleForRead_allowsRead() {
        rules.add(ScmApiAccessRule.builder()
                .provider("github")
                .operation(ScmApiAccessRule.Operation.READ)
                .access(ScmApiAccessRule.Access.ALLOW)
                .build());
        assertInstanceOf(
                ScmApiAccessEvaluator.Result.Allowed.class,
                evaluator.evaluate("github", ScmApiAccessRule.Operation.READ));
    }

    @Test
    void allowRuleForRead_doesNotAllowMutate() {
        rules.add(ScmApiAccessRule.builder()
                .provider("github")
                .operation(ScmApiAccessRule.Operation.READ)
                .access(ScmApiAccessRule.Access.ALLOW)
                .build());
        assertInstanceOf(
                ScmApiAccessEvaluator.Result.NotAllowed.class,
                evaluator.evaluate("github", ScmApiAccessRule.Operation.MUTATE));
    }

    @Test
    void allowRuleForBoth_allowsReadAndMutate() {
        rules.add(ScmApiAccessRule.builder()
                .provider("github")
                .operation(ScmApiAccessRule.Operation.BOTH)
                .access(ScmApiAccessRule.Access.ALLOW)
                .build());
        assertInstanceOf(
                ScmApiAccessEvaluator.Result.Allowed.class,
                evaluator.evaluate("github", ScmApiAccessRule.Operation.READ));
        assertInstanceOf(
                ScmApiAccessEvaluator.Result.Allowed.class,
                evaluator.evaluate("github", ScmApiAccessRule.Operation.MUTATE));
    }

    @Test
    void denyTakesPrecedenceOverAllow() {
        rules.add(ScmApiAccessRule.builder()
                .provider("github")
                .operation(ScmApiAccessRule.Operation.BOTH)
                .access(ScmApiAccessRule.Access.ALLOW)
                .build());
        rules.add(ScmApiAccessRule.builder()
                .provider("github")
                .operation(ScmApiAccessRule.Operation.READ)
                .access(ScmApiAccessRule.Access.DENY)
                .build());
        assertInstanceOf(
                ScmApiAccessEvaluator.Result.Denied.class,
                evaluator.evaluate("github", ScmApiAccessRule.Operation.READ));
        assertInstanceOf(
                ScmApiAccessEvaluator.Result.Allowed.class,
                evaluator.evaluate("github", ScmApiAccessRule.Operation.MUTATE));
    }

    @Test
    void otherProvider_notAffectedByRule() {
        rules.add(ScmApiAccessRule.builder()
                .provider("github")
                .operation(ScmApiAccessRule.Operation.BOTH)
                .access(ScmApiAccessRule.Access.ALLOW)
                .build());
        assertInstanceOf(
                ScmApiAccessEvaluator.Result.NotAllowed.class,
                evaluator.evaluate("gitlab", ScmApiAccessRule.Operation.READ));
    }
}
