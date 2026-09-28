package com.rbc.fogwall.db;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PackChunksTest {

    private static byte[] bytes(int size) {
        byte[] data = new byte[size];
        new Random(42).nextBytes(data);
        return data;
    }

    private static List<byte[]> chunk(byte[] data, PackChunks.Written[] written) throws IOException {
        List<byte[]> chunks = new ArrayList<>();
        written[0] = PackChunks.write(new ByteArrayInputStream(data), (seq, chunk) -> {
            assertEquals(chunks.size(), seq);
            chunks.add(chunk);
        });
        return chunks;
    }

    private static InputStream reassemble(List<byte[]> chunks, long length, String sha256) {
        return PackChunks.read(
                length, sha256, seq -> seq < chunks.size() ? Optional.of(chunks.get(seq)) : Optional.empty());
    }

    @Test
    void writeThenRead_multiChunkPack_roundTrips() throws IOException {
        byte[] data = bytes(PackChunks.CHUNK_SIZE * 2 + 1234);
        PackChunks.Written[] written = new PackChunks.Written[1];

        List<byte[]> chunks = chunk(data, written);

        assertEquals(3, chunks.size());
        assertEquals(data.length, written[0].length());
        assertArrayEquals(
                data,
                reassemble(chunks, written[0].length(), written[0].sha256()).readAllBytes());
    }

    @Test
    void writeThenRead_emptyPack_hasNoChunks() throws IOException {
        PackChunks.Written[] written = new PackChunks.Written[1];

        List<byte[]> chunks = chunk(new byte[0], written);

        assertTrue(chunks.isEmpty());
        assertEquals(0, written[0].length());
        assertEquals(-1, reassemble(chunks, 0, written[0].sha256()).read());
    }

    @Test
    void read_missingChunk_failsInsteadOfEnding() throws IOException {
        byte[] data = bytes(PackChunks.CHUNK_SIZE + 10);
        PackChunks.Written[] written = new PackChunks.Written[1];
        List<byte[]> chunks = chunk(data, written);
        chunks.remove(1);

        InputStream in = reassemble(chunks, written[0].length(), written[0].sha256());

        assertThrows(IOException.class, in::readAllBytes);
    }

    @Test
    void read_alteredChunk_failsInsteadOfEnding() throws IOException {
        byte[] data = bytes(4096);
        PackChunks.Written[] written = new PackChunks.Written[1];
        List<byte[]> chunks = chunk(data, written);
        chunks.get(0)[100] ^= 1;

        InputStream in = reassemble(chunks, written[0].length(), written[0].sha256());

        assertThrows(IOException.class, in::readAllBytes);
    }

    @Test
    void read_extraBytesBeyondLength_fail() throws IOException {
        byte[] data = bytes(4096);
        PackChunks.Written[] written = new PackChunks.Written[1];
        List<byte[]> chunks = chunk(data, written);

        InputStream in = reassemble(chunks, written[0].length() - 1, written[0].sha256());

        assertThrows(IOException.class, in::readAllBytes);
    }
}
