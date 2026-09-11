package com.rbc.fogwall.dashboard.controller;

import com.rbc.fogwall.dashboard.audit.AdminAuditLog;
import com.rbc.fogwall.git.LocalRepositoryCache;
import com.rbc.fogwall.git.LocalRepositoryCache.CacheEntrySummary;
import com.rbc.fogwall.git.LocalRepositoryCache.RefInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin REST endpoints for inspecting and invalidating the local-mirror caches (fogwall#340).
 *
 * <p>fogwall keeps a local bare mirror of each proxied repository to inspect push content — two independent caches, one
 * for server mode ({@code server}, the old "store-and-forward") and one for transparent proxy ({@code proxy}). These
 * mirrors can go stale or poisoned; before this existed the only recovery was a pod restart. These endpoints give an
 * operator visibility into cache state (which repos are mirrored, age, last fetch, on-disk size, ref count) and let
 * them invalidate a single mirror or every mirror in one mode.
 *
 * <p>Scope is per-pod: each pod serves its own in-memory cache, so an operator inspects/invalidates the pod they reach
 * (via port-forward or the pod endpoint), consistent with existing Kubernetes operational patterns. Nothing here is
 * distributed.
 *
 * <p>All endpoints require {@code ROLE_ADMIN} (gated by {@code /api/admin/**} in {@code SecurityConfig}). Every
 * mutating call is logged with the acting admin's login for auditability.
 */
@Tag(name = "Admin", description = "Administrative operations — requires ROLE_ADMIN")
@RestController
@RequestMapping("/api/admin/cache")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCacheController {

    private final LocalRepositoryCache serverCache;
    private final LocalRepositoryCache proxyCache;
    private final AdminAuditLog auditLog;

    public AdminCacheController(
            @Qualifier("serverCache") LocalRepositoryCache serverCache,
            @Qualifier("proxyCache") LocalRepositoryCache proxyCache,
            AdminAuditLog auditLog) {
        this.serverCache = serverCache;
        this.proxyCache = proxyCache;
        this.auditLog = auditLog;
    }

    @Operation(
            operationId = "listCache",
            summary = "List cached local mirrors",
            description = "Returns the mirrors held by each mode's cache: server (store-and-forward) and proxy.")
    @GetMapping
    public Map<String, List<CacheEntrySummary>> list() {
        return Map.of(
                "server", serverCache.listEntries(),
                "proxy", proxyCache.listEntries());
    }

    @Operation(
            operationId = "listCacheRefs",
            summary = "List refs in one cached mirror",
            description = "Branches and tags present in the mirror identified by mode + cache key.")
    @GetMapping("/refs")
    public ResponseEntity<List<RefInfo>> refs(@RequestParam String mode, @RequestParam String key) {
        LocalRepositoryCache cache = cacheFor(mode);
        if (cache == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(cache.listRefs(key));
    }

    @Operation(
            operationId = "invalidateCacheEntry",
            summary = "Invalidate one cached mirror",
            description = "Removes the mirror identified by mode + cache key, deleting its local clone.")
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> invalidate(@RequestParam String mode, @RequestParam String key)
            throws IOException {
        LocalRepositoryCache cache = cacheFor(mode);
        if (cache == null) {
            return ResponseEntity.badRequest().build();
        }
        boolean removed = cache.removeByKey(key);
        auditLog.success("cache.invalidate", "cache:" + mode + ":" + key, "removed=" + removed);
        return ResponseEntity.ok(Map.of("removed", removed));
    }

    @Operation(
            operationId = "invalidateCacheAll",
            summary = "Invalidate all cached mirrors in a mode",
            description = "Removes every mirror in the given mode's cache, deleting each local clone.")
    @DeleteMapping("/all")
    public ResponseEntity<Map<String, Object>> invalidateAll(@RequestParam String mode) throws IOException {
        LocalRepositoryCache cache = cacheFor(mode);
        if (cache == null) {
            return ResponseEntity.badRequest().build();
        }
        int count = cache.invalidateAll();
        auditLog.success("cache.invalidate_all", "cache:" + mode, "count=" + count);
        return ResponseEntity.ok(Map.of("invalidated", count));
    }

    /** Maps a mode name to its cache instance, or {@code null} for an unknown mode (→ 400). */
    private LocalRepositoryCache cacheFor(String mode) {
        return switch (mode) {
            case "server" -> serverCache;
            case "proxy" -> proxyCache;
            default -> null;
        };
    }
}
