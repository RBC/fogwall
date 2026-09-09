package com.rbc.fogwall.dashboard.issues;

import com.rbc.fogwall.config.FogwallConfig;
import com.rbc.fogwall.config.ProviderConfig;
import com.rbc.fogwall.crypto.TokenCipher;
import com.rbc.fogwall.crypto.TokenCipherProvider;
import com.rbc.fogwall.db.ScmApiActionStore;
import com.rbc.fogwall.db.ScmApiProposalStore;
import com.rbc.fogwall.db.model.ScmApiActionRecord;
import com.rbc.fogwall.db.model.ScmApiActionStatus;
import com.rbc.fogwall.db.model.ScmApiProposalRecord;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.provider.FogwallProvider;
import com.rbc.fogwall.provider.ForgejoProvider;
import com.rbc.fogwall.provider.GitHubProvider;
import com.rbc.fogwall.provider.GitLabProvider;
import com.rbc.fogwall.provider.ProviderRegistry;
import com.rbc.fogwall.scmapi.ProposalContent;
import com.rbc.fogwall.scmapi.ProposalPayload;
import com.rbc.fogwall.scmapi.ScmContentInspector;
import com.rbc.fogwall.user.ScmOAuthTokenStore;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Files and follows up on issues through the dashboard on a user's behalf: resolve the provider, enforce the
 * {@code ISSUE}/{@code PROPOSE} grant (fail-closed), content-inspect the text, act with the user's linked OAuth token,
 * and write one audit record per attempt — the same auditability bar as the push and CLI-proxy paths.
 *
 * <p>The dashboard is offered a provider only when it is {@code issues-enabled} <em>and</em> the user has linked their
 * account for it; both are re-checked here so a hand-crafted request cannot bypass the UI's gating.
 */
@Slf4j
public class DashboardIssueService {

    private static final String CLIENT_TYPE = "dashboard";

    private final ProviderRegistry providers;
    private final RepoPermissionService permissions;
    private final ScmContentInspector contentInspector;
    private final Optional<ScmOAuthTokenStore> tokenStore;
    private final TokenCipherProvider cipherProvider;
    private final ScmApiActionStore auditStore;
    private final ScmApiProposalStore proposalStore;
    private final FogwallConfig fogwallConfig;
    private final DashboardIssueClient client;

    public DashboardIssueService(
            ProviderRegistry providers,
            RepoPermissionService permissions,
            ScmContentInspector contentInspector,
            Optional<ScmOAuthTokenStore> tokenStore,
            TokenCipherProvider cipherProvider,
            ScmApiActionStore auditStore,
            ScmApiProposalStore proposalStore,
            FogwallConfig fogwallConfig,
            DashboardIssueClient client) {
        this.providers = providers;
        this.permissions = permissions;
        this.contentInspector = contentInspector;
        this.tokenStore = tokenStore;
        this.cipherProvider = cipherProvider;
        this.auditStore = auditStore;
        this.proposalStore = proposalStore;
        this.fogwallConfig = fogwallConfig;
        this.client = client;
    }

    private enum Op {
        CREATE("createIssue"),
        EDIT("updateIssue"),
        COMMENT("addComment"),
        CLOSE("closeIssue"),
        REOPEN("reopenIssue");

        final String mutationField;

        Op(String mutationField) {
            this.mutationField = mutationField;
        }
    }

    /**
     * The outcome of an issue operation, mapped to HTTP by the controller. Success carries the created/edited issue.
     */
    public record IssueOutcome(
            int httpStatus, DashboardIssueClient.IssueResult result, String error, List<String> violations) {
        public boolean ok() {
            return result != null;
        }
    }

    /**
     * Provider names the current user may file issues on: {@code issues-enabled} in config and linked by this user via
     * OAuth. Whether any grant matches a given repo is decided per operation, since grants are path patterns.
     */
    public List<String> eligibleProviders(String username) {
        List<String> linked =
                tokenStore.map(s -> s.findLinkedProviders(username)).orElse(List.of());
        List<String> result = new ArrayList<>();
        for (String provider : linked) {
            if (isSupportedAndEnabled(provider)) {
                result.add(provider);
            }
        }
        return result;
    }

    public IssueOutcome createIssue(
            String username, String providerName, String owner, String repo, String title, String body) {
        return perform(
                Op.CREATE,
                username,
                providerName,
                owner,
                repo,
                title,
                body,
                (provider, token) -> client.createIssue(provider, owner, repo, title, body, token));
    }

