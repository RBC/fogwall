package com.rbc.fogwall.permission;

import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single authorization grant: {@link #username} is permitted to perform {@link #grant} on repos matching
 * {@link #value} at {@link #provider}.
 *
 * <p>{@link #target} selects which part of the repo URL is compared (default {@link MatchTarget#SLUG});
 * {@link #matchType} controls how {@link #value} is interpreted: {@code GLOB} for {@code *}/{@code ?} wildcards
 * (default), {@code LITERAL} for exact equality, {@code REGEX} for full Java regex.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RepoPermission {

    @Builder.Default
    private String id = UUID.randomUUID().toString();

    private String username;
    private String provider;

    /** Which part of the repository URL is matched. Defaults to {@link MatchTarget#SLUG}. */
    @Builder.Default
    private MatchTarget target = MatchTarget.SLUG;

    /** Pattern to match against the {@link #target} portion of the URL. */
    private String value;

    /** How {@link #value} is interpreted when matching. Defaults to {@link MatchType#GLOB}. */
    @Builder.Default
    private MatchType matchType = MatchType.GLOB;

    @Builder.Default
    private Grant grant = Grant.PUSH;

    @Builder.Default
    private Source source = Source.DB;

    public enum Grant {
        /** Can submit pushes for review. */
        PUSH(Capability.PUSH),
        /** Can review (approve or reject) pushes submitted by others. */
        REVIEW(Capability.REVIEW),
        /** Shorthand for {@link #PUSH} + {@link #REVIEW}. Does not include {@link #SELF_CERTIFY}. */
        PUSH_AND_REVIEW(Capability.PUSH, Capability.REVIEW),
        /**
         * Trusted contributor: can certify their own clean pushes without a separate peer reviewer. All validation
         * still runs; the automated attestation is recorded in the audit log. Does not imply {@link #PUSH} or
         * {@link #REVIEW} — those must be granted separately if also needed.
         */
        SELF_CERTIFY(Capability.SELF_CERTIFY),
        /**
         * Can propose a change against matching repos — open a pull/merge request and iterate on it through the SCM API
         * proxy, along with the issue operations that accompany it.
         *
         * <p>Scope is the whole request surface of the allowlisted endpoints, not just title, body and comment: every
         * field the supported CLIs send has one of their own flags behind it, and {@code tea} PATCHes the full object
         * on every edit. So this also permits retargeting a proposal's base branch — within the same repository, since
         * no allowlisted edit endpoint takes a repository-valued field. The only effect reaching past the repo is
         * association with an object that is not repo-scoped: a GitHub project, or a GitLab group milestone or epic.
         *
         * <p>Named for what it permits rather than what it achieves: whether a proposal becomes a contribution is the
         * upstream maintainer's call, outside fogwall. Kept distinct from {@link #PUSH} so an operator can permission
         * git-push and change proposals independently — pushing to a fork proposes nothing — and does not imply
         * {@link #PUSH} or {@link #REVIEW}. Reads are not gated by this grant at all; they go through the existing
         * URL-rule mechanism, the same as git fetches.
         *
         * <p>Issue create/edit/comment are deliberately included: filing an issue that a pull request then closes is
         * part of one contribution, and comments cannot be split by subject anyway — GitHub's {@code addComment} takes
         * an "Issue or PR" id, and Gitea posts pull-request comments to its issue endpoint. A narrower participation
         * grant, for people who file issues but propose no code, would be a subset of this one.
         *
         * <p>Merging is <b>not</b> included — that is a maintainer operation with its own design questions. Neither is
         * review, which stays with the SCM's own UI.
         */
        PROPOSE(Capability.PROPOSE, Capability.ISSUE),
        /**
         * Can file and follow up on issues against matching repos — create an issue, edit its title or body, and
         * comment — and nothing more. The narrow floor under {@link #PROPOSE}: it carries none of {@code PROPOSE}'s
         * pull/merge-request abilities, so someone can be permitted to report a bug without any ability to push code or
         * open a proposal. {@link #PROPOSE} is a superset and implies it; the two are one axis, not peers.
         *
         * <p>Honoured on fogwall's dashboard issue path, where fogwall constructs a specific issue operation on the
         * user's behalf and so knows it is an issue write. It is deliberately <em>not</em> accepted by the SCM API
         * (CLI) proxy: there, an issue comment cannot always be told apart from a pull-request comment — GitHub's
         * {@code addComment} takes an "Issue or PR" id and Gitea posts PR comments to its issue endpoint — which is why
         * that channel bundles both under {@link #PROPOSE}. Fail-closed: no grant, no issue write. Reads happen on the
         * SCM's own UI and are not gated here.
         */
        ISSUE(Capability.ISSUE),
        /**
         * Can merge a pull/merge request through the SCM API proxy's maintainer path. Standalone: does not imply and is
         * not implied by {@link #PUSH}, {@link #REVIEW} or {@link #PROPOSE}. Fail-closed: no grant, no merge.
         */
        MERGE(Capability.MERGE);

        /**
         * The atomic, independently-permissionable abilities a grant confers. A grant is defined by the set of these it
         * carries, and every relationship between grants — whether one satisfies another, whether two collide — is a
         * set operation over these sets. So a compound grant like {@link #PUSH_AND_REVIEW} needs no capability of its
         * own (it is exactly {@code PUSH} + {@code REVIEW}), while a grant that is a strict superset of another, like
         * {@link #PROPOSE} over {@link #ISSUE}, keeps its own token so the containment is directional.
         */
        private enum Capability {
            PUSH,
            REVIEW,
            SELF_CERTIFY,
            PROPOSE,
            ISSUE,
            MERGE
        }

        private final Set<Capability> capabilities;

        Grant(Capability... capabilities) {
            this.capabilities =
                    capabilities.length == 0 ? EnumSet.noneOf(Capability.class) : EnumSet.copyOf(List.of(capabilities));
        }

        /**
         * Whether holding this grant satisfies a check that requires {@code requested} — i.e. this grant confers every
         * capability {@code requested} does. Reflexive, and directional: {@link #PROPOSE} implies {@link #ISSUE} but
         * not the reverse.
         */
        public boolean implies(Grant requested) {
            return capabilities.containsAll(requested.capabilities);
        }

        /**
         * Whether this grant and {@code other} share any capability — i.e. two configured entries on overlapping paths
         * would collide rather than sit on independent axes. {@link #PUSH} and {@link #REVIEW} do not overlap each
         * other, but both overlap {@link #PUSH_AND_REVIEW}; {@link #SELF_CERTIFY} and {@link #MERGE} overlap only
         * themselves.
         */
        public boolean overlaps(Grant other) {
            return !Collections.disjoint(capabilities, other.capabilities);
        }
    }

    public enum Source {
        CONFIG,
        DB
    }
}
