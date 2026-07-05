package com.rbc.fogwall.git;

import com.rbc.fogwall.db.model.PushStep;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Data;

/**
 * Per-request context shared across all pre/post-receive hooks within a single push. Carries both accumulated
 * {@link PushStep} records (diffs, scan results, etc.) and transient per-request values that must not be stored on the
 * shared cached {@link org.eclipse.jgit.lib.Repository} config.
 *
 * <p>All fields are written once (by {@link StoreAndForwardReceivePackFactory} or early hooks) and read by later hooks.
 * No synchronisation is needed because hooks in a single push execute sequentially on the same thread.
 */
@Data
public class PushContext {

    private final List<PushStep> steps = new ArrayList<>();

    // Per-request credentials — written by StoreAndForwardReceivePackFactory before any hook runs.
    private String pushUser;
    private String pushToken;
    private String repoSlug;

    /**
     * Transport context for this push — carries pre-authenticated identity (SSH) and upstream transport configuration.
     * Defaults to {@link PushTransport#http()} so HTTP pushes need not set it explicitly.
     */
    private PushTransport transport = PushTransport.http();

    // Resolved upstream username for Bitbucket pushes — written by BitbucketCredentialRewriteHook.
    private String upstreamUser;

    // Push record ID — written by PushStorePersistenceHook.preReceiveHook().
    private String pushId;

    // Identity resolved by CheckUserPushPermissionHook — written after permission check passes.
    private String resolvedUser;
    private String scmUsername;

    // Validation record ID — written by PushStorePersistenceHook.validationResultHook().
    private String validationRecordId;

    // Effective upstream base per ref — written by PriorPushEnrichmentHook when a re-push is detected
    // whose local cache tip has not been forwarded upstream yet. Downstream hooks use this instead of
    // cmd.getOldId() to enumerate the full commit range relative to the upstream.
    private final Map<String, String> effectiveFromIds = new HashMap<>();

    /** Add a step to the context. */
    public void addStep(PushStep step) {
        steps.add(step);
    }

    /** All accumulated steps, in the order they were added. */
    public List<PushStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    /** Store the effective upstream base SHA for a ref, overriding cmd.getOldId() for commit enumeration. */
    public void setEffectiveFromId(String refName, String sha) {
        effectiveFromIds.put(refName, sha);
    }

    /**
     * Returns the effective upstream base SHA for a ref, or {@code null} if no enrichment was detected. When non-null,
     * hooks should use this instead of {@code cmd.getOldId()} to compute the full commit range relative to upstream.
     */
    public String getEffectiveFromId(String refName) {
        return effectiveFromIds.get(refName);
    }

    /** Returns the content of the first step matching {@code stepName}, if present. */
    public Optional<String> getStepContent(String stepName) {
        return steps.stream()
                .filter(s -> stepName.equals(s.getStepName()))
                .findFirst()
                .map(PushStep::getContent);
    }
}
