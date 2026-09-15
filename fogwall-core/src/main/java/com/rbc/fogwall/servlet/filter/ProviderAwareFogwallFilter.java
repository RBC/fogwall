package com.rbc.fogwall.servlet.filter;

import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.git.LifecycleStage;
import com.rbc.fogwall.provider.FogwallProvider;
import java.util.Set;

/**
 * A {@link FogwallFilter} that holds the {@link FogwallProvider} it was constructed for, for filter logic that needs a
 * provider reference (e.g. resolving the upstream URI, calling a provider's API). Registration already scopes each
 * filter instance to its provider's URL pattern ({@code FogwallServletRegistrar}), so this class does not re-check that
 * scoping itself — {@link #shouldFilter()} is inherited unchanged from {@link AbstractFogwallFilter} and only gates on
 * applicable {@link HttpOperation}s.
 *
 * @param <P> the provider type. Most filters only need generic provider access and should use {@link FogwallProvider}
 *     directly; narrow this to a concrete subtype only for filters with genuinely provider-type-specific logic (e.g.
 *     {@code BitbucketIdentityFilter} needs {@code BitbucketProvider}).
 */
public abstract sealed class ProviderAwareFogwallFilter<P extends FogwallProvider> extends AbstractFogwallFilter
        permits BitbucketIdentityFilter, EnrichPushCommitsFilter, ParseGitRequestFilter, UrlRuleAggregateFilter {

    protected final P provider;

    public ProviderAwareFogwallFilter(LifecycleStage stage, Set<HttpOperation> appliedOperations, P provider) {
        super(stage, appliedOperations);
        this.provider = provider;
    }

    /** Applies this filter to all git operations for the given provider. */
    public ProviderAwareFogwallFilter(LifecycleStage stage, P provider) {
        this(stage, ALL_OPERATIONS, provider);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" + "provider="
                + provider.getName() + ", stage="
                + stage + ", appliedOperations="
                + applicableOperations + '}';
    }
}
