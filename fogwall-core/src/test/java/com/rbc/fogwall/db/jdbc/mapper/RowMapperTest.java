package com.rbc.fogwall.db.jdbc.mapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.rbc.fogwall.db.model.AccessRule;
import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class RowMapperTest {

    // ---- AccessRuleRowMapper ----

    @Test
    void accessRule_allFields_mapped() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("id")).thenReturn("rule-1");
        when(rs.getString("provider")).thenReturn("github");
        when(rs.getString("target")).thenReturn("OWNER");
        when(rs.getString("match_value")).thenReturn("org");
        when(rs.getString("match_type")).thenReturn("GLOB");
        when(rs.getString("access")).thenReturn("DENY");
        when(rs.getString("operation")).thenReturn("PUSH");
        when(rs.getString("description")).thenReturn("Block all pushes");
        when(rs.getBoolean("enabled")).thenReturn(true);
        when(rs.getInt("rule_order")).thenReturn(50);
        when(rs.getString("source")).thenReturn("CONFIG");

        AccessRule rule = AccessRuleRowMapper.INSTANCE.mapRow(rs, 0);

        assertEquals("rule-1", rule.getId());
        assertEquals("github", rule.getProvider());
        assertEquals(MatchTarget.OWNER, rule.getTarget());
        assertEquals("org", rule.getValue());
        assertEquals(MatchType.GLOB, rule.getMatchType());
        assertEquals(AccessRule.Access.DENY, rule.getAccess());
        assertEquals(AccessRule.Operation.PUSH, rule.getOperation());
        assertEquals("Block all pushes", rule.getDescription());
        assertTrue(rule.isEnabled());
        assertEquals(50, rule.getRuleOrder());
        assertEquals(AccessRule.Source.CONFIG, rule.getSource());
    }

    @Test
    void accessRule_nullableFields_null() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("id")).thenReturn("rule-2");
        when(rs.getString("provider")).thenReturn(null);
        when(rs.getString("target")).thenReturn("SLUG");
        when(rs.getString("match_value")).thenReturn("/org/**");
        when(rs.getString("match_type")).thenReturn("GLOB");
        when(rs.getString("access")).thenReturn("ALLOW");
        when(rs.getString("operation")).thenReturn("BOTH");
        when(rs.getString("description")).thenReturn(null);
        when(rs.getBoolean("enabled")).thenReturn(false);
        when(rs.getInt("rule_order")).thenReturn(100);
        when(rs.getString("source")).thenReturn("DB");

        AccessRule rule = AccessRuleRowMapper.INSTANCE.mapRow(rs, 1);

        assertNull(rule.getProvider());
        assertEquals(MatchTarget.SLUG, rule.getTarget());
        assertEquals("/org/**", rule.getValue());
        assertEquals(MatchType.GLOB, rule.getMatchType());
        assertNull(rule.getDescription());
        assertEquals(AccessRule.Access.ALLOW, rule.getAccess());
        assertEquals(AccessRule.Operation.BOTH, rule.getOperation());
        assertEquals(AccessRule.Source.DB, rule.getSource());
        assertFalse(rule.isEnabled());
    }
}
