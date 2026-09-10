package com.rbc.fogwall.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.rbc.fogwall.permission.RepoPermission.Grant;
import org.junit.jupiter.api.Test;

/**
 * Pins the full grant relationship matrix. {@link Grant#implies} and {@link Grant#overlaps} are derived from each
 * grant's declared capability set, so these tests are what catch a mis-declared capability when a new grant is added:
 * they assert the intended truth table directly, independent of the set machinery that produces it.
 */
class GrantTest {

    @Test
    void implies_isReflexive() {
        for (Grant g : Grant.values()) {
            assertEquals(true, g.implies(g), g + " must imply itself");
        }
    }

    @Test
    void implies_compoundGrantsSatisfyTheirParts() {
        assertEquals(true, Grant.PUSH_AND_REVIEW.implies(Grant.PUSH));
        assertEquals(true, Grant.PUSH_AND_REVIEW.implies(Grant.REVIEW));
        // PROPOSE is a strict superset of ISSUE.
        assertEquals(true, Grant.PROPOSE.implies(Grant.ISSUE));
    }

    @Test
    void implies_isDirectional_partsDoNotSatisfyTheWhole() {
        assertEquals(false, Grant.PUSH.implies(Grant.PUSH_AND_REVIEW));
        assertEquals(false, Grant.REVIEW.implies(Grant.PUSH_AND_REVIEW));
        // ISSUE must NOT imply PROPOSE — the whole point of the narrow grant.
        assertEquals(false, Grant.ISSUE.implies(Grant.PROPOSE));
    }

    @Test
    void implies_unrelatedGrantsNeverSatisfyEachOther() {
        // Independent axes: push/review, propose/issue, self-certify, merge.
        assertEquals(false, Grant.PUSH.implies(Grant.REVIEW));
        assertEquals(false, Grant.REVIEW.implies(Grant.PUSH));
        assertEquals(false, Grant.PUSH.implies(Grant.PROPOSE));
        assertEquals(false, Grant.PROPOSE.implies(Grant.PUSH));
        assertEquals(false, Grant.SELF_CERTIFY.implies(Grant.PUSH));
        assertEquals(false, Grant.PUSH_AND_REVIEW.implies(Grant.SELF_CERTIFY));
        assertEquals(false, Grant.MERGE.implies(Grant.PROPOSE));
        assertEquals(false, Grant.PROPOSE.implies(Grant.MERGE));
    }

    @Test
    void overlaps_isReflexiveAndSymmetric() {
        for (Grant a : Grant.values()) {
            assertEquals(true, a.overlaps(a), a + " must overlap itself");
            for (Grant b : Grant.values()) {
                assertEquals(a.overlaps(b), b.overlaps(a), "overlaps must be symmetric for " + a + "/" + b);
            }
        }
    }

    @Test
    void overlaps_onlyWithinTheSameAxis() {
        // push/review axis: the compound overlaps each part, the parts don't overlap each other.
        assertEquals(true, Grant.PUSH.overlaps(Grant.PUSH_AND_REVIEW));
        assertEquals(true, Grant.REVIEW.overlaps(Grant.PUSH_AND_REVIEW));
        assertEquals(false, Grant.PUSH.overlaps(Grant.REVIEW));

        // propose/issue axis.
        assertEquals(true, Grant.PROPOSE.overlaps(Grant.ISSUE));

        // standalone axes overlap nothing but themselves. MAINTAIN is a bundle over push/propose/issue/merge, so
        // it shares MERGE's capability — but never SELF_CERTIFY's, which it deliberately leaves out.
        for (Grant other : Grant.values()) {
            if (other != Grant.SELF_CERTIFY) {
                assertEquals(false, Grant.SELF_CERTIFY.overlaps(other), "SELF_CERTIFY should not overlap " + other);
            }
            if (other != Grant.MERGE && other != Grant.MAINTAIN) {
                assertEquals(false, Grant.MERGE.overlaps(other), "MERGE should not overlap " + other);
            }
        }

        // cross-axis pairs never overlap.
        assertEquals(false, Grant.PUSH.overlaps(Grant.PROPOSE));
        assertEquals(false, Grant.PUSH_AND_REVIEW.overlaps(Grant.ISSUE));
        assertEquals(false, Grant.REVIEW.overlaps(Grant.MERGE));
    }

    @Test
    void maintain_bundlesPushProposeIssueMerge_butNotSelfCertifyOrReview() {
        // The bundle satisfies each of its parts.
        assertEquals(true, Grant.MAINTAIN.implies(Grant.PUSH));
        assertEquals(true, Grant.MAINTAIN.implies(Grant.PROPOSE));
        assertEquals(true, Grant.MAINTAIN.implies(Grant.ISSUE));
        assertEquals(true, Grant.MAINTAIN.implies(Grant.MERGE));

        // Deliberately excluded: self-certify is a peer-review bypass, review stays on the SCM's own UI.
        assertEquals(false, Grant.MAINTAIN.implies(Grant.SELF_CERTIFY));
        assertEquals(false, Grant.MAINTAIN.implies(Grant.REVIEW));
        assertEquals(false, Grant.MAINTAIN.implies(Grant.PUSH_AND_REVIEW));

        // Directional: a part never satisfies the whole bundle.
        assertEquals(false, Grant.PUSH.implies(Grant.MAINTAIN));
        assertEquals(false, Grant.MERGE.implies(Grant.MAINTAIN));
        assertEquals(false, Grant.PROPOSE.implies(Grant.MAINTAIN));

        // Overlaps every axis it carries, plus PUSH_AND_REVIEW via the shared PUSH capability; never SELF_CERTIFY.
        assertEquals(true, Grant.MAINTAIN.overlaps(Grant.PUSH));
        assertEquals(true, Grant.MAINTAIN.overlaps(Grant.PROPOSE));
        assertEquals(true, Grant.MAINTAIN.overlaps(Grant.ISSUE));
        assertEquals(true, Grant.MAINTAIN.overlaps(Grant.MERGE));
        assertEquals(true, Grant.MAINTAIN.overlaps(Grant.PUSH_AND_REVIEW));
        assertEquals(false, Grant.MAINTAIN.overlaps(Grant.SELF_CERTIFY));
        assertEquals(false, Grant.MAINTAIN.overlaps(Grant.REVIEW));
    }
}