    public IssueOutcome editIssue(
            String username, String providerName, String owner, String repo, int number, String title, String body) {
        return perform(
                Op.EDIT,
                username,
                providerName,
                owner,
                repo,
                title,
                body,
                (provider, token) -> client.editIssue(provider, owner, repo, number, title, body, token));
    }

    public IssueOutcome comment(
            String username, String providerName, String owner, String repo, int number, String body) {
        return perform(
                Op.COMMENT,
                username,
                providerName,
                owner,
                repo,
                null,
                body,
                (provider, token) -> client.comment(provider, owner, repo, number, body, token));
    }

    public IssueOutcome setState(
            String username, String providerName, String owner, String repo, int number, boolean close) {
        return perform(
                close ? Op.CLOSE : Op.REOPEN,
                username,
                providerName,
                owner,
                repo,
                null,
                null,
                (provider, token) -> client.setState(provider, owner, repo, number, close, token));
    }

    @FunctionalInterface
    private interface UpstreamCall {
        DashboardIssueClient.IssueResult call(FogwallProvider provider, String token);
    }

    private IssueOutcome perform(
            Op op,
            String username,
            String providerName,
            String owner,
            String repo,
            String title,
            String body,
            UpstreamCall upstream) {
        Optional<FogwallProvider> resolved = providers.getProviders().stream()
                .filter(p -> p.getName().equals(providerName))
                .findFirst();
        if (resolved.isEmpty() || !isSupported(resolved.get())) {
            return new IssueOutcome(400, null, "Unknown or unsupported provider: " + providerName, List.of());
        }
        FogwallProvider provider = resolved.get();

        if (!isIssuesEnabled(providerName)) {
            audit(op, provider, username, owner, repo, ScmApiActionStatus.DENIED, "issue filing not enabled", null);
            return new IssueOutcome(403, null, "Issue filing is not enabled for " + providerName, List.of());
        }

        String repoPath = "/" + owner + "/" + repo;
        if (!permissions.isAllowedToFileIssue(username, providerName, repoPath)) {
            audit(op, provider, username, owner, repo, ScmApiActionStatus.DENIED, "no ISSUE/PROPOSE grant", null);
            return new IssueOutcome(
                    403, null, "You do not have permission to file issues on " + owner + "/" + repo, List.of());
        }

        List<String> violations = inspect(op, title, body);
        if (!violations.isEmpty()) {
            audit(op, provider, username, owner, repo, ScmApiActionStatus.REJECTED, "content inspection", null);
            return new IssueOutcome(422, null, "Blocked by content inspection", violations);
        }

        Optional<String> token = accessToken(username, providerName);
        if (token.isEmpty()) {
            audit(op, provider, username, owner, repo, ScmApiActionStatus.DENIED, "no linked OAuth token", null);
            return new IssueOutcome(
                    409, null, "Link your " + providerName + " account before filing issues", List.of());
        }

        try {
            DashboardIssueClient.IssueResult result = upstream.call(provider, token.get());
            int status = op == Op.CREATE ? 201 : 200;
            ScmApiActionRecord action = actionBuilder(
                            op, provider, username, owner, repo, ScmApiActionStatus.FORWARDED, null, status)
                    .build();
            String proposalId = registerProposal(op, provider, username, owner, repo, result, title, action.getId());
            if (proposalId != null) {
                action.setProposalId(proposalId);
            }
            auditStore.save(action);
            return new IssueOutcome(status, result, null, List.of());
        } catch (DashboardIssueClient.IssueApiException e) {
            int upstreamStatus = e.upstreamStatus();
            audit(
                    op,
                    provider,
                    username,
                    owner,
                    repo,
                    ScmApiActionStatus.ERROR,
                    e.getMessage(),
                    upstreamStatus == 0 ? null : upstreamStatus);
            log.warn("Dashboard issue {} on {}/{} at {} failed: {}", op, owner, repo, providerName, e.getMessage());
            return new IssueOutcome(502, null, "The provider rejected the request: " + e.getMessage(), List.of());
        }
    }

    private List<String> inspect(Op op, String title, String body) {
        List<ProposalContent> fields = new ArrayList<>();
        if (op != Op.COMMENT && title != null) {
            fields.add(new ProposalContent("title", title));
        }
        if (body != null) {
            fields.add(new ProposalContent("body", body));
        }
        StringBuilder combined = new StringBuilder();
        if (op != Op.COMMENT && title != null) {
            combined.append(title).append('\n');
        }
        if (body != null) {
            combined.append(body);
        }
        ProposalPayload payload = ProposalPayload.of(combined.toString().getBytes(StandardCharsets.UTF_8), null);
        return contentInspector.inspect(fields, payload);
    }

