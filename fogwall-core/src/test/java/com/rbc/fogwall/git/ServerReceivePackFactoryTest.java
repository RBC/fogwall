package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.rbc.fogwall.approval.ApprovalGateway;
import com.rbc.fogwall.approval.SelfApprovalPolicy;
import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.provider.GitHubProvider;
import org.junit.jupiter.api.Test;

/**
 * The push store, approval gateway and self-approval policy are security controls: without the store there is no push
 * record and no approval state, without the gateway nothing gates forwarding, and without the policy a self-approval
 * would forward unchecked. Construction must fail loudly rather than assemble a hook chain that silently skips any of
 * them.
 */
class ServerReceivePackFactoryTest {

    private final GitHubProvider provider = new GitHubProvider("/push");
    private final SelfApprovalPolicy policy = mock(SelfApprovalPolicy.class);

    @Test
    void nullPushStore_refusedAtConstruction() {
        assertThrows(
                NullPointerException.class,
                () -> new ServerReceivePackFactory(
                        provider, CommitConfig.defaultConfig(), null, mock(ApprovalGateway.class), policy));
    }

    @Test
    void nullApprovalGateway_refusedAtConstruction() {
        assertThrows(
                NullPointerException.class,
                () -> new ServerReceivePackFactory(
                        provider, CommitConfig.defaultConfig(), mock(PushStore.class), null, policy));
    }

    @Test
    void bothControlDependenciesPresent_constructs() {
        assertDoesNotThrow(() -> new ServerReceivePackFactory(
                provider, CommitConfig.defaultConfig(), mock(PushStore.class), mock(ApprovalGateway.class), policy));
    }

    @Test
    void nullSelfApprovalPolicy_refusedAtConstruction() {
        assertThrows(
                NullPointerException.class,
                () -> new ServerReceivePackFactory(
                        provider,
                        CommitConfig.defaultConfig(),
                        mock(PushStore.class),
                        mock(ApprovalGateway.class),
                        null));
    }
}
