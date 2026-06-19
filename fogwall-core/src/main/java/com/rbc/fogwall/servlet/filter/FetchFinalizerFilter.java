package com.rbc.fogwall.servlet.filter;

import static com.rbc.fogwall.servlet.FogwallServlet.GIT_REQUEST_ATTR;

import com.rbc.fogwall.git.GitRequestDetails;
import com.rbc.fogwall.git.HttpOperation;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;

// A filter that sets a git request to be ALLOWED for fetch requests so long as the request
// has passed through all applicable fetch filters unmodified (ie. request is still in PENDING
// initial state & not a mutated result such as ERROR, REJECTED or REVIEW)
public class FetchFinalizerFilter extends AbstractFogwallFilter {

    private static final int ORDER = Integer.MAX_VALUE - 2;

    public FetchFinalizerFilter() {
        super(ORDER, Set.of(HttpOperation.FETCH));
    }

    @Override
    public void doHttpFilter(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        var details = (GitRequestDetails) request.getAttribute(GIT_REQUEST_ATTR);
        if (details != null && details.getResult() == GitRequestDetails.GitResult.PENDING) {
            details.setResult(GitRequestDetails.GitResult.ALLOWED);
        }
    }
}
