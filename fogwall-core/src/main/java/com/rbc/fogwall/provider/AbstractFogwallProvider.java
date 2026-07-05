package com.rbc.fogwall.provider;

import com.rbc.fogwall.servlet.FogwallServlet;
import java.net.URI;
import lombok.RequiredArgsConstructor;

/** An upstream Git server that will be proxied by the application. */
@RequiredArgsConstructor
public abstract class AbstractFogwallProvider implements FogwallProvider {

    protected final String name;
    protected final String type;
    protected final URI uri;
    protected final String pathSuffix;

    /**
     * Returns the path that the servlet will be mapped to after the default path (this controls routing to the
     * appropriate servlet or JGit factory). This is based on the host of the target URL along with an optional
     * application-wide base path. To configure a {@link FogwallServlet} for proxying, use {@link #servletMapping()}
     * instead.
     *
     * @return The base path that this provider will be mapped to.
     */
    @Override
    public String servletPath() {
        int port = uri.getPort();
        if (pathSuffix != null && !pathSuffix.isBlank()) {
            return pathSuffix;
        }
        boolean defaultPort = port < 0
                || ("https".equals(uri.getScheme()) && port == 443)
                || ("http".equals(uri.getScheme()) && port == 80);

        return defaultPort ? "/" + uri.getHost() : "/" + uri.getHost() + ":" + port;
    }

    /**
     * Returns the servlet mapping for the provider. This is used to map the servlet to a specific path in the
     * application. Since this mapping is always used for setting up underlying proxying servlet, the mapping will
     * always append a wildcard end of the path to ensure that all matching requests are proxied.
     *
     * <p>Matcher functions should use {@link #servletPath()} instead.
     *
     * @return the servlet mapping for the provider
     */
    @Override
    public final String servletMapping() {
        return servletPath() + "/*";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" + "name='"
                + name + '\'' + ", type='"
                + type + '\'' + ", uri="
                + uri + ", basePath='"
                + pathSuffix + '\'' + '}';
    }
}
