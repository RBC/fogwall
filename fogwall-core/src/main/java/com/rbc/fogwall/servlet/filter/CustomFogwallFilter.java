package com.rbc.fogwall.servlet.filter;

import com.rbc.fogwall.git.LifecycleStage;

/**
 * The extension surface for filters contributed outside the static core. A custom filter runs in one of the custom
 * lifecycle stages ({@link LifecycleStage#CUSTOM_PRE} or {@link LifecycleStage#CUSTOM_POST}) — the only stages open to
 * external code — and so can never displace or precede a mandatory step such as request parsing.
 *
 * <p>This type is {@code non-sealed}: implement it (typically via {@link AbstractCustomFogwallFilter}) to add a filter.
 * The plugin SPI builds its filter-facing contract on top of this type.
 */
public non-sealed interface CustomFogwallFilter extends FogwallFilter {}
