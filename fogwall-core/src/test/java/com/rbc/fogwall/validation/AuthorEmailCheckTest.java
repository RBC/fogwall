package com.rbc.fogwall.validation;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.config.EmailRule;
import com.rbc.fogwall.git.Commit;
import com.rbc.fogwall.git.Contributor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AuthorEmailCheckTest {

    // --- config helpers ---

    private static CommitConfig.EmailConfig domainAllow(String pattern) {
        return CommitConfig.EmailConfig.builder()
                .rules(List.of(EmailRule.allow(EmailRule.Field.DOMAIN, EmailRule.Match.REGEX, pattern)))
                .build();
    }

    private static CommitConfig.EmailConfig localBlock(String pattern) {
        return CommitConfig.EmailConfig.builder()
                .rules(List.of(EmailRule.block(EmailRule.Field.LOCAL, EmailRule.Match.REGEX, pattern)))
                .build();
    }

    private static CommitConfig committerDomainAllow(String pattern) {
        return CommitConfig.builder()
                .committer(CommitConfig.CommitterConfig.builder()
                        .email(domainAllow(pattern))
                        .build())
                .build();
    }

    private static CommitConfig committerLocalBlock(String pattern) {
        return CommitConfig.builder()
                .committer(CommitConfig.CommitterConfig.builder()
                        .email(localBlock(pattern))
                        .build())
                .build();
    }

    private static CommitConfig authorDomainAllow(String pattern) {
        return CommitConfig.builder()
                .author(CommitConfig.AuthorConfig.builder()
                        .email(domainAllow(pattern))
                        .build())
                .build();
    }

    private static CommitConfig bothDomainAllow(String pattern) {
        return CommitConfig.builder()
                .author(CommitConfig.AuthorConfig.builder()
                        .email(domainAllow(pattern))
                        .build())
                .committer(CommitConfig.CommitterConfig.builder()
                        .email(domainAllow(pattern))
                        .build())
                .build();
    }

    // --- commit builders ---

    private static Commit commit(String authorEmail, String committerEmail) {
        return Commit.builder()
                .sha("abc123")
                .author(Contributor.builder().name("Author").email(authorEmail).build())
                .committer(Contributor.builder()
                        .name("Committer")
                        .email(committerEmail)
                        .build())
                .message("Test commit")
                .build();
    }

    private static Commit plainCommit(String email) {
        return commit(email, email);
    }

    private static Commit rebasedExternalCommit(String committerEmail) {
        return commit("external@opensource.io", committerEmail);
    }

    // --- committer-only policy ---

    @Test
    void defaultConfig_anyValidEmail_noViolations() {
        AuthorEmailCheck check = new AuthorEmailCheck(CommitConfig.defaultConfig());
        assertTrue(check.check(List.of(plainCommit("anyone@anywhere.io"))).isEmpty());
    }

    @Test
    void committerOnly_matchingDomain_noViolations() {
        AuthorEmailCheck check = new AuthorEmailCheck(committerDomainAllow("corp\\.com$"));
        assertTrue(check.check(List.of(plainCommit("dev@corp.com"))).isEmpty());
    }

    @Test
    void committerOnly_wrongDomain_violation_labelledAsCommitter() {
        AuthorEmailCheck check = new AuthorEmailCheck(committerDomainAllow("corp\\.com$"));
        List<Violation> violations = check.check(List.of(plainCommit("dev@gmail.com")));
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).formattedDetail().contains("committer email (dev@gmail.com)"));
        assertTrue(violations.get(0).formattedDetail().contains("git config user.email"));
    }

    @Test
    void committerOnly_blockedLocalPart_violation_labelledAsCommitter() {
        AuthorEmailCheck check = new AuthorEmailCheck(committerLocalBlock("^(noreply|bot)$"));
        List<Violation> violations = check.check(List.of(plainCommit("noreply@corp.com")));
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).formattedDetail().contains("committer email (noreply@corp.com)"));
    }

    // --- rebase scenario: committer policy only allows rebased external commits ---

    @Test
    void committerOnly_rebasedExternalCommit_authorEmailNotChecked() {
        // external@opensource.io author passes because author policy is not configured
        AuthorEmailCheck check = new AuthorEmailCheck(committerDomainAllow("corp\\.com$"));
        Commit rebased = rebasedExternalCommit("employee@corp.com");
        assertTrue(check.check(List.of(rebased)).isEmpty());
    }

    @Test
    void committerOnly_rebasedExternalCommit_committerViolates_blocked() {
        AuthorEmailCheck check = new AuthorEmailCheck(committerDomainAllow("corp\\.com$"));
        Commit rebased = rebasedExternalCommit("employee@gmail.com");
        List<Violation> violations = check.check(List.of(rebased));
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).formattedDetail().contains("committer email"));
    }

    // --- author-only policy (strict rebase prevention) ---

    @Test
    void authorOnly_rebasedExternalCommit_authorViolates_committerPasses() {
        // no committer domain rule, so employee@corp.com passes as committer
        // but external@opensource.io fails the author rule
        AuthorEmailCheck check = new AuthorEmailCheck(authorDomainAllow("corp\\.com$"));
        Commit rebased = rebasedExternalCommit("employee@corp.com");
        List<Violation> violations = check.check(List.of(rebased));
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).formattedDetail().contains("author email (external@opensource.io)"));
        assertTrue(violations.get(0).formattedDetail().contains("Rebasing external commits"));
    }

    @Test
    void authorOnly_internalAuthor_noViolations() {
        AuthorEmailCheck check = new AuthorEmailCheck(authorDomainAllow("corp\\.com$"));
        assertTrue(check.check(List.of(plainCommit("dev@corp.com"))).isEmpty());
    }

    // --- both policies configured (strictest mode) ---

    @Test
    void bothPolicies_cleanInternalCommit_noViolations() {
        AuthorEmailCheck check = new AuthorEmailCheck(bothDomainAllow("corp\\.com$"));
        assertTrue(check.check(List.of(plainCommit("dev@corp.com"))).isEmpty());
    }

    @Test
    void bothPolicies_rebasedExternalCommit_twoViolations_bothLabelled() {
        AuthorEmailCheck check = new AuthorEmailCheck(bothDomainAllow("corp\\.com$"));
        Commit rebased = rebasedExternalCommit("employee@corp.com");
        // committer passes corp domain; author (external@opensource.io) fails
        List<Violation> violations = check.check(List.of(rebased));
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).formattedDetail().contains("author email"));
    }

    @Test
    void bothPolicies_wrongCommitterAndExternalAuthor_twoDistinctViolations() {
        AuthorEmailCheck check = new AuthorEmailCheck(bothDomainAllow("corp\\.com$"));
        Commit rebased = commit("external@opensource.io", "employee@gmail.com");
        List<Violation> violations = check.check(List.of(rebased));
        assertEquals(2, violations.size());
        assertTrue(violations.stream().anyMatch(v -> v.formattedDetail().contains("committer email")));
        assertTrue(violations.stream().anyMatch(v -> v.formattedDetail().contains("author email")));
    }

    // --- shared edge cases ---

    @Test
    void emptyCommitList_noViolations() {
        AuthorEmailCheck check = new AuthorEmailCheck(CommitConfig.defaultConfig());
        assertTrue(check.check(List.of()).isEmpty());
    }

    @Test
    void invalidEmailFormat_committerViolation() {
        AuthorEmailCheck check = new AuthorEmailCheck(committerDomainAllow("corp\\.com$"));
        assertFalse(check.check(List.of(plainCommit("notanemail"))).isEmpty());
    }

    @Test
    void multipleCommits_deduplicatesCommitterEmails() {
        AuthorEmailCheck check = new AuthorEmailCheck(committerDomainAllow("corp\\.com$"));
        Commit c1 = plainCommit("bad@gmail.com");
        Commit c2 = plainCommit("bad@gmail.com");
        assertEquals(1, check.check(List.of(c1, c2)).size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"user@corp.com", "dev+alias@corp.com", "a.b.c@sub.corp.com"})
    void variousValidCommitterEmails_noViolations(String email) {
        AuthorEmailCheck check = new AuthorEmailCheck(committerDomainAllow("corp\\.com$"));
        assertTrue(check.check(List.of(plainCommit(email))).isEmpty());
    }

    // ---- tagger identity (annotated tags, held to the committer policy) ----

    private static Contributor tagger(String email) {
        return Contributor.builder().name("Tagger").email(email).build();
    }

    @Test
    void checkTagger_allowedDomain_noViolations() {
        AuthorEmailCheck check = new AuthorEmailCheck(committerDomainAllow("corp\\.com$"));
        assertTrue(check.checkTagger(tagger("dev@corp.com")).isEmpty());
    }

    @Test
    void checkTagger_disallowedDomain_violation() {
        AuthorEmailCheck check = new AuthorEmailCheck(committerDomainAllow("corp\\.com$"));
        List<Violation> violations = check.checkTagger(tagger("dev@gmail.com"));
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).formattedDetail().contains("tagger email"));
    }

    @Test
    void checkTagger_blockedLocalPart_violation() {
        AuthorEmailCheck check = new AuthorEmailCheck(committerLocalBlock("^noreply$"));
        assertFalse(check.checkTagger(tagger("noreply@corp.com")).isEmpty());
    }

    @Test
    void checkTagger_emptyEmailWithPolicyConfigured_violation() {
        // A hand-crafted tag object without a tagger line surfaces as an empty identity; a configured
        // policy must flag it rather than skip it.
        AuthorEmailCheck check = new AuthorEmailCheck(committerDomainAllow("corp\\.com$"));
        assertFalse(check.checkTagger(tagger("")).isEmpty());
    }

    @Test
    void checkTagger_noCommitterPolicy_noViolations() {
        // Only an author policy is configured — the tagger follows the committer policy, so nothing applies.
        AuthorEmailCheck check = new AuthorEmailCheck(authorDomainAllow("corp\\.com$"));
        assertTrue(check.checkTagger(tagger("anyone@anywhere.io")).isEmpty());
    }

    @Test
    void checkTagger_nullTagger_noViolations() {
        AuthorEmailCheck check = new AuthorEmailCheck(committerDomainAllow("corp\\.com$"));
        assertTrue(check.checkTagger(null).isEmpty());
    }
}
