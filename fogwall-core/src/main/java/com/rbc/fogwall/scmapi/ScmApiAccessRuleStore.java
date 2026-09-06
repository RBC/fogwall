package com.rbc.fogwall.scmapi;

import java.util.List;

/** Storage abstraction for {@link ScmApiAccessRule}s. Implementations exist for JDBC and MongoDB. */
public interface ScmApiAccessRuleStore {

    /** Persist a new rule. */
    void save(ScmApiAccessRule rule);

    /** Delete a rule by ID. */
    void delete(String id);

    /** Return all rules. */
    List<ScmApiAccessRule> findAll();

    /** Return all rules for the given provider. */
    List<ScmApiAccessRule> findByProvider(String provider);

    /** Initialize the store (create tables/indexes). Called once at startup. */
    void initialize();

    /**
     * Seeds rules from config on startup. Clears all CONFIG-sourced rows and re-inserts to keep YAML authoritative;
     * DB-sourced rows are left untouched. Mirrors {@code UrlRuleRegistry#seedFromConfig}.
     */
    default void seedFromConfig(List<ScmApiAccessRule> rules) {
        findAll().stream()
                .filter(r -> r.getSource() == ScmApiAccessRule.Source.CONFIG)
                .forEach(r -> delete(r.getId()));
        rules.forEach(this::save);
    }
}
