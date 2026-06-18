package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;

import com.rbc.fogwall.git.GitRequestDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public interface AuditFilter extends FogwallFilter {

    void audit(GitRequestDetails requestDetails);

    @Override
    default void doHttpFilter(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        var requestDetails = (GitRequestDetails) request.getAttribute(GIT_REQUEST_ATTR);
        audit(requestDetails);
    }
}
