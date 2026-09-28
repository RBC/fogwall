package com.rbc.fogwall.git;

import static com.rbc.fogwall.git.GitClientUtils.AnsiColor.RED;
import static com.rbc.fogwall.git.GitClientUtils.SymbolCodes.NO_ENTRY;
import static com.rbc.fogwall.git.GitClientUtils.color;
import static com.rbc.fogwall.git.GitClientUtils.sym;

import com.rbc.fogwall.db.ParkedPushStore;
import com.rbc.fogwall.db.model.ParkedRefUpdate;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

/**
 * Stores what a push delivered so any instance can forward it after the client has gone: the pack JGit wrote into the
 * push's quarantine, and the ref updates the client asked for.
 *
 * <p>Runs after validation and before the push record is saved as PENDING, so a push is reviewable only once its pack
 * is stored. A push with validation issues is left alone for the persistence hook to reject. Any failure to store
 * rejects the push: a push is never acknowledged unless it can be forwarded.
 *
 * <p>The quarantine holds one pack at most. JGit completes a thin pack with the bases it borrowed from the mirror, so
 * the stored pack does not depend on what this instance's mirror happened to hold.
 */
@Slf4j
public class ParkPushPreReceiveHook implements PreReceiveHook {

    private final ParkedPushStore parkedPushStore;
    private final QuarantineObjectStore quarantine;
    private final ValidationContext validationContext;
    private final PushContext pushContext;
    private final String providerName;
    private final String forwardUser;

    /**
     * @param quarantine the push's quarantine; {@code null} when none could be opened, in which case the push is
     *     rejected
     * @param providerName the provider instance the forwarding user's linked account is held under
     * @param forwardUser the fogwall user whose linked account will forward the push
     */
    public ParkPushPreReceiveHook(
            ParkedPushStore parkedPushStore,
            QuarantineObjectStore quarantine,
            ValidationContext validationContext,
            PushContext pushContext,
            String providerName,
            String forwardUser) {
        this.parkedPushStore = parkedPushStore;
        this.quarantine = quarantine;
        this.validationContext = validationContext;
        this.pushContext = pushContext;
        this.providerName = providerName;
        this.forwardUser = forwardUser;
    }

    @Override
    public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
        if (validationContext.hasIssues()) {
            return;
        }
        String pushId = pushContext.getPushId();
        String upstreamUrl = pushContext.getUpstreamUrl();
        if (quarantine == null || pushId == null || upstreamUrl == null) {
            log.error(
                    "Cannot park push {}: quarantine={}, upstreamUrl={} - rejecting",
                    pushId,
                    quarantine != null,
                    upstreamUrl);
            reject(rp, commands);
            return;
        }

        List<ParkedRefUpdate> refs = commands.stream()
                .filter(cmd -> cmd.getResult() == ReceiveCommand.Result.NOT_ATTEMPTED)
                .map(cmd -> new ParkedRefUpdate(
                        cmd.getRefName(), cmd.getOldId().name(), cmd.getNewId().name(), cmd.getType()))
                .toList();
        try (InputStream pack = openPack()) {
            parkedPushStore.park(pushId, providerName, forwardUser, upstreamUrl, refs, pack);
            log.info("Parked push {} ({} ref update(s)) for forwarding as {}", pushId, refs.size(), forwardUser);
        } catch (IOException | RuntimeException e) {
            log.error("Failed to park push {} - rejecting", pushId, e);
            reject(rp, commands);
        }
    }

    /** The quarantine's pack, or an empty stream when the push carried no objects. */
    private InputStream openPack() throws IOException {
        Path packDir = quarantine.getObjectsDirectory().resolve("pack");
        if (!Files.isDirectory(packDir)) {
            return InputStream.nullInputStream();
        }
        List<Path> packs;
        try (Stream<Path> files = Files.list(packDir)) {
            packs = files.filter(p -> p.getFileName().toString().endsWith(".pack"))
                    .toList();
        }
        if (packs.size() > 1) {
            throw new IOException("Expected at most one pack in the quarantine, found " + packs.size());
        }
        return packs.isEmpty() ? InputStream.nullInputStream() : Files.newInputStream(packs.get(0));
    }

    private static void reject(ReceivePack rp, Collection<ReceiveCommand> commands) {
        rp.sendMessage(color(RED, sym(NO_ENTRY) + "  Push blocked - fogwall could not store it for forwarding"));
        for (ReceiveCommand cmd : commands) {
            if (cmd.getResult() == ReceiveCommand.Result.NOT_ATTEMPTED) {
                cmd.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "Push could not be stored for forwarding");
            }
        }
    }
}
