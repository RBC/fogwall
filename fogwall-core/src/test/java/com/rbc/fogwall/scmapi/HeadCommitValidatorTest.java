package com.rbc.fogwall.scmapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.PushStoreFactory;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HeadCommitValidatorTest {

    @Test
    void shaWithPushRecord_isValidated() {
        PushStore store = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());
        String sha = "sha-" + UUID.randomUUID();
        store.save(PushRecord.builder()
                .commitTo(sha)
                .branch("refs/heads/feature")
                .provider("github")
                .project("owner")
                .repoName("fork-repo")
                .status(PushStatus.APPROVED)
                .build());

        assertTrue(new HeadCommitValidator(store).isValidated(sha));
    }

    @Test
    void shaWithNoPushRecord_isNotValidated() {
        PushStore store = PushStoreFactory.h2InMemory("test-" + UUID.randomUUID());

        assertFalse(new HeadCommitValidator(store).isValidated("sha-never-pushed"));
    }
}
