package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.PushStoreFactory;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class HeadCommitValidatorTest {

    private static PushRecord record(String sha, PushStatus status) {
        return PushRecord.builder()
                .commitTo(sha)
                .branch("refs/heads/feature")
                .provider("github")
                .project("owner")
                .repoName("fork-repo")
                .status(status)
                .build();
    }

    /** Only a push fogwall approved or forwarded validates its commit; any other outcome leaves it unvalidated. */
    @ParameterizedTest
    @EnumSource(PushStatus.class)
    void onlyAPushFogwallLetThrough_validates(PushStatus status) {
        PushStore store = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());
        String sha = "sha-" + UUID.randomUUID();
        store.save(record(sha, status));

        boolean expected = status == PushStatus.APPROVED || status == PushStatus.FORWARDED;
        assertEquals(expected, new HeadCommitValidator(store).isValidated(sha), status.name());
    }

    @Test
    void blockedThenForwarded_validates() {
        PushStore store = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());
        String sha = "sha-" + UUID.randomUUID();
        store.save(record(sha, PushStatus.REJECTED));
        store.save(record(sha, PushStatus.FORWARDED));

        assertEquals(true, new HeadCommitValidator(store).isValidated(sha));
    }

    @Test
    void shaWithNoPushRecord_isNotValidated() {
        PushStore store = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());

        assertFalse(new HeadCommitValidator(store).isValidated("sha-never-pushed"));
    }
}
