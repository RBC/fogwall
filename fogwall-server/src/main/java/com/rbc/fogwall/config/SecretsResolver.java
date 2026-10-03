package com.rbc.fogwall.config;

import com.rbc.fogwall.crypto.AesGcmTokenCipher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Resolves every sensitive key once, while the configuration loads. Each takes either its value form ({@code <key>}) or
 * its file form ({@code <key>-path}); the resolved value is written back to the value field, so consumers never read a
 * secret file themselves. Setting both forms fails. The form that supplied each secret is logged, never its value.
 *
 * <p>A value form is attributed to a YAML file when the merged file layers hold that exact value at the key, and to the
 * environment otherwise: an {@code FOGWALL_} override, or a {@code ${...}} substitution in the YAML.
 */
@Slf4j
final class SecretsResolver {

    /** Which form supplied a secret. */
    enum Source {
        FILE,
        YAML,
        ENVIRONMENT
    }

    private final ObjectNode fileTree;
    private final Map<String, Source> sources = new LinkedHashMap<>();

    private SecretsResolver(ObjectNode fileTree) {
        this.fileTree = fileTree;
    }

    /**
     * Resolves the sensitive keys of {@code config} in place and logs their provenance.
     *
     * @param fileTree the merged file layers {@code config} was bound from, before environment overrides
     * @return the source of each configured secret, keyed by its config key
     * @throws IllegalStateException if both forms of a secret are set, a secret file cannot be read, the token
     *     encryption key is not base64 text of exactly 32 bytes, or only one of the reload git username and password is
     *     set
     */
    static Map<String, Source> resolve(FogwallConfig config, ObjectNode fileTree) {
        SecretsResolver resolver = new SecretsResolver(fileTree);
        resolver.resolveAll(config);
        resolver.logProvenance();
        return Collections.unmodifiableMap(resolver.sources);
    }

    /**
     * Resolves the sensitive keys of {@code config} in place without logging their provenance, for a configuration
     * composed by hot reload. Its secrets are the ones the process started with, already logged and checked.
     *
     * @throws IllegalStateException as {@link #resolve}
     */
    static void resolveQuietly(FogwallConfig config, ObjectNode fileTree) {
        new SecretsResolver(fileTree).resolveAll(config);
    }

    private void resolveAll(FogwallConfig config) {
        DatabaseConfig db = config.getDatabase();
        text(db.getPassword(), db.getPasswordPath(), "database", "password").ifPresent(db::setPassword);

        ServerConfig.RedisConfig redis = config.getServer().getRedis();
        text(redis.getPassword(), redis.getPasswordPath(), "server", "redis", "password")
                .ifPresent(redis::setPassword);

        TlsConfig.KeystoreConfig keystore = config.getServer().getTls().getKeystore();
        if (keystore != null) {
            text(keystore.getPassword(), keystore.getPasswordPath(), "server", "tls", "keystore", "password")
                    .ifPresent(keystore::setPassword);
        }

        OutboundProxyConfig.AuthConfig proxyAuth =
                config.getServer().getOutboundProxy().getAuth();
        text(proxyAuth.getPassword(), proxyAuth.getPasswordPath(), "server", "outbound-proxy", "auth", "password")
                .ifPresent(proxyAuth::setPassword);

        config.getProviders().forEach((name, provider) -> {
            text(provider.getApiToken(), provider.getApiTokenPath(), "providers", name, "api-token")
                    .ifPresent(provider::setApiToken);
            OAuthProviderSettings oauth = provider.getOauth();
            text(oauth.getClientSecret(), oauth.getClientSecretPath(), "providers", name, "oauth", "client-secret")
                    .ifPresent(oauth::setClientSecret);
            if (oauth.isEnabled() && !oauth.getClientId().isBlank() && !isSet(oauth.getClientSecret())) {
                String prefix = "providers." + name + ".oauth.";
                throw new IllegalStateException(prefix + "enabled is set with a client-id, but neither " + prefix
                        + "client-secret nor " + prefix + "client-secret-path is set");
            }
        });

        OidcAuthConfig oidc = config.getAuth().getOidc();
        text(oidc.getClientSecret(), oidc.getClientSecretPath(), "auth", "oidc", "client-secret")
                .ifPresent(oidc::setClientSecret);
        LdapAuthConfig ldap = config.getAuth().getLdap();
        text(ldap.getBindPassword(), ldap.getBindPasswordPath(), "auth", "ldap", "bind-password")
                .ifPresent(ldap::setBindPassword);
        AdAuthConfig ad = config.getAuth().getAd();
        text(ad.getBindPassword(), ad.getBindPasswordPath(), "auth", "ad", "bind-password")
                .ifPresent(ad::setBindPassword);
        AuthConfig auth = config.getAuth();
        text(auth.getApiKey(), auth.getApiKeyPath(), "auth", "api-key").ifPresent(auth::setApiKey);

        ReloadConfig.GitAuthConfig gitAuth = config.getReload().getGit().getAuth();
        text(gitAuth.getPassword(), gitAuth.getPasswordPath(), "reload", "git", "auth", "password")
                .ifPresent(gitAuth::setPassword);
        if (isSet(gitAuth.getUsername()) != isSet(gitAuth.getPassword())) {
            throw new IllegalStateException("reload.git.auth.username and reload.git.auth.password (or "
                    + "reload.git.auth.password-path) must be set together, or neither");
        }

        ScmOAuthSettings scmOAuth = config.getScmOauth();
        tokenKey(scmOAuth.getTokenEncryptionKey(), scmOAuth.getTokenEncryptionKeyPath())
                .ifPresent(scmOAuth::setTokenEncryptionKey);
    }

