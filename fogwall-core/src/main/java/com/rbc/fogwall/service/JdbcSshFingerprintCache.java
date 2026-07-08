package com.rbc.fogwall.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JDBC-backed cache for SSH public key fingerprints fetched from upstream SCM providers.
 *
 * <p>Fingerprints are stored as a comma-separated string in a single column. The TTL is enforced on read — expired
 * entries are treated as absent and overwritten on the next successful fetch.
 */
public class JdbcSshFingerprintCache implements SshFingerprintCache {

    private static final Logger log = LoggerFactory.getLogger(JdbcSshFingerprintCache.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Duration maxAge;

    public JdbcSshFingerprintCache(DataSource dataSource, Duration maxAge) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.maxAge = maxAge;
    }

    @Override
    public Set<String> lookup(String provider, String scmLogin) {
        Timestamp cutoff = Timestamp.from(Instant.now().minus(maxAge));
        List<String> rows = jdbc.queryForList(
                "SELECT fingerprints FROM ssh_fingerprint_cache"
                        + " WHERE provider = :provider AND scm_login = :login AND cached_at >= :cutoff",
                Map.of("provider", provider, "login", scmLogin, "cutoff", cutoff),
                String.class);
        if (rows.isEmpty()) return Set.of();
        log.debug("SSH fingerprint cache hit: provider={}, login={}", provider, scmLogin);
        return Arrays.stream(rows.get(0).split(",")).collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public void store(String provider, String scmLogin, Set<String> fingerprints) {
        String joined = new TreeSet<>(fingerprints).stream().collect(Collectors.joining(","));
        var params = Map.of(
                "provider", provider,
                "login", scmLogin,
                "fingerprints", joined,
                "now", Timestamp.from(Instant.now()));
        tx.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM ssh_fingerprint_cache WHERE provider = :provider AND scm_login = :login", params);
            jdbc.update(
                    "INSERT INTO ssh_fingerprint_cache (provider, scm_login, fingerprints, cached_at)"
                            + " VALUES (:provider, :login, :fingerprints, :now)",
                    params);
        });
        log.debug("SSH fingerprints cached: provider={}, login={}", provider, scmLogin);
    }

    @Override
    public void evict(String provider, String scmLogin) {
        int deleted = jdbc.update(
                "DELETE FROM ssh_fingerprint_cache WHERE provider = :provider AND scm_login = :login",
                Map.of("provider", provider, "login", scmLogin));
        if (deleted > 0) {
            log.debug("SSH fingerprint cache evicted: provider={}, login={}", provider, scmLogin);
        }
    }
}
