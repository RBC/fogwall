package com.rbc.fogwall.db.jdbc;

import com.rbc.fogwall.db.UrlRuleRegistry;
import com.rbc.fogwall.db.jdbc.mapper.AccessRuleRowMapper;
import com.rbc.fogwall.db.model.AccessRule;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** JDBC-backed {@link UrlRuleRegistry}. Works with H2 and PostgreSQL. */
public class JdbcUrlRuleRegistry implements UrlRuleRegistry {

    private final DataSource dataSource;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcUrlRuleRegistry(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Override
    public void initialize() {
        DatabaseMigrator.migrate(dataSource);
    }

    @Override
    public void save(AccessRule rule) {
        jdbc.update("""
                INSERT INTO access_rules
                    (id, provider, target, match_value, match_type, access, operations,
                     description, enabled, rule_order, source)
                VALUES
                    (:id, :provider, :target, :matchValue, :matchType, :access, :operations,
                     :description, :enabled, :ruleOrder, :source)
                """, params(rule));
    }

    @Override
    public void update(AccessRule rule) {
        jdbc.update("""
                UPDATE access_rules SET
                    provider = :provider, target = :target, match_value = :matchValue, match_type = :matchType,
                    access = :access, operations = :operations, description = :description,
                    enabled = :enabled, rule_order = :ruleOrder, source = :source
                WHERE id = :id
                """, params(rule));
    }

    @Override
    public void delete(String id) {
        jdbc.update("DELETE FROM access_rules WHERE id = :id", Map.of("id", id));
    }

    @Override
    public Optional<AccessRule> findById(String id) {
        List<AccessRule> rows =
                jdbc.query("SELECT * FROM access_rules WHERE id = :id", Map.of("id", id), AccessRuleRowMapper.INSTANCE);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<AccessRule> findAll() {
        return jdbc.query("SELECT * FROM access_rules ORDER BY rule_order ASC, id ASC", AccessRuleRowMapper.INSTANCE);
    }

    @Override
    public List<AccessRule> findEnabledForProvider(String provider) {
        return jdbc.query("""
                SELECT * FROM access_rules
                WHERE enabled = TRUE
                  AND (provider IS NULL OR provider = :provider)
                ORDER BY rule_order ASC, id ASC
                """, Map.of("provider", provider), AccessRuleRowMapper.INSTANCE);
    }

    private static MapSqlParameterSource params(AccessRule rule) {
        return new MapSqlParameterSource()
                .addValue("id", rule.getId())
                .addValue("provider", rule.getProvider())
                .addValue("target", rule.getTarget().name())
                .addValue("matchValue", rule.getValue())
                .addValue("matchType", rule.getMatchType().name())
                .addValue("access", rule.getAccess().name())
                .addValue("operations", rule.getOperations().name())
                .addValue("description", rule.getDescription())
                .addValue("enabled", rule.isEnabled())
                .addValue("ruleOrder", rule.getRuleOrder())
                .addValue("source", rule.getSource().name());
    }
}
