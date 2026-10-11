package com.rbc.fogwall.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/** Tests for {@link SecretsResolver}: both forms of every sensitive key, the token key formats, and enforcement. */
class SecretsResolverTest {

    @TempDir
    Path tempDir;

    private static final ObjectNode NO_FILES = JsonNodeFactory.instance.objectNode();

    private Path secretFile(String name, String content) throws IOException {
        return Files.writeString(tempDir.resolve(name), content);
    }

    private static Map<String, SecretsResolver.Source> resolve(FogwallConfig config) {
        return SecretsResolver.resolve(config, NO_FILES);
    }

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    @Test
    void everyPathKey_resolvesIntoItsValueField() throws IOException {
        FogwallConfig config = new FogwallConfig();
        config.getDatabase().setPasswordPath(secretFile("db", "db-secret\n").toString());
        config.getServer()
                .getRedis()
                .setPasswordPath(secretFile("redis", "redis-secret").toString());
        TlsConfig.KeystoreConfig keystore = new TlsConfig.KeystoreConfig();
        keystore.setPasswordPath(secretFile("keystore", "keystore-secret").toString());
        config.getServer().getTls().setKeystore(keystore);
        config.getServer()
                .getOutboundProxy()
                .getAuth()
                .setPasswordPath(secretFile("proxy", "proxy-secret").toString());
        ProviderConfig provider = new ProviderConfig();
        provider.setApiTokenPath(secretFile("api-token", "api-token-secret").toString());
        provider.getOauth()
                .setClientSecretPath(secretFile("oauth", "oauth-secret").toString());
        config.getProviders().put("github", provider);
        config.getAuth()
                .getOidc()
                .setClientSecretPath(secretFile("oidc", "oidc-secret").toString());
        config.getAuth()
                .getLdap()
                .setBindPasswordPath(secretFile("ldap", "ldap-secret").toString());
        config.getAuth()
                .getAd()
                .setBindPasswordPath(secretFile("ad", "ad-secret").toString());

        var sources = resolve(config);

        assertEquals("db-secret", config.getDatabase().getPassword());
        assertEquals("redis-secret", config.getServer().getRedis().getPassword());
        assertEquals("keystore-secret", keystore.getPassword());
        assertEquals(
                "proxy-secret", config.getServer().getOutboundProxy().getAuth().getPassword());
        assertEquals("api-token-secret", provider.getApiToken());
        assertEquals("oauth-secret", provider.getOauth().getClientSecret());
        assertEquals("oidc-secret", config.getAuth().getOidc().getClientSecret());
        assertEquals("ldap-secret", config.getAuth().getLdap().getBindPassword());
        assertEquals("ad-secret", config.getAuth().getAd().getBindPassword());
        assertEquals(9, sources.size());
        assertTrue(sources.values().stream().allMatch(s -> s == SecretsResolver.Source.FILE), sources.toString());
    }

    @Test
    void valueForm_isKeptAndAttributedToTheEnvironmentWhenNoFileLayerHoldsIt() {
        FogwallConfig config = new FogwallConfig();
        config.getAuth().getOidc().setClientSecret("literal");

        var sources = resolve(config);

        assertEquals("literal", config.getAuth().getOidc().getClientSecret());
        assertEquals(SecretsResolver.Source.ENVIRONMENT, sources.get("auth.oidc.client-secret"));
    }

    @Test
    void valueForm_matchingTheFileLayer_isAttributedToYaml() {
        FogwallConfig config = new FogwallConfig();
        config.getAuth().getOidc().setClientSecret("literal");
        ObjectNode tree = JsonNodeFactory.instance.objectNode();
        tree.putObject("auth").putObject("oidc").put("client-secret", "literal");

        var sources = SecretsResolver.resolve(config, tree);

        assertEquals(SecretsResolver.Source.YAML, sources.get("auth.oidc.client-secret"));
    }

    @Test
    void valueForm_differingFromTheFileLayer_isAttributedToTheEnvironment() {
        FogwallConfig config = new FogwallConfig();
        config.getAuth().getOidc().setClientSecret("substituted");
        ObjectNode tree = JsonNodeFactory.instance.objectNode();
        tree.putObject("auth").putObject("oidc").put("client-secret", "${OIDC_SECRET}");

        var sources = SecretsResolver.resolve(config, tree);

        assertEquals(SecretsResolver.Source.ENVIRONMENT, sources.get("auth.oidc.client-secret"));
    }

    @Test
    void unsetSecrets_areLeftAloneAndNotReported() {
        FogwallConfig config = new FogwallConfig();

        assertTrue(resolve(config).isEmpty());
        assertEquals("", config.getDatabase().getPassword());
        assertNull(config.getServer().getOutboundProxy().getAuth().getPassword());
    }

