package com.rbc.fogwall.dashboard.controller;

import com.rbc.fogwall.config.FogwallConfig;
import com.rbc.fogwall.dashboard.audit.AdminAuditLog;
import com.rbc.fogwall.provider.ProviderRegistry;
import com.rbc.fogwall.provider.ScmOAuthProvider;
import com.rbc.fogwall.service.GitCredentialService;
import com.rbc.fogwall.user.GitCredential;
import com.rbc.fogwall.user.GitCredentialNameConflictException;
import com.rbc.fogwall.user.ReadOnlyUserStore;
import com.rbc.fogwall.user.ScmOAuthTokenStore;
import com.rbc.fogwall.user.UserStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The git credentials fogwall issues for server-mode pushes on providers with {@code oauth.brokered-push}: a user
 * manages their own, and an administrator can list and revoke anyone's.
 *
 * <p>A credential's value is returned once, in the response that issues or rotates it. Nothing returns it afterwards.
 */
@Tag(name = "Git credentials", description = "fogwall-issued git credentials for server-mode pushes")
@RestController
@RequiredArgsConstructor
public class GitCredentialController {

    private final GitCredentialService credentials;

    private final ReadOnlyUserStore userStore;

    private final ScmOAuthTokenStore scmOAuthTokens;

    private final ProviderRegistry providers;

    private final FogwallConfig fogwallConfig;

    private final AdminAuditLog auditLog;

    /**
     * A credential as the API shows it: never its value or hash. {@code expiresAt} is when it stops working under the
     * current configuration, which a lowered lifetime limit can bring forward from the expiry it was issued with.
     */
    public record CredentialView(
            String id, String name, Instant createdAt, Instant expiresAt, boolean expired, Instant lastUsedAt) {}

    /** A credential just issued or rotated, with the one copy of its value. */
    public record IssuedView(CredentialView credential, String value) {}

    public record IssueRequest(String name) {}

    @Operation(operationId = "listMyGitCredentials", summary = "List the current user's git credentials")
    @GetMapping("/api/me/git-credentials")
    public List<CredentialView> listMine() {
        return credentials.list(currentUsername()).stream().map(this::view).toList();
    }

    @Operation(
            operationId = "listMyGitCredentialProviders",
            summary = "List the providers the current user can push to with a git credential",
            description = "Providers with brokered pushes on which the user has linked an account with permission to"
                    + " push. A credential can be issued only when there is at least one.")
    @GetMapping("/api/me/git-credentials/providers")
    public List<String> providersMine() {
        return usableProviders(currentUsername());
    }

