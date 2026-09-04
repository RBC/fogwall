package com.rbc.fogwall.git;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.rbc.fogwall.approval.ApprovalGateway;
import com.rbc.fogwall.config.CommitConfig;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.provider.GitHubProvider;
import org.junit.jupiter.api.Test;

/**
 * The push store and approval gateway are security controls: without the store there is no push record and no approval
 * state, and without the gateway nothing gates forwarding. Construction must fail loudly rather than assemble a hook
 * chain that silently skips both.
 */
class ServerReceivePackFactoryTest {

    private final GitHubProvider provider = new GitHubProvider("/push");

    @Test
    void nullPushStore_refusedAtConstruction() {
        assertThrows(
                NullPointerException.class,
                () -> new ServerReceivePackFactory(
                        provider, CommitConfig.defaultConfig(), null, mock(ApprovalGateway.class)));
    }

    @Test
    void nullApprovalGateway_refusedAtConstruction() {
        assertThrows(
                NullPointerException.class,
                () -> new ServerReceivePackFactory(
                        provider, CommitConfig.defaultConfig(), mock(PushStore.class), null));
    }

    @Test
    void bothControlDependenciesPresent_constructs() {
        assertDoesNotThrow(() -> new ServerReceivePackFactory(
                provider, CommitConfig.defaultConfig(), mock(PushStore.class), mock(ApprovalGateway.class)));
    }
}