    @Test
    void bothForms_failNamingBothKeys() throws IOException {
        FogwallConfig config = new FogwallConfig();
        ProviderConfig provider = new ProviderConfig();
        provider.getOauth().setClientSecret("literal");
        provider.getOauth().setClientSecretPath(secretFile("oauth", "file").toString());
        config.getProviders().put("gitlab", provider);

        var e = assertThrows(IllegalStateException.class, () -> resolve(config));
        assertTrue(e.getMessage().contains("providers.gitlab.oauth.client-secret and"), e.getMessage());
        assertTrue(e.getMessage().contains("providers.gitlab.oauth.client-secret-path"), e.getMessage());
    }

    @Test
    void unreadableFile_failsNamingTheKeyAndPath() {
        FogwallConfig config = new FogwallConfig();
        Path missing = tempDir.resolve("missing");
        config.getAuth().getLdap().setBindPasswordPath(missing.toString());

        var e = assertThrows(IllegalStateException.class, () -> resolve(config));
        assertTrue(e.getMessage().contains("auth.ldap.bind-password-path"), e.getMessage());
        assertTrue(e.getMessage().contains(missing.toString()), e.getMessage());
    }

    // --- token encryption key ---

    @Test
    void tokenKey_base64Value_isAccepted() {
        byte[] key = randomKey();
        FogwallConfig config = new FogwallConfig();
        config.getScmOauth().setTokenEncryptionKey(Base64.getEncoder().encodeToString(key));

        resolve(config);

        assertArrayEquals(key, Base64.getDecoder().decode(config.getScmOauth().getTokenEncryptionKey()));
    }

    @Test
    void tokenKey_raw32CharacterValue_isAccepted() {
        String raw = "0123456789abcdef0123456789abcdef";
        FogwallConfig config = new FogwallConfig();
        config.getScmOauth().setTokenEncryptionKey(raw);

        resolve(config);

        assertArrayEquals(
                raw.getBytes(StandardCharsets.UTF_8),
                Base64.getDecoder().decode(config.getScmOauth().getTokenEncryptionKey()));
    }

    @Test
    void tokenKey_raw32ByteFile_isAccepted() throws IOException {
        byte[] key = randomKey();
        Path file = Files.write(tempDir.resolve("raw-key"), key);
        FogwallConfig config = new FogwallConfig();
        config.getScmOauth().setTokenEncryptionKeyPath(file.toString());

        var sources = resolve(config);

        assertArrayEquals(key, Base64.getDecoder().decode(config.getScmOauth().getTokenEncryptionKey()));
        assertEquals(SecretsResolver.Source.FILE, sources.get("scm-oauth.token-encryption-key"));
    }

    @Test
    void tokenKey_base64FileWithTrailingNewline_isAccepted() throws IOException {
        byte[] key = randomKey();
        FogwallConfig config = new FogwallConfig();
        config.getScmOauth()
                .setTokenEncryptionKeyPath(
                        secretFile("b64-key", Base64.getEncoder().encodeToString(key) + "\n")
                                .toString());

        resolve(config);

        assertArrayEquals(key, Base64.getDecoder().decode(config.getScmOauth().getTokenEncryptionKey()));
    }

    @Test
    void tokenKey_inNeitherForm_failsNamingBothForms() throws IOException {
        FogwallConfig config = new FogwallConfig();
        config.getScmOauth()
                .setTokenEncryptionKeyPath(
                        secretFile("short-key", Base64.getEncoder().encodeToString(new byte[16]))
                                .toString());

        var e = assertThrows(IllegalStateException.class, () -> resolve(config));
        assertTrue(e.getMessage().contains("scm-oauth.token-encryption-key"), e.getMessage());
        assertTrue(e.getMessage().contains("32 raw bytes"), e.getMessage());
        assertTrue(e.getMessage().contains("base64"), e.getMessage());
    }

    @Test
    void tokenKey_missingFile_fails() {
        FogwallConfig config = new FogwallConfig();
        config.getScmOauth()
                .setTokenEncryptionKeyPath(tempDir.resolve("missing").toString());

        assertThrows(IllegalStateException.class, () -> resolve(config));
    }

    // --- secrets.require-file-sourcing ---

    @Test
    void requireFileSourcing_refusesYamlLiteralNamingIt() {
        FogwallConfig config = new FogwallConfig();
        config.getSecrets().setRequireFileSourcing(true);
        config.getDatabase().setPassword("literal");
        ObjectNode tree = JsonNodeFactory.instance.objectNode();
        tree.putObject("database").put("password", "literal");

        var e = assertThrows(IllegalStateException.class, () -> SecretsResolver.resolve(config, tree));
        assertTrue(e.getMessage().contains("database.password (yaml)"), e.getMessage());
    }

