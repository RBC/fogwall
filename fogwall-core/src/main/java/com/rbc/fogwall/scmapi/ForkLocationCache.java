package com.rbc.fogwall.scmapi;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Remembers which repository is a head owner's fork of an upstream, by numeric repository ID, so a repeat pull request
 * from the same owner skips the fork search. An ID survives a rename and is not reused by a repository deleted and
 * recreated under the same name; the caller still re-checks the repository's parent and owner on every hit, so an entry
 * only ever saves the search, never vouches for a repository.
 *
 * <p>Bounded and in-memory. Entries expire after {@link #TTL}; expired entries are dropped on insert, and when full the
 * oldest entry makes room. All methods are thread-safe.
 */
class ForkLocationCache {

    static final Duration TTL = Duration.ofHours(6);
    static final int MAX_ENTRIES = 10_000;

    /**
     * Provider, upstream and head owner, lower-cased: Gitea/Forgejo owner and repository names are case-insensitive.
     */
    record Key(String provider, String upstreamOwner, String upstreamName, String headOwner) {

        static Key of(String provider, OwnerRepo upstream, String headOwner) {
            return new Key(lower(provider), lower(upstream.owner()), lower(upstream.name()), lower(headOwner));
        }

        private static String lower(String value) {
            return value.toLowerCase(Locale.ROOT);
        }
    }

    private record Entry(long repositoryId, Instant expiresAt) {}

    private final Clock clock;
    private final int maxEntries;

    /** Insertion order, refreshed on every put, so the first entry is always the oldest and the first to expire. */
    private final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<>();

    ForkLocationCache() {
        this(Clock.systemUTC(), MAX_ENTRIES);
    }

    ForkLocationCache(Clock clock, int maxEntries) {
        this.clock = clock;
        this.maxEntries = maxEntries;
    }

    synchronized OptionalLong get(Key key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return OptionalLong.empty();
        }
        if (!clock.instant().isBefore(entry.expiresAt())) {
            entries.remove(key);
            return OptionalLong.empty();
        }
        return OptionalLong.of(entry.repositoryId());
    }

    synchronized void put(Key key, long repositoryId) {
        Instant now = clock.instant();
        entries.remove(key);
        Iterator<Map.Entry<Key, Entry>> oldestFirst = entries.entrySet().iterator();
        while (oldestFirst.hasNext()) {
            if (!now.isBefore(oldestFirst.next().getValue().expiresAt()) || entries.size() >= maxEntries) {
                oldestFirst.remove();
            } else {
                break;
            }
        }
        entries.put(key, new Entry(repositoryId, now.plus(TTL)));
    }

    /** Drops {@code key} only while it still maps to {@code repositoryId}, so a concurrent refresh is kept. */
    synchronized void invalidate(Key key, long repositoryId) {
        Entry entry = entries.get(key);
        if (entry != null && entry.repositoryId() == repositoryId) {
            entries.remove(key);
        }
    }

    synchronized int size() {
        return entries.size();
    }
}
