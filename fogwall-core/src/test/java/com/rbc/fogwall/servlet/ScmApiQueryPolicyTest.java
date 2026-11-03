package com.rbc.fogwall.servlet;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ScmApiQueryPolicyTest {

    private static final boolean MUTATION = true;
    private static final boolean READ = false;

    @Test
    void noQueryString_isPermitted() {
        assertNull(ScmApiQueryPolicy.refusedParameter(null, READ));
        assertNull(ScmApiQueryPolicy.refusedParameter("", MUTATION));
        assertNull(ScmApiQueryPolicy.refusedParameter("&&", MUTATION));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "in=title%2Cdescription&assignee_id=1&wip=yes&include_rebase_in_progress=true",
                "state=open&labels=bug&page=2&per_page=50",
                "access_token=x",
                "sudo=root"
            })
    void aReadIsNeverRefusedForItsParameters(String query) {
        assertNull(ScmApiQueryPolicy.refusedParameter(query, READ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"access_token", "private_token", "token", "sudo"})
    void aWriteCarryingACredentialOrIdentityParameterIsRefused(String name) {
        assertEquals(name, ScmApiQueryPolicy.refusedParameter(name + "=x", MUTATION));
        assertEquals(name, ScmApiQueryPolicy.refusedParameter("a=1&" + name, MUTATION), "a bare name counts too");
    }

    @Test
    void aWriteParameterIsMatchedAfterDecodingAndLowerCasing() {
        assertEquals("token", ScmApiQueryPolicy.refusedParameter("%74oken=x", MUTATION));
        assertEquals("sudo", ScmApiQueryPolicy.refusedParameter("SUDO=root", MUTATION));
    }

    @Test
    void anyOtherParameterOnAWriteIsLeftToTheCli() {
        assertNull(ScmApiQueryPolicy.refusedParameter("state=closed&license=true", MUTATION));
    }
}
