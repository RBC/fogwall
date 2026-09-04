package com.rbc.fogwall.validation;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.config.CommitConfig.CoAuthorPolicy;
import com.rbc.fogwall.config.EmailRule;
import com.rbc.fogwall.git.Commit;
import com.rbc.fogwall.git.Contributor;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrailerPolicyCheckTest {

    // --- commit builders ---

    private static Commit commit(String authorEmail, List<String> signedOffBy, List<String> coAuthoredBy) {
        return Commit.builder()
                .sha("abc1234def")
                .author(Contributor.builder().name("Dev").email(authorEmail).build())
                .committer(Contributor.builder().name("Dev").email(authorEmail).build())
                .message("Some change")
                .signedOffBy(signedOffBy)
                .coAuthoredBy(coAuthoredBy)
                .build();
    }

    // --- config builders ---

    private static CommitConfig signedOffBy(boolean require, boolean requireAuthorMatch) {
        return CommitConfig.builder()
                .trailers(CommitConfig.TrailerPolicyConfig.builder()
                        .signedOffBy(CommitConfig.SignedOffByConfig.builder()
                                .require(require)
                                .requireAuthorMatch(requireAuthorMatch)
                                .build())
                        .build())
                .build();
    }

    private static CommitConfig coAuthored(CoAuthorPolicy policy, EmailRule... rules) {
        return CommitConfig.builder()
                .trailers(CommitConfig.TrailerPolicyConfig.builder()
                        .coAuthoredBy(CommitConfig.CoAuthoredByConfig.builder()
                                .policy(policy)
                                .email(CommitConfig.EmailConfig.builder()
                                        .rules(List.of(rules))
                                        .build())
                                .build())
                        .build())
                .build();
    }

    // --- default: nothing enforced ---

    @Test
    void defaultConfig_noTrailers_noViolations() {
        var check = new TrailerPolicyCheck(CommitConfig.defaultConfig());
        assertTrue(check.check(List.of(commit("dev@corp.com", List.of(), List.of())))
                .isEmpty());
    }

    // --- DCO: require Signed-off-by ---

    @Test
    void requireSignedOffBy_missing_blocks() {
        var check = new TrailerPolicyCheck(signedOffBy(true, false));
        List<Violation> v = check.check(List.of(commit("dev@corp.com", List.of(), List.of())));
        assertEquals(1, v.size());
        assertTrue(v.get(0).formattedDetail().contains("no Signed-off-by"));
    }

    @Test
    void requireSignedOffBy_present_passes() {
        var check = new TrailerPolicyCheck(signedOffBy(true, false));
        assertTrue(check.check(List.of(commit("dev@corp.com", List.of("Dev <dev@corp.com>"), List.of())))
                .isEmpty());
    }

    @Test
    void requireSignedOffBy_anySignOffSatisfiesWithoutAuthorMatch() {
        var check = new TrailerPolicyCheck(signedOffBy(true, false));
        assertTrue(check.check(List.of(commit("dev@corp.com", List.of("Someone Else <other@corp.com>"), List.of())))
                .isEmpty());
    }

    // --- DCO: require author match ---

    @Test
    void requireAuthorMatch_signOffFromAnotherIdentity_blocks() {
        var check = new TrailerPolicyCheck(signedOffBy(true, true));
        List<Violation> v = check.check(List.of(commit("dev@corp.com", List.of("Other <other@corp.com>"), List.of())));
        assertEquals(1, v.size());
        assertTrue(v.get(0).formattedDetail().contains("matching its author"));
    }

    @Test
    void requireAuthorMatch_signOffMatchesAuthor_passes() {
        var check = new TrailerPolicyCheck(signedOffBy(true, true));
        assertTrue(check.check(List.of(commit("dev@corp.com", List.of("Dev <dev@corp.com>"), List.of())))
                .isEmpty());
    }

    @Test
    void requireAuthorMatch_isCaseInsensitiveOnEmail() {
        var check = new TrailerPolicyCheck(signedOffBy(true, true));
        assertTrue(check.check(List.of(commit("Dev@Corp.com", List.of("Dev <dev@corp.com>"), List.of())))
                .isEmpty());
    }

    // --- Co-authored-by: BAN ---

    @Test
    void banCoAuthors_present_blocks() {
        var check = new TrailerPolicyCheck(coAuthored(CoAuthorPolicy.BAN));
        List<Violation> v = check.check(List.of(commit("dev@corp.com", List.of(), List.of("Pair <pair@corp.com>"))));
        assertEquals(1, v.size());
        assertTrue(v.get(0).formattedDetail().contains("not permitted"));
    }

    @Test
    void banCoAuthors_absent_passes() {
        var check = new TrailerPolicyCheck(coAuthored(CoAuthorPolicy.BAN));
        assertTrue(check.check(List.of(commit("dev@corp.com", List.of(), List.of())))
                .isEmpty());
    }

    // --- Co-authored-by: REQUIRE ---

    @Test
    void requireCoAuthors_absent_blocks() {
        var check = new TrailerPolicyCheck(coAuthored(CoAuthorPolicy.REQUIRE));
        List<Violation> v = check.check(List.of(commit("dev@corp.com", List.of(), List.of())));
        assertEquals(1, v.size());
        assertTrue(v.get(0).formattedDetail().contains("no Co-authored-by"));
    }

    @Test
    void requireCoAuthors_present_passes() {
        var check = new TrailerPolicyCheck(coAuthored(CoAuthorPolicy.REQUIRE));
        assertTrue(check.check(List.of(commit("dev@corp.com", List.of(), List.of("Pair <pair@corp.com>"))))
                .isEmpty());
    }

    // --- Co-authored-by: ALLOWLIST ---

    @Test
    void allowlistCoAuthors_disallowedDomain_blocks() {
        var check = new TrailerPolicyCheck(coAuthored(
                CoAuthorPolicy.ALLOWLIST,
                EmailRule.allow(EmailRule.Field.DOMAIN, EmailRule.Match.REGEX, "corp\\.com$")));
        List<Violation> v = check.check(List.of(commit("dev@corp.com", List.of(), List.of("Ext <ext@gmail.com>"))));
        assertEquals(1, v.size());
        assertTrue(v.get(0).formattedDetail().contains("Co-authored-by"));
    }

    @Test
    void allowlistCoAuthors_allowedDomain_passes() {
        var check = new TrailerPolicyCheck(coAuthored(
                CoAuthorPolicy.ALLOWLIST,
                EmailRule.allow(EmailRule.Field.DOMAIN, EmailRule.Match.REGEX, "corp\\.com$")));
        assertTrue(check.check(List.of(commit("dev@corp.com", List.of(), List.of("Pair <pair@corp.com>"))))
                .isEmpty());
    }

    @Test
    void allowlistCoAuthors_literalAddressAllow_permitsExactBotOnly() {
        var check = new TrailerPolicyCheck(coAuthored(
                CoAuthorPolicy.ALLOWLIST,
                EmailRule.allow(EmailRule.Field.ADDRESS, EmailRule.Match.LITERAL, "noreply@anthropic.com")));
        assertTrue(check.check(List.of(commit("dev@corp.com", List.of(), List.of("Claude <noreply@anthropic.com>"))))
                .isEmpty());
        assertFalse(check.check(List.of(commit("dev@corp.com", List.of(), List.of("Someone <someone@anthropic.com>"))))
                .isEmpty());
    }

    // --- combined + multi-commit ---

    @Test
    void multipleCommits_reportsEachViolation() {
        var check = new TrailerPolicyCheck(signedOffBy(true, false));
        Commit ok = commit("dev@corp.com", List.of("Dev <dev@corp.com>"), List.of());
        Commit bad1 = commit("dev@corp.com", List.of(), List.of());
        Commit bad2 = commit("dev@corp.com", List.of(), List.of());
        assertEquals(2, check.check(List.of(ok, bad1, bad2)).size());
    }

    @Test
    void extractEmail_handlesBareAddressAndAngleBrackets() {
        assertEquals("a@b.com", TrailerPolicyCheck.extractEmail("Name <a@b.com>"));
        assertEquals("a@b.com", TrailerPolicyCheck.extractEmail("a@b.com"));
    }
}
