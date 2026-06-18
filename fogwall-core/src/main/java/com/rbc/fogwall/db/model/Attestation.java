package com.rbc.fogwall.db.model;

import java.time.Instant;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/** An approval, rejection, or cancellation attestation for a push. */
@Data
@Builder
public class Attestation {

    public enum Type {
        APPROVAL,
        REJECTION,
        CANCELLATION
    }

    /** ID of the parent push record. */
    private String pushId;

    /** Type of attestation. */
    private Type type;

    /** Username of the reviewer. */
    private String reviewerUsername;

    /** Email of the reviewer. */
    private String reviewerEmail;

    /** Reason for the decision (required for rejections). */
    private String reason;

    /** Whether this was an automated decision. */
    @Builder.Default
    private boolean automated = false;

    /** Whether an admin approved/rejected their own push, bypassing the normal self-approval block. */
    @Builder.Default
    private boolean selfApproval = false;

    /** When the attestation was made. */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Reviewer's answers to the provider's attestation questions. Keys are question IDs; values are the submitted
     * answer (e.g. {@code "true"} for a checked checkbox, the text value for free-text, the selected option for
     * dropdowns). {@code null} when no attestation questions are configured for the provider.
     */
    private Map<String, String> answers;
}
