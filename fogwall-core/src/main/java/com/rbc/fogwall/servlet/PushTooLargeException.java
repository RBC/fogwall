package com.rbc.fogwall.servlet;

import java.io.IOException;
import lombok.Getter;

/**
 * Thrown when a request body exceeds the configured maximum. Extends {@link IOException} so it propagates out of the
 * servlet read path without widening any signatures; callers that want to report the limit to the client catch it
 * specifically.
 */
@Getter
public class PushTooLargeException extends IOException {

    private final long limitBytes;

    /** Bytes read before the limit was exceeded. Not the full body size — reading stops at the limit. */
    private final long bytesRead;

    public PushTooLargeException(long limitBytes, long bytesRead) {
        super("Request body exceeds the configured maximum of " + limitBytes + " bytes");
        this.limitBytes = limitBytes;
        this.bytesRead = bytesRead;
    }
}
