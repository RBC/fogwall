package com.rbc.fogwall.net;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.Map;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import lombok.extern.slf4j.Slf4j;

/**
 * Applies a {@link ResolvedOutboundProxy} at the JVM level so JGit's {@code Transport} — which uses the JDK's built-in
 * {@code HttpURLConnection} and respects standard {@code http(s).proxyHost/Port} system properties rather than any
 * fogwall-specific config — picks it up. Call once at startup, before any outbound connection is made.
 *
 * <p>Kerberos ticket acquisition: {@code useSubjectCredsOnly=false} (set unconditionally in Kerberos mode) lets the
 * JDK's GSS provider use the OS's existing ticket cache directly — the default, keytab-free path, matching how a
 * developer machine already has a live ticket from domain/SSO login. Keytab mode installs an in-memory JAAS
 * {@link Configuration} instead, so the JVM authenticates as its own service identity with no external
 * {@code login.conf} file needed.
 */
@Slf4j
public final class OutboundProxySystemProperties {

    private OutboundProxySystemProperties() {}

    public static void apply(ResolvedOutboundProxy proxy) {
        if (proxy == null || !proxy.isConfigured()) {
            log.info("Outbound proxy: not configured (direct connections)");
            return;
        }

        if (proxy.httpProxyHost() != null) {
            System.setProperty("http.proxyHost", proxy.httpProxyHost());
            System.setProperty("http.proxyPort", String.valueOf(proxy.httpProxyPort()));
        }
        if (proxy.httpsProxyHost() != null) {
            System.setProperty("https.proxyHost", proxy.httpsProxyHost());
            System.setProperty("https.proxyPort", String.valueOf(proxy.httpsProxyPort()));
        }
        if (!proxy.noProxyHosts().isEmpty()) {
            System.setProperty("http.nonProxyHosts", String.join("|", proxy.noProxyHosts()));
        }

        switch (proxy.authType()) {
            case BASIC -> applyBasicAuthenticator(proxy);
            case KERBEROS -> applyKerberos(proxy);
            case NONE -> {
                // no credentials — proxy is either open or handles auth upstream itself
            }
        }

        log.info(
                "Outbound proxy configured: http={} https={} auth={}",
                proxy.httpProxyHost() != null ? proxy.httpProxyHost() + ":" + proxy.httpProxyPort() : "-",
                proxy.httpsProxyHost() != null ? proxy.httpsProxyHost() + ":" + proxy.httpsProxyPort() : "-",
                proxy.authType());
    }

    private static void applyBasicAuthenticator(ResolvedOutboundProxy proxy) {
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() != RequestorType.PROXY) {
                    return null;
                }
                return new PasswordAuthentication(
                        proxy.username(), proxy.password().toCharArray());
            }
        });
    }

    private static void applyKerberos(ResolvedOutboundProxy proxy) {
        // Lets the JDK's GSS provider negotiate using whatever the OS ticket cache already holds, with no
        // explicit Subject required — the ticket-cache (keytab-free) default mode.
        System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");

        if (!proxy.isKeytabMode()) {
            return;
        }

        var options = Map.<String, Object>of(
                "useKeyTab", "true",
                "keyTab", proxy.keytabPath(),
                "principal", proxy.principal(),
                "storeKey", "true",
                "doNotPrompt", "true",
                "isInitiator", "true");
        var entry = new AppConfigurationEntry(
                "com.sun.security.auth.module.Krb5LoginModule",
                AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                options);

        Configuration.setConfiguration(new Configuration() {
            @Override
            public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                return new AppConfigurationEntry[] {entry};
            }
        });
        log.info("Kerberos keytab configured for outbound proxy auth: principal={}", proxy.principal());
    }
}
