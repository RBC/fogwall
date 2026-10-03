package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;

import com.rbc.fogwall.db.model.PushStep;
import com.rbc.fogwall.db.model.StepStatus;
import com.rbc.fogwall.git.CommitInspectionService;
import com.rbc.fogwall.git.DiffGenerationHook;
import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.git.LifecycleStage;
import com.rbc.fogwall.git.PushStepKind;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Repository;

/**
 * Records the push's aggregate {@code old..new} diff as a {@link DiffGenerationHook#STEP_NAME_PUSH_DIFF} step, so the
 * dashboard can display it whichever content checks are turned on. The transparent-proxy counterpart of
 * {@link DiffGenerationHook}; runs after {@link EnrichPushCommitsFilter} has unpacked the pushed objects. Tag pushes
 * and ref deletions carry no diff and record no step.
 *
 * <p>{@link ScanDiffFilter} scans the diff recorded here rather than regenerating it.
 */
@Slf4j
public final class DiffGenerationFilter extends AbstractFogwallFilter {

    public DiffGenerationFilter() {
        super(LifecycleStage.MANDATORY_PROCESSING, Set.of(HttpOperation.PUSH));
    }

    @Override
    public Optional<PushStepKind> stepKind() {
        return Optional.of(PushStepKind.DIFF_GENERATION);
    }

    /**
     * Applies only to a branch push with a commit range. Anything else has no diff, and is passed over here rather than
     * in {@link #doHttpFilter} so the chain records no step for it.
     */
    @Override
    public Predicate<HttpServletRequest> shouldFilter() {
        return request -> super.shouldFilter().test(request)
                && request.getAttribute(GIT_REQUEST_ATTR) instanceof GitRequestDetails details
                && !details.isTagPush()
                && details.getCommitTo() != null
                && !details.getCommitTo().isEmpty();
    }

    @Override
    public void doHttpFilter(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var requestDetails = (GitRequestDetails) request.getAttribute(GIT_REQUEST_ATTR);
        String fromCommit = requestDetails.getCommitFrom();
        String toCommit = requestDetails.getCommitTo();
        Repository repository = requestDetails.getLocalRepository();
        if (repository == null) {
            log.warn("localRepository not set on request - EnrichPushCommitsFilter may not have run; no diff recorded");
            recordStep(request, StepStatus.SKIPPED, "", "local repository unavailable");
            return;
        }

        try {
            String diff = CommitInspectionService.getFormattedDiff(repository, fromCommit, toCommit);
            requestDetails
                    .getSteps()
                    .add(PushStep.builder()
                            .pushId(requestDetails.getId().toString())
                            .stepName(DiffGenerationHook.STEP_NAME_PUSH_DIFF)
                            .stepOrder(displayOrder())
                            .status(StepStatus.PASS)
                            .content(diff)
                            .build());
        } catch (Exception e) {
            log.warn("Could not generate diff for push {}..{}: {}", fromCommit, toCommit, e.getMessage());
            recordStep(request, StepStatus.SKIPPED, "", e.getMessage());
        }
    }
}
