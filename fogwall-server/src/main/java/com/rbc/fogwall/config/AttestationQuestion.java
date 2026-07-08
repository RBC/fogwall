package com.rbc.fogwall.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** A single attestation question presented to reviewers before they can approve a push. */
@Data
public class AttestationQuestion {

    /** Unique identifier used as the key when storing answers. */
    private String id;

    /** Input type. Supported values: {@code checkbox} (default), {@code text}, {@code dropdown}. */
    private String type = "checkbox";

    /** Human-readable question label shown in the review form. */
    private String label;

    /** Whether the reviewer must answer this question before submitting an approval. */
    private boolean required = false;

    /** Options for {@code dropdown} type questions. Ignored for other types. */
    private List<String> options = new ArrayList<>();

    /** Optional tooltip / help text shown alongside the question. */
    private String tooltip;
}
