package com.rbc.fogwall.servlet.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

/**
 * Refuses Git LFS uploads, which fogwall cannot inspect.
 *
 * <p>LFS transfers file content out-of-band over its own HTTP API and leaves only a small text pointer in the pack, so
 * every content filter in the chain would inspect the pointer and pass it while the actual bytes travelled a path
 * fogwall never sees. Until LFS is modelled properly, refusing the upload is the honest answer: fogwall cannot make a
 * statement about content it never received.
 *
 * <p>Downloads are deliberately allowed. Their request body is a small JSON document, and content validation applies to
 * pushes rather than fetches, so blocking them would break clones of any repository that has ever used LFS without
 * gaining anything.
 *
 * <p>Runs at {@link Integer#MIN_VALUE}, ahead of {@code ParseGitRequestFilter}, because that filter buffers the request
 * body as its first action. Placed any later, an LFS upload would be read into heap before being refused.
 */
@Slf4j
public class LfsRejectionFilter implements FogwallFilter {

    /** Every LFS endpoint lives under this path segment, relative to the repository. */
    private static final String LFS_PATH_SEGMENT = "/info/lfs/";

    private static final String MEDIA_TYPE = "application/vnd.git-lfs+json";

    private static final String MESSAGE = "Git LFS is not supported through fogwall at this time. "
            + "LFS transfers file content outside the git protocol, so fogwall cannot validate it. "
            + "Push the file directly instead, or contact an administrator if you need LFS for this repository.";

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }

    @Override
    public Predicate<HttpServletRequest> shouldFilter() {
        return LfsRejectionFilter::isLfsUpload;
    }

    /**
     * Overridden because the inherited implementation calls {@code determineOperation}, which recognises only the three
     * smart-HTTP operations and throws for anything else — an LFS request included.
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if (!isLfsUpload(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }
        reject(httpRequest, (HttpServletResponse) response);
    }

    @Override
    public void doHttpFilter(HttpServletRequest request, HttpServletResponse response) throws IOException {
        reject(request, response);
    }

    /**
     * An upload is anything under the LFS path that is not a download. The batch endpoint declares its intent in a JSON
     * body we would have to read to inspect, so the direction is inferred from the method and path instead: GET is
     * always a read, and object PUTs are always writes. A batch POST is treated as an upload because letting it through
     * would only earn the client a rejection one request later, after it had negotiated hrefs.
     */
    private static boolean isLfsUpload(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || !uri.contains(LFS_PATH_SEGMENT)) return false;
        return !"GET".equalsIgnoreCase(request.getMethod());
    }

    /**
     * Answers in the LFS client's own dialect. The batch API is plain JSON over HTTP rather than the git wire protocol,
     * so a sideband packet would not render — git-lfs surfaces the {@code message} field to the user.
     */
    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.warn("Refusing Git LFS upload: {} {}", request.getMethod(), request.getRequestURI());
        if (response.isCommitted()) return;

        byte[] payload = ("{\"message\":\"" + MESSAGE + "\"}").getBytes(StandardCharsets.UTF_8);
        response.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        response.setContentType(MEDIA_TYPE);
        response.setContentLength(payload.length);
        response.getOutputStream().write(payload);
    }
}
