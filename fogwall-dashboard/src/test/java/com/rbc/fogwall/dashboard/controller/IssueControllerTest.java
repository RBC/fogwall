package com.rbc.fogwall.dashboard.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rbc.fogwall.dashboard.issues.DashboardIssueClient;
import com.rbc.fogwall.dashboard.issues.DashboardIssueService;
import com.rbc.fogwall.dashboard.issues.DashboardIssueService.IssueOutcome;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IssueControllerTest {

    @Mock
    DashboardIssueService service;

    private IssueController controller() {
        return new IssueController(service);
    }

    private static IssueOutcome ok() {
        return new IssueOutcome(201, new DashboardIssueClient.IssueResult(7, "https://x/issues/7"), null, List.of());
    }

    @Test
    void create_success_maps201AndBody() {
        // currentUsername() is null with no security context; the service is mocked to accept it.
        when(service.createIssue(any(), eq("github"), eq("o"), eq("r"), eq("Bug"), eq("body")))
                .thenReturn(ok());

        var response = controller().create(new IssueController.CreateIssueRequest("github", "o", "r", "Bug", "body"));

        assertEquals(201, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) response.getBody();
        assertEquals(7, body.get("number"));
        assertEquals("https://x/issues/7", body.get("url"));
    }

    @Test
    void create_blankTitle_is400_andServiceNotCalled() {
        var response = controller().create(new IssueController.CreateIssueRequest("github", "o", "r", "  ", "body"));

        assertEquals(400, response.getStatusCode().value());
        verify(service, never()).createIssue(any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_forbiddenOutcome_mapsStatusAndError() {
        when(service.createIssue(any(), any(), any(), any(), any(), any()))
                .thenReturn(new IssueOutcome(403, null, "You do not have permission", List.of()));

        var response = controller().create(new IssueController.CreateIssueRequest("github", "o", "r", "Bug", "b"));

        assertEquals(403, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) response.getBody();
        assertEquals("You do not have permission", body.get("error"));
    }

    @Test
    void create_contentRejected_includesViolations() {
        when(service.createIssue(any(), any(), any(), any(), any(), any()))
                .thenReturn(new IssueOutcome(422, null, "Blocked by content inspection", List.of("secret detected")));

        var response = controller().create(new IssueController.CreateIssueRequest("github", "o", "r", "Bug", "b"));

        assertEquals(422, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) response.getBody();
        assertEquals(List.of("secret detected"), body.get("violations"));
    }

    @Test
    void comment_blankBody_is400() {
        var response = controller().comment(new IssueController.CommentRequest("github", "o", "r", 3, "   "));
        assertEquals(400, response.getStatusCode().value());
        verify(service, never()).comment(any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void edit_blankTitleAndBody_is400() {
        var response = controller().edit(new IssueController.EditIssueRequest("github", "o", "r", 3, "", ""));
        assertEquals(400, response.getStatusCode().value());
        verify(service, never()).editIssue(any(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void edit_blankFieldsBecomeNull() {
        when(service.editIssue(any(), eq("github"), eq("o"), eq("r"), eq(3), eq("New title"), eq(null)))
                .thenReturn(ok());

        var response =
                controller().edit(new IssueController.EditIssueRequest("github", "o", "r", 3, "New title", "  "));

        assertEquals(201, response.getStatusCode().value());
    }

    @Test
    void setState_delegatesToService() {
        when(service.setState(any(), eq("github"), eq("o"), eq("r"), eq(3), eq(true)))
                .thenReturn(new IssueOutcome(200, new DashboardIssueClient.IssueResult(3, "u"), null, List.of()));

        var response = controller().setState(new IssueController.StateRequest("github", "o", "r", 3, true));

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void providers_mapsToNameObjects() {
        when(service.eligibleProviders(any())).thenReturn(List.of("github", "gitlab"));

        var providers = controller().providers();

        assertEquals(2, providers.size());
        assertEquals("github", providers.get(0).get("name"));
        assertInstanceOf(Map.class, providers.get(0));
    }
}
