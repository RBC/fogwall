package com.rbc.fogwall.db.jdbc.mapper;

import com.rbc.fogwall.db.model.AccessRule;
import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;

/** Maps an {@code access_rules} result-set row to an {@link AccessRule}. */
public final class AccessRuleRowMapper implements RowMapper<AccessRule> {

    public static final AccessRuleRowMapper INSTANCE = new AccessRuleRowMapper();

    private AccessRuleRowMapper() {}

    @Override
    public AccessRule mapRow(ResultSet rs, int rowNum) throws SQLException {
        return AccessRule.builder()
                .id(rs.getString("id"))
                .provider(rs.getString("provider"))
                .target(MatchTarget.valueOf(rs.getString("target")))
                .value(rs.getString("match_value"))
                .matchType(MatchType.valueOf(rs.getString("match_type")))
                .access(AccessRule.Access.valueOf(rs.getString("access")))
                .operation(AccessRule.Operation.valueOf(rs.getString("operation")))
                .description(rs.getString("description"))
                .enabled(rs.getBoolean("enabled"))
                .ruleOrder(rs.getInt("rule_order"))
                .source(AccessRule.Source.valueOf(rs.getString("source")))
                .build();
    }
}