    @Operation(
            operationId = "issueGitCredential",
            summary = "Issue a git credential to the current user",
            description = "The response carries the credential's value, which is never shown again.")
    @PostMapping("/api/me/git-credentials")
    public ResponseEntity<?> issue(@RequestBody IssueRequest request) {
        String username = currentUsername();
        ResponseEntity<?> refusal = refuseWithoutLinkedAccount(username, "git-credential.issue");
        if (refusal != null) {
            return refusal;
        }
        if (!(userStore instanceof UserStore mutable)) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(Map.of("error", "Git credentials require a database-backed user store"));
        }
        // A user declared only in config has no database row until something references it, and credentials do.
        mutable.upsertUser(username);
        try {
            GitCredentialService.Issued issued = credentials.issue(username, request != null ? request.name() : null);
            auditLog.success(
                    "git-credential.issue",
                    "user:" + username,
                    "credential=" + issued.credential().id());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new IssuedView(view(issued.credential()), issued.value()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (GitCredentialNameConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "You already have a credential with this name"));
        }
    }

    @Operation(
            operationId = "rotateGitCredential",
            summary = "Replace the value of one of the current user's git credentials",
            description = "The old value stops working at once. The response carries the new value, which is never"
                    + " shown again.")
    @PostMapping("/api/me/git-credentials/{id}/rotate")
    public ResponseEntity<?> rotate(@PathVariable String id) {
        String username = currentUsername();
        ResponseEntity<?> refusal = refuseWithoutLinkedAccount(username, "git-credential.rotate");
        if (refusal != null) {
            return refusal;
        }
        return credentials
                .rotate(username, id)
                .<ResponseEntity<?>>map(issued -> {
                    auditLog.success("git-credential.rotate", "user:" + username, "credential=" + id);
                    return ResponseEntity.ok(new IssuedView(view(issued.credential()), issued.value()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(operationId = "revokeMyGitCredential", summary = "Revoke one of the current user's git credentials")
    @DeleteMapping("/api/me/git-credentials/{id}")
    public ResponseEntity<?> revokeMine(@PathVariable String id) {
        String username = currentUsername();
        if (!credentials.revoke(username, id)) {
            return ResponseEntity.notFound().build();
        }
        auditLog.success("git-credential.revoke", "user:" + username, "credential=" + id);
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "listUserGitCredentials", summary = "List a user's git credentials")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/users/{username}/git-credentials")
    public List<CredentialView> listForUser(@PathVariable String username) {
        return credentials.list(username).stream().map(this::view).toList();
    }

    @Operation(operationId = "revokeUserGitCredential", summary = "Revoke one of a user's git credentials")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/api/users/{username}/git-credentials/{id}")
    public ResponseEntity<?> revokeForUser(@PathVariable String username, @PathVariable String id) {
        if (!credentials.revoke(username, id)) {
            return ResponseEntity.notFound().build();
        }
        auditLog.success("git-credential.revoke", "user:" + username, "credential=" + id);
        return ResponseEntity.noContent().build();
    }

    private CredentialView view(GitCredential credential) {
        return new CredentialView(
                credential.id(),
                credential.name(),
                credential.createdAt(),
                credentials.effectiveExpiry(credential),
                credentials.isExpired(credential),
                credential.lastUsedAt());
    }

    /**
     * Refuses to hand out a credential value to a user who could not push with it: one is useful only with a linked
     * OAuth token that can push, on a provider that brokers pushes, and until then it would be a live authentication
     * factor for nothing. Returns null when the user has such a provider.
     */
    private ResponseEntity<?> refuseWithoutLinkedAccount(String username, String action) {
        List<String> brokered = brokeredProviders();
        if (brokered.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "No provider accepts fogwall credentials for pushes"));
        }
        if (!usableProviders(username).isEmpty()) {
            return null;
        }
        List<String> linked = scmOAuthTokens.findLinkedProviders(username);
        if (brokered.stream().anyMatch(linked::contains)) {
            auditLog.denied(action, "user:" + username, "linked account cannot push");
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error",
                            "Your linked account was linked without permission to push. Link your account for "
                                    + String.join(" or ", brokered) + " again on your profile."));
        }
        auditLog.denied(action, "user:" + username, "no linked account");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "error",
                        "Link your account for " + String.join(" or ", brokered)
                                + " on your profile before creating a git credential"));
    }

    /** Providers with brokered pushes on which {@code username} has linked an account that can push. */
    private List<String> usableProviders(String username) {
        return brokeredProviders().stream()
                .filter(provider -> scmOAuthTokens
                        .findToken(username, provider)
                        .filter(token -> grantsPush(provider, token.scopes()))
                        .isPresent())
                .toList();
    }

    private boolean grantsPush(String provider, String scopes) {
        return providers
                .getProvider(provider)
                .filter(ScmOAuthProvider.class::isInstance)
                .map(p -> ((ScmOAuthProvider) p).grantsPush(scopes))
                .orElse(true);
    }

    private List<String> brokeredProviders() {
        return fogwallConfig.getProviders().entrySet().stream()
                .filter(e -> e.getValue().isEnabled()
                        && e.getValue().getOauth().isEnabled()
                        && e.getValue().getOauth().isBrokeredPush())
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}
