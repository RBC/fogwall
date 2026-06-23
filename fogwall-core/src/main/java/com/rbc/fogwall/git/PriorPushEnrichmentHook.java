package com.rbc.fogwall.git;

import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.model.PushQuery;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import com.rbc.fogwall.db.model.PushStep;
import com.rbc.fogwall.db.model.StepStatus;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

/**
 * Pre-receive hook that detects S&amp;F re-pushes where the local cache tip has not yet been forwarded upstream, and
 * stores the effective upstream base SHA in {@link PushContext} so downstream hooks enumerate the full commit range
 * relative to the upstream rather than just the local cache delta.
 *
 * <p>Example: branch {@code foo} was first pushed (A, B) and then canceled. On re-push (A, B, C), JGit sets
 * {@code cmd.getOldId()} = B (local cache tip) and {@code cmd.getNewId()} = C, so downstream hooks only see C. This
 * hook detects that B was never forwarded, sets {@code effectiveFromId("refs/heads/foo", zero)} in the push context,
 * and downstream hooks use that to enumerate A, B, C instead.
 *
 * <p>Runs at order 195 — after authorization (150/160) but before content validation (210+). No-ops on truly new
 * branches ({@code cmd.getOldId()} = zero) since the local cache and upstream are both empty for that ref.
 */
@Slf4j
@RequiredArgsConstructor
public class PriorPushEnrichmentHook implements FogwallHook {

    static final int ORDER = 195;
    static final String STEP_NAME = "commitEnrichment";

    private final PushStore pushStore;
    private final PushContext pushContext;

    @Override
    public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
        String repoName = extractRepoName(pushContext.getRepoSlug());

        for (ReceiveCommand cmd : commands) {
            if (cmd.getType() == ReceiveCommand.Type.DELETE) continue;
            if (ObjectId.zeroId().equals(cmd.getOldId())) continue; // truly new branch — no enrichment needed

            String refName = cmd.getRefName();
            String localCacheTip = cmd.getOldId().name();

            List<PushRecord> lastForwarded = pushStore.find(PushQuery.builder()
                    .repoName(repoName)
                    .branch(refName)
                    .status(PushStatus.FORWARDED)
                    .limit(1)
                    .build());

            if (!lastForwarded.isEmpty()
                    && localCacheTip.equals(lastForwarded.get(0).getCommitTo())) {
                // Upstream is in sync with the local cache tip — normal incremental push, no enrichment needed
                continue;
            }

            // The local cache tip was never forwarded upstream. Find the last SHA that was forwarded
            // (the effective upstream base), or zero if nothing was ever forwarded for this branch.
            String effectiveFrom = lastForwarded.isEmpty()
                    ? ObjectId.zeroId().name()
                    : lastForwarded.get(0).getCommitTo();

            log.debug(
                    "S&F re-push detected for {}: local tip {} not forwarded upstream; effective base: {}",
                    refName,
                    localCacheTip,
                    effectiveFrom);
            pushContext.setEffectiveFromId(refName, effectiveFrom);
        }

        pushContext.addStep(PushStep.builder()
                .stepName(STEP_NAME)
                .stepOrder(ORDER)
                .status(StepStatus.PASS)
                .build());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public String getName() {
        return "PriorPushEnrichmentHook";
    }

    private static String extractRepoName(String slug) {
        if (slug == null || slug.isEmpty()) return null;
        int lastSlash = slug.lastIndexOf('/');
        return lastSlash >= 0 ? slug.substring(lastSlash + 1) : slug;
    }
}
