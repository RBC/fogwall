package com.rbc.fogwall.servlet.filter;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.git.QuarantineObjectStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class QuarantineCleanupFilterTest {

    @TempDir
    Path workDir;

    /** A request whose attributes actually behave like a map, since the filter both reads and removes one. */
    private static HttpServletRequest requestWithAttributes(Map<String, Object> attrs) {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        Mockito.doAnswer(i -> attrs.get(i.getArgument(0, String.class)))
                .when(req)
                .getAttribute(Mockito.anyString());
        Mockito.doAnswer(i -> attrs.remove(i.getArgument(0, String.class)))
                .when(req)
                .removeAttribute(Mockito.anyString());
        return req;
    }

    private Repository bareMirror() throws Exception {
        return Git.init()
                .setBare(true)
                .setDirectory(workDir.resolve("mirror.git").toFile())
                .call()
                .getRepository();
    }

    @Test
    void discardsTheQuarantineTheServletOpened() throws Exception {
        Repository mirror = bareMirror();
        Map<String, Object> attrs = new HashMap<>();
        HttpServletRequest req = requestWithAttributes(attrs);

        // The servlet runs inside the chain, which is where the quarantine is opened
        Path[] scratch = new Path[1];
        FilterChain chain = (rq, rs) -> {
            QuarantineObjectStore q = QuarantineObjectStore.createOrNull(mirror);
            scratch[0] = q.getDirectory();
            attrs.put(QuarantineObjectStore.REQUEST_ATTRIBUTE, q);
        };

        new QuarantineCleanupFilter().doFilter(req, Mockito.mock(HttpServletResponse.class), chain);

        assertFalse(Files.exists(scratch[0]), "The quarantine must not outlive the request");
        assertNull(attrs.get(QuarantineObjectStore.REQUEST_ATTRIBUTE));
        mirror.close();
    }

    @Test
    void discardsTheQuarantineEvenWhenTheChainThrows() throws Exception {
        Repository mirror = bareMirror();
        Map<String, Object> attrs = new HashMap<>();
        HttpServletRequest req = requestWithAttributes(attrs);

        Path[] scratch = new Path[1];
        FilterChain chain = (rq, rs) -> {
            QuarantineObjectStore q = QuarantineObjectStore.createOrNull(mirror);
            scratch[0] = q.getDirectory();
            attrs.put(QuarantineObjectStore.REQUEST_ATTRIBUTE, q);
            throw new ServletException("push blew up");
        };

        assertThrows(
                ServletException.class,
                () -> new QuarantineCleanupFilter().doFilter(req, Mockito.mock(HttpServletResponse.class), chain));

        assertFalse(Files.exists(scratch[0]), "A failed push must not leak its scratch directory either");
        mirror.close();
    }

    @Test
    void doesNothingWhenNoQuarantineWasOpened() throws IOException, ServletException {
        Map<String, Object> attrs = new HashMap<>();
        boolean[] ran = {false};
        FilterChain chain = (rq, rs) -> ran[0] = true;

        new QuarantineCleanupFilter()
                .doFilter(requestWithAttributes(attrs), Mockito.mock(HttpServletResponse.class), chain);

        assertTrue(ran[0], "The chain must still run");
    }
}
