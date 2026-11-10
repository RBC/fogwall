package com.rbc.fogwall.dashboard.controller;

import com.rbc.fogwall.db.model.RecordCursor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

/**
 * The query parameters every record listing shares, parsed the same way everywhere: a malformed value is a 400 naming
 * the parameter, never an empty page. Listings page in {@code (time, id)} order; a full page carries the cursor of its
 * last row in {@link #NEXT_CURSOR_HEADER}, and passing it back as {@code after} reads the page that follows.
 */
final class ListingParams {

    /** Response header carrying the cursor for the page after this one. Absent on a page shorter than the limit. */
    static final String NEXT_CURSOR_HEADER = "X-Next-Cursor";

    static final int MAX_LIMIT = 1000;

    private ListingParams() {}

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException badRequest(String message, Exception cause) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message, cause);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** {@code value}, or empty when blank. */
    static Optional<String> text(String value) {
        return blank(value) ? Optional.empty() : Optional.of(value);
    }

    /**
     * The value of a parameter that also has an older name. Either name may be used; naming both with different values
     * is a 400.
     */
    static Optional<String> aliased(String name, String value, String legacyName, String legacyValue) {
        if (!blank(value) && !blank(legacyValue) && !value.equals(legacyValue)) {
            throw badRequest("'" + name + "' and '" + legacyName + "' name the same filter; give one");
        }
        return text(blank(value) ? legacyValue : value);
    }

    /** An enum parameter, case-insensitive. An unknown value is a 400 listing the valid ones. */
    static <E extends Enum<E>> Optional<E> enumValue(String name, String value, Class<E> type) {
        if (blank(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            throw badRequest(
                    "Unknown " + name + ": " + value + ". Valid values: " + Arrays.toString(type.getEnumConstants()),
                    e);
        }
    }

    /** An ISO-8601 instant parameter. A malformed value is a 400. */
    static Optional<Instant> instant(String name, String value) {
        if (blank(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(value.trim()));
        } catch (DateTimeParseException e) {
            throw badRequest("Invalid " + name + " timestamp (expected ISO-8601): " + value, e);
        }
    }

    /** Refuses a shared filter this record type cannot answer, rather than silently ignoring it. */
    static void unsupported(String name, String value, String recordType) {
        if (!blank(value)) {
            throw badRequest("'" + name + "' is not a filter on " + recordType);
        }
    }

    /** {@code limit} within 1..{@value #MAX_LIMIT}; anything else is a 400. Negative offsets are a 400. */
    static void checkPage(int limit, int offset) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw badRequest("limit must be between 1 and " + MAX_LIMIT);
        }
        if (offset < 0) {
            throw badRequest("offset must not be negative");
        }
    }

    /** The cursor a caller passed back as {@code after}. A token this server did not issue is a 400. */
    static Optional<RecordCursor> cursor(String value) {
        if (blank(value)) {
            return Optional.empty();
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value.trim()), StandardCharsets.UTF_8);
            int sep = decoded.indexOf('\n');
            if (sep < 1 || sep == decoded.length() - 1) {
                throw badRequest("Invalid cursor: " + value);
            }
            return Optional.of(new RecordCursor(Instant.parse(decoded.substring(0, sep)), decoded.substring(sep + 1)));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw badRequest("Invalid cursor: " + value, e);
        }
    }

    /**
     * The opaque token for {@code cursor}; {@link #cursor(String)} reads it back. Keeps the instant's full precision.
     */
    static String encode(RecordCursor cursor) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((cursor.at() + "\n" + cursor.id()).getBytes(StandardCharsets.UTF_8));
    }

    /** A page of {@code rows}, with the next-page cursor header when the page is full. */
    static <T> ResponseEntity<List<T>> page(List<T> rows, int limit, Function<T, RecordCursor> cursorOf) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (!rows.isEmpty() && rows.size() >= limit) {
            response.header(NEXT_CURSOR_HEADER, encode(cursorOf.apply(rows.getLast())));
        }
        return response.body(rows);
    }
}
