package com.rbc.fogwall.servlet.filter;

import com.rbc.fogwall.git.LifecycleStage;

/**
 * A built-in filter that runs in one of the mandatory lifecycle stages ({@link LifecycleStage#MANDATORY_PRE},
 * {@link LifecycleStage#MANDATORY_PROCESSING}, {@link LifecycleStage#MANDATORY_POST}) — the static core of fogwall that
 * must run on every request and is closed to external extension.
 *
 * <p>This type is {@code sealed} to {@code fogwall-core}: its permitted implementers are the base classes
 * {@link AbstractFogwallFilter} / {@link ProviderAwareFogwallFilter} and the two infrastructure filters that implement
 * it directly. External code therefore cannot implement a mandatory-stage filter — the only extension surface is
 * {@link CustomFogwallFilter}. That closure is a compile-time property, not a documented promise.
 */
public sealed interface MandatoryFogwallFilter extends FogwallFilter
        permits AbstractFogwallFilter, AuditFilter, ForceGitClientFilter, LfsRejectionFilter {}
