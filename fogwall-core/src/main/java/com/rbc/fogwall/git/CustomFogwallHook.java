package com.rbc.fogwall.git;

/**
 * The extension surface for server-mode hooks contributed outside the static core. A custom hook runs in a custom
 * lifecycle stage ({@link LifecycleStage#CUSTOM_PRE} or {@link LifecycleStage#CUSTOM_POST}) — the only stages open to
 * external code — so it can never precede a mandatory step. This type is {@code non-sealed}: the plugin SPI builds its
 * hook-facing contract on top of it.
 */
public non-sealed interface CustomFogwallHook extends FogwallHook {}
