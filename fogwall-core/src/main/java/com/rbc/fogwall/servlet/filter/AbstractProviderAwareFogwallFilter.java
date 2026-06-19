package com.rbc.fogwall.servlet.filter;

import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.provider.AbstractFogwallProvider;
import com.rbc.fogwall.provider.FogwallProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

/**
 * A {@link FogwallFilter} that is aware of the {@link FogwallProvider} it is applied to. The base class handles the
 * matching of the request URI to the hostname of the target URI of a provider. The application registers instances of
 * these Filters against corresponding servlets that proxy all known {@link AbstractFogwallProvider} instances in the
 * application context.
 *
 * <p>This class enables the main use case below:
 *
 * <pre>
 *     Git HTTP client initiates request to {applicationUrl}/{providerHostname}/{providerPath...}
 *     -> Find Filter instances that match hostname of {@link AbstractFogwallProvider#getUri()}
 *     -> Execute {@link #doHttpFilter(HttpServletRequest, HttpServletResponse)}
 * </pre>
 *
 * <p>The <a href="https://git-scm.com/docs/http-protocol">Git http protocol is stateless</a> and therefore each
 * implementing Filter only has to implement {@link #doHttpFilter} which is explicitly cast to
 * {@link HttpServletRequest} and {@link HttpServletResponse}.
 */
@Slf4j
public abstract class AbstractProviderAwareFogwallFilter extends AbstractFogwallFilter
        implements ProviderAwareFogwallFilter {

    protected final FogwallProvider provider;
    private final String pathPrefix;

    public AbstractProviderAwareFogwallFilter(
            int order, Set<HttpOperation> appliedOperations, FogwallProvider provider) {
        this(order, appliedOperations, provider, "");
    }

    public AbstractProviderAwareFogwallFilter(
            int order, Set<HttpOperation> appliedOperations, FogwallProvider provider, String pathPrefix) {
        super(order, appliedOperations);
        this.provider = provider;
        this.pathPrefix = pathPrefix != null ? pathPrefix : "";
    }

    /**
     * Applies this fogwallFilter to all git operations for a given provider.
     *
     * @param order the order the Filters are applied
     * @param provider provider which this filter applies to
     */
    public AbstractProviderAwareFogwallFilter(int order, FogwallProvider provider) {
        this(order, ALL_OPERATIONS, provider, "");
    }

    /**
     * Applies this fogwallFilter to all git operations for a given provider, with a path prefix for the servlet mapping
     * (e.g. {@code /proxy} for transparent proxy mode).
     *
     * @param order the order the Filters are applied
     * @param provider provider which this filter applies to
     * @param pathPrefix prefix prepended to the provider's servlet path for URI matching
     */
    public AbstractProviderAwareFogwallFilter(int order, FogwallProvider provider, String pathPrefix) {
        this(order, ALL_OPERATIONS, provider, pathPrefix);
    }

    @Override
    public Predicate<HttpServletRequest> shouldFilter() {
        String expectedPath = pathPrefix + provider.servletPath();
        return super.shouldFilter()
                .and((HttpServletRequest request) -> request.getRequestURI().startsWith(expectedPath));
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" + "provider="
                + provider.getName() + ", order="
                + order + ", appliedOperations="
                + applicableOperations + '}';
    }
}
