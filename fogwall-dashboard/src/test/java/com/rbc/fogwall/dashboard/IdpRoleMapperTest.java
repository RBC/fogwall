package com.rbc.fogwall.dashboard;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.config.AuthConfig;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IdpRoleMapperTest {

    private static final Map<String, List<String>> MAPPINGS = Map.of(
            "USER", List.of("devs"),
            "ADMIN", List.of("ops"),
            "AUDITOR", List.of("audit"),
            "READER", List.of("staff"),
            "SELF_CERTIFY", List.of("trusted"));

    private static final Optional<String> NONE = Optional.empty();

    private static Optional<Set<String>> map(List<String> groups, Optional<String> defaultRole) {
        return IdpRoleMapper.rolesFor(groups, MAPPINGS, defaultRole);
    }

    // ── mapping ──────────────────────────────────────────────────────────────

    @Test
    void mappingGrantsWhatItNames() {
        assertEquals(Optional.of(Set.of("USER")), map(List.of("devs"), NONE));
        assertEquals(Optional.of(Set.of("READER")), map(List.of("staff"), NONE));
        assertEquals(Optional.of(Set.of("AUDITOR")), map(List.of("audit"), NONE));
    }

    @Test
    void adminImpliesUser() {
        assertEquals(Optional.of(Set.of("ADMIN", "USER")), map(List.of("ops"), NONE));
    }

    @Test
    void readerIsAdditive() {
        assertEquals(Optional.of(Set.of("READER", "USER")), map(List.of("staff", "devs"), NONE));
    }

    @Test
    void defaultRoleIgnoredOnceASessionRoleIsMapped() {
        assertEquals(Optional.of(Set.of("READER")), map(List.of("staff"), Optional.of("USER")));
    }

    @Test
    void unmatched_refusedWithNoDefault() {
        assertEquals(NONE, map(List.of("nobody"), NONE));
    }

    @Test
    void unmatched_getsTheDefaultRole() {
        assertEquals(Optional.of(Set.of("READER")), map(List.of("nobody"), Optional.of("READER")));
        assertEquals(Optional.of(Set.of("USER")), map(List.of("nobody"), Optional.of("USER")));
    }

    @Test
    void selfCertifyAlone_refusedWithNoDefault() {
        assertEquals(NONE, map(List.of("trusted"), NONE));
    }

    @Test
    void selfCertifyAlone_keepsItAlongsideTheDefault() {
        assertEquals(Optional.of(Set.of("SELF_CERTIFY", "USER")), map(List.of("trusted"), Optional.of("USER")));
        assertEquals(Optional.of(Set.of("SELF_CERTIFY", "READER")), map(List.of("trusted"), Optional.of("READER")));
    }

    @Test
    void mappingKeysAreCaseInsensitive() {
        assertEquals(
                Optional.of(Set.of("ADMIN", "USER")),
                IdpRoleMapper.rolesFor(List.of("ops"), Map.of("admin", List.of("ops")), NONE));
    }

    @Test
    void rolesAdmittingNoSession_namesModifiersOnly() {
        assertEquals(List.of("SELF_CERTIFY"), IdpRoleMapper.rolesAdmittingNoSession(MAPPINGS));
    }

    // ── default-role resolution ──────────────────────────────────────────────

    private static AuthConfig auth(String defaultRole, Boolean requireRoleMapping, Map<String, List<String>> mappings) {
        AuthConfig auth = new AuthConfig();
        auth.setDefaultRole(defaultRole);
        auth.setRequireRoleMapping(requireRoleMapping);
        auth.setRoleMappings(mappings);
        return auth;
    }

    @Test
    void defaultRole_unsetIsNone() {
        assertEquals(NONE, IdpRoleMapper.defaultRole(auth(null, null, MAPPINGS)));
    }

    @Test
    void defaultRole_acceptsNoneReaderUserAuditorInAnyCase() {
        assertEquals(NONE, IdpRoleMapper.defaultRole(auth("none", null, MAPPINGS)));
        assertEquals(Optional.of("AUDITOR"), IdpRoleMapper.defaultRole(auth("auditor", null, MAPPINGS)));
        assertEquals(Optional.of("READER"), IdpRoleMapper.defaultRole(auth("Reader", null, MAPPINGS)));
        assertEquals(Optional.of("USER"), IdpRoleMapper.defaultRole(auth("USER", null, MAPPINGS)));
    }

    @Test
    void defaultRole_refusesAnyOtherRole() {
        for (String role : List.of("ADMIN", "SELF_CERTIFY", "nobody")) {
            assertThrows(
                    IllegalStateException.class,
                    () -> IdpRoleMapper.defaultRole(auth(role, null, MAPPINGS)),
                    role + " must not be a default role");
        }
    }

    @Test
    void defaultRole_noneWithNoMappingsFailsStartup() {
        var e = assertThrows(IllegalStateException.class, () -> IdpRoleMapper.defaultRole(auth(null, null, Map.of())));
        assertTrue(e.getMessage().contains("auth.default-role"), e.getMessage());
        assertThrows(IllegalStateException.class, () -> IdpRoleMapper.defaultRole(auth("NONE", null, Map.of())));
    }

    @Test
    void defaultRole_withNoMappingsIsAllowedWhenItAdmits() {
        assertEquals(Optional.of("READER"), IdpRoleMapper.defaultRole(auth("READER", null, Map.of())));
    }

    @Test
    void deprecatedRequireRoleMapping_translates() {
        assertEquals(NONE, IdpRoleMapper.defaultRole(auth(null, true, MAPPINGS)));
        assertEquals(Optional.of("READER"), IdpRoleMapper.defaultRole(auth(null, false, MAPPINGS)));
    }

    @Test
    void deprecatedRequireRoleMappingTrue_withNoMappingsFailsStartup() {
        assertThrows(IllegalStateException.class, () -> IdpRoleMapper.defaultRole(auth(null, true, Map.of())));
    }

    @Test
    void bothKeysSet_failsStartup() {
        assertThrows(IllegalStateException.class, () -> IdpRoleMapper.defaultRole(auth("READER", false, MAPPINGS)));
    }
}
