package com.rbc.fogwall.config;

import com.rbc.fogwall.servlet.filter.FogwallFilter;
import java.util.List;

/** Interface for loading filter configuration from various sources (in-memory, configuration files, databases, etc.) */
public interface FilterConfigurationSource {

    /**
     * Get all configured filters for a specific provider
     *
     * @param providerName Name of the provider
     * @return List of filters to apply for this provider
     */
    List<FogwallFilter> getFiltersForProvider(String providerName);

    /**
     * Get all configured filters
     *
     * @return List of all filters
     */
    List<FogwallFilter> getAllFilters();
}