    private Optional<String> accessToken(String username, String providerName) {
        if (tokenStore.isEmpty()) {
            return Optional.empty();
        }
        Optional<TokenCipher> cipher = cipherProvider.cipher();
        if (cipher.isEmpty()) {
            return Optional.empty();
        }
        return tokenStore
                .get()
                .findAccessToken(username, providerName)
                .map(encrypted -> new String(cipher.get().decrypt(encrypted), StandardCharsets.UTF_8));
    }

    private boolean isIssuesEnabled(String providerName) {
        ProviderConfig config = fogwallConfig.getProviders().get(providerName);
        return config != null && config.isIssuesEnabled();
    }

    private boolean isSupportedAndEnabled(String providerName) {
        if (!isIssuesEnabled(providerName)) {
            return false;
        }
        return providers.getProviders().stream()
                .filter(p -> p.getName().equals(providerName))
                .findFirst()
                .map(DashboardIssueService::isSupported)
                .orElse(false);
    }

    private static boolean isSupported(FogwallProvider provider) {
        return provider instanceof GitHubProvider
                || provider instanceof GitLabProvider
                || provider instanceof ForgejoProvider;
    }

    private ScmApiActionRecord.ScmApiActionRecordBuilder actionBuilder(
            Op op,
            FogwallProvider provider,
            String username,
            String owner,
            String repo,
            ScmApiActionStatus status,
            String reason,
            Integer upstreamStatus) {
        return ScmApiActionRecord.builder()
                .provider(provider.getName())
                .resolvedUser(username)
                .repoOwner(owner)
                .repoName(repo)
                .mutationField(op.mutationField)
                .status(status)
                .reason(reason)
                .clientType(CLIENT_TYPE)
                .upstreamStatus(upstreamStatus);
    }

    private void audit(
            Op op,
            FogwallProvider provider,
            String username,
            String owner,
            String repo,
            ScmApiActionStatus status,
            String reason,
            Integer upstreamStatus) {
        auditStore.save(actionBuilder(op, provider, username, owner, repo, status, reason, upstreamStatus)
                .build());
    }

    /**
     * Records the created or touched issue in the proposal registry — the current-state index the Proposals view reads
     * — and returns the registry row's id to link the action record to it. Create saves a new row; edit refreshes an
     * existing row's title and URL; close/reopen flip its state; comment only bumps an existing row's last-touched
     * marker. Anything but comment registers the issue if fogwall hasn't seen it before. A comment on an issue fogwall
     * never saw isn't registered — a comment response carries no issue title and its URL points at the comment, not the
     * issue. Returns null when nothing was recorded.
     */
    private String registerProposal(
            Op op,
            FogwallProvider provider,
            String username,
            String owner,
            String repo,
            DashboardIssueClient.IssueResult result,
            String title,
            String actionId) {
        int number = result.number();
        Optional<ScmApiProposalRecord> existing =
                proposalStore.findByTarget(provider.getName(), owner, repo, ScmApiProposalRecord.Kind.ISSUE, number);
        ScmApiProposalRecord.State stateChange =
                switch (op) {
                    case CLOSE -> ScmApiProposalRecord.State.CLOSED;
                    case REOPEN -> ScmApiProposalRecord.State.OPEN;
                    default -> null; // create/edit/comment don't move state
                };

        if (op == Op.COMMENT) {
            if (existing.isEmpty()) {
                return null;
            }
            ScmApiProposalRecord row = existing.get();
            row.setLastActionId(actionId);
            row.setUpdatedAt(Instant.now());
            proposalStore.update(row);
            return row.getId();
        }

        if (existing.isPresent()) {
            ScmApiProposalRecord row = existing.get();
            if (title != null) {
                row.setTitle(title);
            }
            if (result.url() != null && !result.url().isEmpty()) {
                row.setUrl(result.url());
            }
            if (stateChange != null) {
                row.setState(stateChange);
            }
            row.setLastActionId(actionId);
            row.setUpdatedAt(Instant.now());
            proposalStore.update(row);
            return row.getId();
        }

        ScmApiProposalRecord row = ScmApiProposalRecord.builder()
                .provider(provider.getName())
                .repoOwner(owner)
                .repoName(repo)
                .kind(ScmApiProposalRecord.Kind.ISSUE)
                .number(number)
                .url(result.url())
                .title(title)
                .state(stateChange != null ? stateChange : ScmApiProposalRecord.State.OPEN)
                .createdBy(username)
                .createdActionId(actionId)
                .lastActionId(actionId)
                .build();
        proposalStore.save(row);
        return row.getId();
    }
}
