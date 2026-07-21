package com.rbc.fogwall.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rbc.fogwall.config.ContentPatternConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContentPatternBundleResolverTest {

    @Test
    void disabled_resolvesEmpty() {
        var config = ContentPatternConfig.builder()
                .enabled(false)
                .bundles(List.of("national-id-us"))
                .build();

        assertTrue(ContentPatternBundleResolver.resolve(config).isEmpty());
    }

    @Test
    void enabledWithNoBundlesSelected_resolvesEmpty() {
        var config =
                ContentPatternConfig.builder().enabled(true).bundles(List.of()).build();

        assertTrue(ContentPatternBundleResolver.resolve(config).isEmpty());
    }

    @Test
    void enabledWithBundlesSelected_resolvesOnlyThoseBundles() {
        var config = ContentPatternConfig.builder()
                .enabled(true)
                .bundles(List.of("national-id-us", "national-id-gb"))
                .build();

        List<PatternBundle> resolved = ContentPatternBundleResolver.resolve(config);

        assertEquals(2, resolved.size());
        assertTrue(resolved.stream().anyMatch(b -> b.name().equals("national-id-us")));
        assertTrue(resolved.stream().anyMatch(b -> b.name().equals("national-id-gb")));
        assertTrue(resolved.stream().noneMatch(b -> b.name().equals("national-id-ca")));
    }

    @Test
    void unknownBundleName_isSilentlyIgnored() {
        var config = ContentPatternConfig.builder()
                .enabled(true)
                .bundles(List.of("national-id-nonexistent"))
                .build();

        assertTrue(ContentPatternBundleResolver.resolve(config).isEmpty());
    }

    @Test
    void nationalIdAllGeosAlias_resolvesToEveryNationalIdBundle() {
        var config = ContentPatternConfig.builder()
                .enabled(true)
                .bundles(List.of("national-id-all-geos"))
                .build();

        List<PatternBundle> resolved = ContentPatternBundleResolver.resolve(config);

        assertEquals(17, resolved.size());
        assertTrue(resolved.stream().allMatch(b -> b.name().startsWith("national-id-")));
    }

    @Test
    void aliasAndConcreteName_canBeMixedWithoutDuplicates() {
        var config = ContentPatternConfig.builder()
                .enabled(true)
                .bundles(List.of("generic-all", "generic-iban"))
                .build();

        List<PatternBundle> resolved = ContentPatternBundleResolver.resolve(config);

        assertEquals(4, resolved.size());
        assertEquals(
                1,
                resolved.stream().filter(b -> b.name().equals("generic-iban")).count(),
                "generic-iban selected via both the alias and its own name should not duplicate");
    }
}
