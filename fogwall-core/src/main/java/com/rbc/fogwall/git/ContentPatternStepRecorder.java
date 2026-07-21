package com.rbc.fogwall.git;

import com.rbc.fogwall.db.model.PushStep;
import com.rbc.fogwall.db.model.StepStatus;
import com.rbc.fogwall.validation.ContentPatternFinding;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Shared WARN-step recording for {@link ContentPatternDiffHook} and {@link ContentPatternCommitMessageHook} -
 * S&amp;F-mode content-pattern checks that both scan for {@link ContentPatternFinding}s against a {@link PushContext}
 * and never block. Matched values are routed to redaction; only the data type and jurisdiction (never the raw match)
 * are recorded in the step content shown to reviewers.
 */
final class ContentPatternStepRecorder {

    private ContentPatternStepRecorder() {}

    static void record(PushContext pushContext, String stepName, int order, List<ContentPatternFinding> findings) {
        if (findings.isEmpty()) {
            pushContext.addStep(PushStep.builder()
                    .stepName(stepName)
                    .stepOrder(order)
                    .status(StepStatus.PASS)
                    .build());
            return;
        }

        for (ContentPatternFinding finding : findings) {
            pushContext.addSecretsToRedact(List.of(finding.matchedText()));
        }

        String content = findings.stream()
                .map(f -> "Possible " + f.dataType() + " (" + f.jurisdiction() + ")")
                .distinct()
                .collect(Collectors.joining("\n"));
        pushContext.addStep(PushStep.builder()
                .stepName(stepName)
                .stepOrder(order)
                .status(StepStatus.WARN)
                .content(content)
                .build());
    }
}
