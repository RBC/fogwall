package com.rbc.fogwall.net;

import static org.junit.jupiter.api.Assertions.*;

import com.rbc.fogwall.config.OutboundProxyConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OutboundProxyResolverTest {

    private static Map<String, String> noEnv() {
        return Map.of();
    }

    @Test
    void resolve_noProxyConfiguredAndNoEnvVars_returnsNone() {
        var resolved = OutboundProxyResolver.resolve(new OutboundProxyConfig(), noEnv()::get);
        assertEquals(ResolvedOutboundProxy.NONE, resolved);
        assertFalse(resolved.isConfigured());
    }

    @Test
    void resolve_yamlValueSet_takesPrecedenceOverEnvVar() {
        var config = new OutboundProxyConfig();
        config.setHttpsProxy("http://yaml-proxy.example.com:8080");
        var env = Map.of("HTTPS_PROXY", "http://env-proxy.example.com:9090");

        var resolved = OutboundProxyResolver.resolve(config, env::get);

        assertEquals("yaml-proxy.example.com", resolved.httpsProxyHost());
        assertEquals(8080, resolved.httpsProxyPort());
    }

    @Test
    void resolve_noYamlValue_fallsBackToEnvVar() {
        var env = Map.of("HTTPS_PROXY", "http://env-proxy.example.com:9090");

        var resolved = OutboundProxyResolver.resolve(new OutboundProxyConfig(), env::get);

        assertEquals("env-proxy.example.com", resolved.httpsProxyHost());
        assertEquals(9090, resolved.httpsProxyPort());
        assertTrue(resolved.isConfigured());
    }

    @Test
    void resolve_noProxyList_expandsBareHostsToAlsoMatchSubdomains() {
        var config = new OutboundProxyConfig();
        config.setHttpsProxy("http://proxy.example.com:8080");
        config.setNoProxy("localhost,*.already-wild.example.com,internal.example.com");

        var resolved = OutboundProxyResolver.resolve(config, noEnv()::get);

        assertTrue(resolved.noProxyHosts().contains("localhost"));
        assertTrue(resolved.noProxyHosts().contains("*.localhost"));
        assertTrue(resolved.noProxyHosts().contains("*.already-wild.example.com"));
        assertFalse(resolved.noProxyHosts().contains("**.already-wild.example.com"));
        assertTrue(resolved.noProxyHosts().contains("internal.example.com"));
        assertTrue(resolved.noProxyHosts().contains("*.internal.example.com"));
    }

    @Test
    void resolve_basicAuthMissingCredentials_throws() {
        var config = new OutboundProxyConfig();
        config.setHttpsProxy("http://proxy.example.com:8080");
        config.getAuth().setType("basic");

        assertThrows(IllegalArgumentException.class, () -> OutboundProxyResolver.resolve(config, noEnv()::get));
    }

    @Test
    void resolve_basicAuthWithCredentials_resolvesSuccessfully() {
        var config = new OutboundProxyConfig();
        config.setHttpsProxy("http://proxy.example.com:8080");
        config.getAuth().setType("basic");
        config.getAuth().setUsername("user");
        config.getAuth().setPassword("pass");

        var resolved = OutboundProxyResolver.resolve(config, noEnv()::get);

        assertEquals(ResolvedOutboundProxy.AuthType.BASIC, resolved.authType());
        assertEquals("user", resolved.username());
        assertEquals("pass", resolved.password());
    }

    @Test
    void resolve_kerberosWithKeytabPathButNoPrincipal_throws() {
        var config = new OutboundProxyConfig();
        config.setHttpsProxy("http://proxy.example.com:8080");
        config.getAuth().setType("kerberos");
        config.getAuth().setKeytabPath("/etc/fogwall/proxy.keytab");

        assertThrows(IllegalArgumentException.class, () -> OutboundProxyResolver.resolve(config, noEnv()::get));
    }

    @Test
    void resolve_kerberosWithNoKeytab_resolvesInTicketCacheMode() {
        var config = new OutboundProxyConfig();
        config.setHttpsProxy("http://proxy.example.com:8080");
        config.getAuth().setType("kerberos");

        var resolved = OutboundProxyResolver.resolve(config, noEnv()::get);

        assertEquals(ResolvedOutboundProxy.AuthType.KERBEROS, resolved.authType());
        assertFalse(resolved.isKeytabMode());
    }

    @Test
    void resolve_invalidProxyUrl_throws() {
        var config = new OutboundProxyConfig();
        config.setHttpsProxy("not-a-valid-url-missing-host");

        assertThrows(IllegalArgumentException.class, () -> OutboundProxyResolver.resolve(config, noEnv()::get));
    }
}
