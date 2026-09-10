package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rbc.fogwall.db.model.PushStep;
import com.rbc.fogwall.db.model.StepStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class GitClientUtilsTest {

    @Test
    void buildValidationSummary_checkTrailers_usesHumanReadableLabel() {
        PushStep step = PushStep.builder()
                .stepName("trailers")
                .stepOrder(255)
                .status(StepStatus.PASS)
                .build();

        String summary = GitClientUtils.buildValidationSummary(List.of(step));

        assertTrue(
                summary.contains("Checking Co-Authored-By/Signed-off-by trailers"),
                "expected the friendly label, got: " + summary);
        assertTrue(summary.contains("trailers OK"), "expected the friendly pass result, got: " + summary);
        assertFalse(summary.contains("checkTrailers..."), "should not fall back to the raw internal step name");
    }
}
