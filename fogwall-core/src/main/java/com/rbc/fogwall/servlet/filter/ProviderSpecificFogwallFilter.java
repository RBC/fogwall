package com.rbc.fogwall.servlet.filter;

import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.provider.FogwallProvider;
import java.util.Set;

public abstract class ProviderSpecificFogwallFilter<P extends FogwallProvider>
        extends AbstractProviderAwareFogwallFilter {

    protected final P provider;

    public ProviderSpecificFogwallFilter(int order, Set<HttpOperation> appliedOperations, P provider) {
        super(order, appliedOperations, provider);
        this.provider = provider;
    }
}