    @Test
    void requireFileSourcing_refusesEnvironmentValue() {
        FogwallConfig config = new FogwallConfig();
        config.getSecrets().setRequireFileSourcing(true);
        config.getScmOauth().setTokenEncryptionKey(Base64.getEncoder().encodeToString(randomKey()));

        var e = assertThrows(IllegalStateException.class, () -> resolve(config));
        assertTrue(e.getMessage().contains("scm-oauth.token-encryption-key (environment)"), e.getMessage());
    }

    @Test
    void requireFileSourcing_acceptsFileForms() throws IOException {
        FogwallConfig config = new FogwallConfig();
        config.getSecrets().setRequireFileSourcing(true);
        config.getDatabase().setPasswordPath(secretFile("db", "db-secret").toString());

        assertEquals(Map.of("database.password", SecretsResolver.Source.FILE), resolve(config));
    }

    @Test
    void requireFileSourcingOff_acceptsValues() {
        FogwallConfig config = new FogwallConfig();
        config.getDatabase().setPassword("literal");

        assertDoesNotThrow(() -> resolve(config));
    }

    // --- OAuth client secret ---

    @Test
    void oauthEnabledWithClientId_andNoClientSecret_failsNamingBothKeys() {
        FogwallConfig config = new FogwallConfig();
        ProviderConfig provider = new ProviderConfig();
        provider.getOauth().setEnabled(true);
        provider.getOauth().setClientId("the-client");
        config.getProviders().put("github", provider);

        var e = assertThrows(IllegalStateException.class, () -> resolve(config));
        assertTrue(e.getMessage().contains("providers.github.oauth.client-secret nor"), e.getMessage());
        assertTrue(e.getMessage().contains("providers.github.oauth.client-secret-path"), e.getMessage());
    }

    @Test
    void oauthEnabledWithClientSecretFile_resolves() throws IOException {
        FogwallConfig config = new FogwallConfig();
        ProviderConfig provider = new ProviderConfig();
        provider.getOauth().setEnabled(true);
        provider.getOauth().setClientId("the-client");
        provider.getOauth()
                .setClientSecretPath(secretFile("oauth", "oauth-secret\n").toString());
        config.getProviders().put("github", provider);

        resolve(config);

        assertEquals("oauth-secret", provider.getOauth().getClientSecret());
    }

    // --- secrets kept out of toString ---

    @Test
    void resolvedConfig_toStringContainsNoSecretValue() throws IOException {
        FogwallConfig config = new FogwallConfig();
        config.getDatabase().setPasswordPath(secretFile("db", "SECRET-db").toString());
        config.getServer().getRedis().setPassword("SECRET-redis");
        TlsConfig.KeystoreConfig keystore = new TlsConfig.KeystoreConfig();
        keystore.setPassword("SECRET-keystore");
        config.getServer().getTls().setKeystore(keystore);
        config.getServer().getOutboundProxy().getAuth().setPassword("SECRET-proxy");
        ProviderConfig provider = new ProviderConfig();
        provider.setApiToken("SECRET-api-token");
        provider.getOauth().setEnabled(true);
        provider.getOauth().setClientId("the-client");
        provider.getOauth().setClientSecret("SECRET-oauth");
        config.getProviders().put("github", provider);
        config.getAuth().getOidc().setClientSecret("SECRET-oidc");
        config.getAuth().getLdap().setBindPassword("SECRET-ldap");
        config.getAuth().getAd().setBindPassword("SECRET-ad");
        String rawKey = "SECRET-key-0123456789abcdefghijk";
        config.getScmOauth().setTokenEncryptionKey(rawKey);

        resolve(config);
        String rendered = config.toString();

        assertFalse(rendered.contains("SECRET-"), rendered);
        assertFalse(rendered.contains(config.getScmOauth().getTokenEncryptionKey()), rendered);
        assertTrue(rendered.contains("the-client"), rendered);
    }

    // --- quiet resolution for hot reload ---

    @Test
    void resolveQuietly_resolvesFileForms() throws IOException {
        FogwallConfig config = new FogwallConfig();
        config.getAuth()
                .getAd()
                .setBindPasswordPath(secretFile("ad", "ad-secret").toString());

        SecretsResolver.resolveQuietly(config, NO_FILES);

        assertEquals("ad-secret", config.getAuth().getAd().getBindPassword());
    }
}
