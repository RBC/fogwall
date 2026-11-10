package com.rbc.fogwall.dashboard.controller;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Writes a {@link ResponseStatusException} as the API's error body, {@code {"error": reason}}, with its status. Left to
 * the servlet container, the reason would reach the client only inside an HTML error page.
 */
@RestControllerAdvice
class ResponseStatusAdvice {

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> statusError(ResponseStatusException e) {
        String reason = e.getReason() != null
                ? e.getReason()
                : HttpStatus.valueOf(e.getStatusCode().value()).getReasonPhrase();
        return ResponseEntity.status(e.getStatusCode()).headers(e.getHeaders()).body(Map.of("error", reason));
    }
}