    /** A text secret. File contents are stripped of surrounding whitespace, such as a trailing newline. */
    private Optional<String> text(String value, String path, String... keyPath) {
        String key = String.join(".", keyPath);
        if (isSet(path)) {
            requireOneForm(key, value);
            return Optional.of(new String(readFile(key, path), StandardCharsets.UTF_8).strip());
        }
        if (isSet(value)) {
            sources.put(key, valueSource(value, keyPath));
            return Optional.of(value);
        }
        return Optional.empty();
    }

    /** The token encryption key, held as given: base64 text that must decode to exactly 32 bytes. */
    private Optional<String> tokenKey(String value, String path) {
        Optional<String> encoded = text(value, path, "scm-oauth", "token-encryption-key");
        encoded.ifPresent(k -> {
            try {
                AesGcmTokenCipher.decodeKey(k);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Cannot load scm-oauth.token-encryption-key: " + e.getMessage(), e);
            }
        });
        return encoded;
    }

    private static boolean isSet(String s) {
        return s != null && !s.isBlank();
    }

    private static void requireOneForm(String key, String value) {
        if (isSet(value)) {
            throw new IllegalStateException(
                    "Both " + key + " and " + key + "-path are set; set only one form of this secret");
        }
    }

    private byte[] readFile(String key, String path) {
        try {
            byte[] content = Files.readAllBytes(Path.of(path));
            sources.put(key, Source.FILE);
            return content;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + key + "-path file " + path + ": " + e, e);
        }
    }

    private Source valueSource(String value, String... keyPath) {
        JsonNode node = fileTree;
        for (String segment : keyPath) {
            if (!(node instanceof ObjectNode object)) {
                return Source.ENVIRONMENT;
            }
            node = object.get(segment.toLowerCase(Locale.ROOT));
            if (node == null) {
                return Source.ENVIRONMENT;
            }
        }
        return node.isValueNode() && value.equals(node.asString()) ? Source.YAML : Source.ENVIRONMENT;
    }

    private void logProvenance() {
        sources.forEach((key, source) -> {
            switch (source) {
                case FILE -> log.info("Secret {} sourced from file", key);
                case ENVIRONMENT -> log.info("Secret {} sourced from the environment", key);
                case YAML -> log.warn("Secret {} is a literal value in a YAML config file; prefer {}-path", key, key);
            }
        });
    }
}
