package com.rbc.fogwall.servlet.filter;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.GitRequestDetails.GitResult;
import com.rbc.fogwall.git.HttpOperation;
import com.rbc.fogwall.provider.GitHubProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.event.Level;

class AuditLogFilterTest {

    private static GitRequestDetails details(HttpOperation operation, GitResult result) {
        GitRequestDetails details = new GitRequestDetails();
        details.setOperation(operation);
        details.setResult(result);
        details.setProvider(new GitHubProvider("/proxy"));
        return details;
    }

    /** Fetches that went through are the bulk of the traffic; every refusal and every push stays at INFO. */
    @ParameterizedTest
    @CsvSource({
        "FETCH, ALLOWED, DEBUG",
        "INFO, PENDING, DEBUG",
        "INFO, ALLOWED, DEBUG",
        "FETCH, REJECTED, INFO",
        "INFO, REJECTED, INFO",
        "FETCH, ERROR, INFO",
        "PUSH, ALLOWED, INFO",
        "PUSH, PENDING, INFO",
        "PUSH, REJECTED, INFO",
    })
    void levelFor(HttpOperation operation, GitResult result, Level expected) {
        assertEquals(expected, AuditLogFilter.levelFor(details(operation, result)));
    }

    @ParameterizedTest
    @CsvSource({"FETCH, ALLOWED", "PUSH, REJECTED"})
    void audit_logsWithoutFailing(HttpOperation operation, GitResult result) {
        assertDoesNotThrow(() -> new AuditLogFilter().audit(details(operation, result)));
    }
}
