package com.rbc.fogwall.db;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Splits a pack into fixed-size chunks for storage and reassembles it on read, verifying length and SHA-256. Shared by
 * both database families so a pack is stored the same way in each.
 */
public final class PackChunks {

    /**
     * Chunk size. MySQL's driver sends binary parameters hex-encoded, doubling a chunk on the wire, so this keeps one
     * insert under a 1 MiB {@code max_allowed_packet}, the lowest a MySQL-family server is commonly configured with.
     */
    public static final int CHUNK_SIZE = 256 * 1024;

    /** Receives each chunk as it is cut. */
    @FunctionalInterface
    public interface ChunkSink {
        void write(int seq, byte[] data) throws IOException;
    }

    /** Returns the chunk at {@code seq}, or empty if none is stored. */
    @FunctionalInterface
    public interface ChunkSource {
        Optional<byte[]> read(int seq) throws IOException;
    }

    /** The length and SHA-256 (lowercase hex) of a pack as written. */
    public record Written(long length, String sha256) {}

    private PackChunks() {}

    /** Reads {@code in} to its end, handing each chunk to {@code sink} in order. */
    public static Written write(InputStream in, ChunkSink sink) throws IOException {
        MessageDigest digest = sha256();
        long length = 0;
        int seq = 0;
        byte[] chunk;
        while ((chunk = in.readNBytes(CHUNK_SIZE)).length > 0) {
            digest.update(chunk);
            length += chunk.length;
            sink.write(seq++, chunk);
        }
        return new Written(length, HexFormat.of().formatHex(digest.digest()));
    }

    /**
     * Returns a stream over the stored chunks, fetching one at a time. It fails with an {@link IOException} instead of
     * reporting its end if a chunk is missing or the bytes do not match {@code length} and {@code sha256}.
     */
    public static InputStream read(long length, String sha256, ChunkSource source) {
        return new ChunkedInputStream(length, sha256, source);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java platform", e);
        }
    }

    private static final class ChunkedInputStream extends InputStream {

        private final long length;
        private final String sha256;
        private final ChunkSource source;
        private final MessageDigest digest = sha256();

        private byte[] current = new byte[0];
        private int pos;
        private int nextSeq;
        private long delivered;
        private boolean verified;

        ChunkedInputStream(long length, String sha256, ChunkSource source) {
            this.length = length;
            this.sha256 = sha256;
            this.source = source;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n == -1 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            if (pos == current.length && !advance()) {
                return -1;
            }
            int n = Math.min(len, current.length - pos);
            System.arraycopy(current, pos, b, off, n);
            pos += n;
            return n;
        }

        /** Loads the next chunk, or verifies the whole pack and returns false once every byte has been delivered. */
        private boolean advance() throws IOException {
            if (delivered == length) {
                verify();
                return false;
            }
            int seq = nextSeq;
            byte[] chunk = source.read(seq)
                    .filter(c -> c.length > 0 && delivered + c.length <= length)
                    .orElseThrow(() -> new IOException("Stored pack is incomplete or corrupt at chunk " + seq));
            nextSeq++;
            delivered += chunk.length;
            digest.update(chunk);
            current = chunk;
            pos = 0;
            return true;
        }

        private void verify() throws IOException {
            if (verified) {
                return;
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!actual.equals(sha256)) {
                throw new IOException("Stored pack does not match its SHA-256");
            }
            verified = true;
        }
    }
}
