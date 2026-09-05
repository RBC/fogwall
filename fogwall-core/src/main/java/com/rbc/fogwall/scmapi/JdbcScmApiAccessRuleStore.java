package com.rbc.fogwall.scmapi;

import com.rbc.fogwall.db.jdbc.DatabaseMigrator;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** JDBC-based {@link ScmApiAccessRuleStore} implementation. Works with H2, PostgreSQL, MySQL, and MariaDB. */
public class JdbcScmApiAccessRuleStore implements ScmApiAccessRuleStore {

    private static final RowMapper<ScmApiAccessRule> ROW_MAPPER = (rs, rowNum) -> ScmApiAccessRule.builder()
            .id(rs.getString("id"))
            .provider(rs.getString("provider"))
            .operation(ScmApiAccessRule.Operation.valueOf(rs.getString("operation")))
            .access(ScmApiAccessRule.Access.valueOf(rs.getString("access")))
            .source(ScmApiAccessRule.Source.valueOf(rs.getString("source")))
            .build();

    private final DataSource dataSource;
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcScmApiAccessRuleStore(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    @Override
    public void initialize() {
        DatabaseMigrator.migrate(dataSource);
    }

    @Override
    public void save(ScmApiAccessRule rule) {
        jdbc.update("DELETE FROM scm_api_access_rules WHERE id = :id", Map.of("id", rule.getId()));
        jdbc.update(
                "INSERT INTO scm_api_access_rules (id, provider, operation, access, source)"
                        + " VALUES (:id, :provider, :operation, :access, :source)",
                new MapSqlParameterSource()
                        .addValue("id", rule.getId())
                        .addValue("provider", rule.getProvider())
                        .addValue("operation", rule.getOperation().name())
                        .addValue("access", rule.getAccess().name())
                        .addValue("source", rule.getSource().name()));
    }

    @Override
    public void delete(String id) {
        jdbc.update("DELETE FROM scm_api_access_rules WHERE id = :id", Map.of("id", id));
    }

    @Override
    public List<ScmApiAccessRule> findAll() {
        return jdbc.query("SELECT * FROM scm_api_access_rules", ROW_MAPPER);
    }

    @Override
    public List<ScmApiAccessRule> findByProvider(String provider) {
        return jdbc.query(
                "SELECT * FROM scm_api_access_rules WHERE provider = :provider",
                Map.of("provider", provider),
                ROW_MAPPER);
    }
}
