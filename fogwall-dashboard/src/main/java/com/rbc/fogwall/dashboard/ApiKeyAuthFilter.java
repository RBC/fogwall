package com.rbc.fogwall.dashboard;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates requests that carry a valid {@code X-Api-Key} header. The expected key is read from the
 * {@code FOGWALL_API_KEY} environment variable at startup. If the variable is not set the filter is a no-op and every
 * request passes through unauthenticated (relying on form login instead).
 *
 * <p>Intended for CI/test scripts that call {@code /api/**} endpoints without a browser session.
 */
@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Api-Key";

    private final byte[] expectedKeyDigest;

    ApiKeyAuthFilter(String expectedKey) {
        this.expectedKeyDigest = expectedKey == null ? null : sha256(expectedKey);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (expectedKeyDigest != null) {
            String provided = request.getHeader(HEADER);
            // Compare digests rather than the raw values: MessageDigest.isEqual returns immediately on
            // unequal lengths, so comparing raw keys would let response timing reveal the key's length.
            if (provided != null
                    && MessageDigest.isEqual(expectedKeyDigest, sha256(provided))
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                var auth = new UsernamePasswordAuthenticationToken(
                        "api-key",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JCA spec but is unavailable", e);
        }
    }
}
