package com.rbc.fogwall.db;

import com.rbc.fogwall.db.model.ParkedPush;
import com.rbc.fogwall.db.model.ParkedRefUpdate;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Holds the packs of parked pushes until they are forwarded or given up on. Both database families implement this.
 *
 * <p>This is working state, not audit evidence: the push record is the permanent account of a push, and a stored pack
 * is deleted once nothing can still forward it. Packs are stored in fixed-size chunks with their length and SHA-256,
 * and verified against both when read back.
 */
public interface ParkedPushStore {

    /**
     * Stores a parked push and its pack, reading {@code pack} to its end.
     *
     * @param pack the pack of the pushed objects; empty when the push carried none
     * @return the stored push, with its pack size and park time
     * @throws IOException if {@code pack} cannot be read
     */
    ParkedPush park(
            String pushId,
            String providerName,
            String forwardUser,
            String upstreamUrl,
            List<ParkedRefUpdate> refs,
            InputStream pack)
            throws IOException;

    /** Returns the parked push with this id, if its pack is stored. */
    Optional<ParkedPush> find(String pushId);

    /**
     * Opens the stored pack for reading. The stream fails with an {@link IOException} before reporting its end if the
     * bytes read back do not match the stored length and SHA-256.
     *
     * @throws IOException if no pack is stored for this push
     */
    InputStream openPack(String pushId) throws IOException;

    /** Deletes the parked push and its pack. Does nothing if there is none. */
    void delete(String pushId);

    /**
     * Returns the ids of parked pushes nothing can still forward, oldest first:
     *
     * <ul>
     *   <li>pushes whose record is neither PENDING, APPROVED nor ERROR;
     *   <li>pushes in ERROR whose forward failed before {@code failedBefore};
     *   <li>pushes with no record, parked before {@code orphanedBefore}. The pack is stored before the record is
     *       written, so a younger one may still be waiting for its record.
     * </ul>
     */
    List<String> findReclaimable(Instant failedBefore, Instant orphanedBefore, int limit);
}
