package com.rbc.fogwall.git;

import java.io.IOException;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

/**
 * Moves a store-and-forward push's objects out of its quarantine and into the mirror, once every check has passed.
 *
 * <p>Must be the last pre-receive hook. Everything before it can still reject the push, and a rejected push's objects
 * are exactly what should never reach the mirror; everything after it — JGit applying the ref updates, then
 * {@link ForwardingPostReceiveHook} — needs those objects to be there.
 *
 * <p>A failure here rejects the push. Unlike being unable to create a quarantine, which only costs disk hygiene, a
 * half-promoted push would leave the mirror holding refs whose objects are about to be deleted.
 */
@Slf4j
@RequiredArgsConstructor
public class QuarantinePromotionHook implements PreReceiveHook {

    private final QuarantineObjectStore quarantine;

    @Override
    public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
        boolean anyAccepted = commands.stream().anyMatch(c -> c.getResult() == ReceiveCommand.Result.NOT_ATTEMPTED);
        if (!anyAccepted) {
            log.debug("Nothing accepted — leaving the quarantine to be discarded");
            return;
        }
        try {
            quarantine.promote();
        } catch (IOException e) {
            log.error("Failed to promote quarantined objects into the mirror", e);
            for (ReceiveCommand cmd : commands) {
                if (cmd.getResult() == ReceiveCommand.Result.NOT_ATTEMPTED) {
                    cmd.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "internal error storing objects");
                }
            }
        }
    }
}
