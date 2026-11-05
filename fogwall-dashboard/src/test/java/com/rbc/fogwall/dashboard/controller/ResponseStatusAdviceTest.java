package com.rbc.fogwall.dashboard.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ResponseStatusAdviceTest {

    private final ResponseStatusAdvice advice = new ResponseStatusAdvice();

    @Test
    void theReasonIsTheErrorBody_withTheExceptionsStatus() {
        var response =
                advice.statusError(new ResponseStatusException(HttpStatus.BAD_REQUEST, "owner and repo go together"));

        assertEquals(400, response.getStatusCode().value());
        assertEquals(Map.of("error", "owner and repo go together"), response.getBody());
    }

    @Test
    void withoutAReason_theStatusPhraseIsTheError() {
        var response = advice.statusError(new ResponseStatusException(HttpStatus.BAD_GATEWAY));

        assertEquals(502, response.getStatusCode().value());
        assertEquals(Map.of("error", "Bad Gateway"), response.getBody());
    }
}
