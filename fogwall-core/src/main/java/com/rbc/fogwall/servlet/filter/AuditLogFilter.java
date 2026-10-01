package com.rbc.fogwall.servlet.filter;

import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.git.LifecycleStage;
import com.rbc.fogwall.git.PushStepKind;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;

/** A default implementation of {@link AuditFilter} that logs audit messages using SLF4J logger. */
@Slf4j
public final class AuditLogFilter extends AbstractFogwallFilter implements AuditFilter {

    /** Apply audit logging to all operations by default and after all other filters. */
    public AuditLogFilter() {
        super(LifecycleStage.MANDATORY_POST);
    }

    @Override
    public Optional<PushStepKind> stepKind() {
        return Optional.of(PushStepKind.AUDIT_LOG);
    }

    @Override
    public void audit(GitRequestDetails requestDetails) {
        log.atLevel(levelFor(requestDetails))
                .log(
                        "Result={},Reason={},Provider={},Repository={},Operation={}",
                        requestDetails.getResult(),
                        requestDetails.getReason(),
                        requestDetails.getProvider().getName(),
                        requestDetails.getRepoRef(),
                        requestDetails.getOperation());
    }

    /**
     * INFO, except a clone or fetch nothing refused, which is DEBUG: fetches are the bulk of the traffic, and their
     * counts are kept by {@code FetchActivityRecorder}. A discovery request is never finalized, so one that passed is
     * still PENDING here.
     */
    static Level levelFor(GitRequestDetails requestDetails) {
        GitRequestDetails.GitResult result = requestDetails.getResult();
        boolean allowedRead = requestDetails.getOperation() != HttpOperation.PUSH
                && result != GitRequestDetails.GitResult.REJECTED
                && result != GitRequestDetails.GitResult.ERROR;
        return allowedRead ? Level.DEBUG : Level.INFO;
    }
}
