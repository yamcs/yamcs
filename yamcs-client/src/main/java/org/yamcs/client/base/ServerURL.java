package org.yamcs.client.base;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * Parses Yamcs Server URLs and exposes standardized string outputs, as well as access to the individual URL components.
 */
public class ServerURL {

    private final String host;
    private final int port;
    private boolean tls;
    private String context;

    private ServerURL(String host, int port, boolean tls, String context) {
        this.host = host;
        this.port = port;
        this.tls = tls;
        this.context = context;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isTLS() {
        return tls;
    }

    public void setTLS(boolean tls) {
        this.tls = tls;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    /**
     * @throws IllegalArgumentException
     *             when the URL is not something that can be matched to a Yamcs Server URL.
     */
    public static ServerURL parse(String url) {
        url = Objects.requireNonNull(url).trim();
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Failed to parse Server URL", e);
        }

        boolean tls;
        int port = uri.getPort();
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme)) {
            tls = false;
            if (port == -1) {
                port = 80;
            }
        } else if ("https".equalsIgnoreCase(scheme)) {
            tls = true;
            if (port == -1) {
                port = 443;
            }
        } else {
            throw new IllegalArgumentException("Server URL should start with http:// or https://");
        }

        // Remove any leading or trailing slashes
        String context = uri.getRawPath();
        if (context != null && context.length() > 1) {
            if (context.startsWith("/")) {
                context = context.substring(1);
            }
            if (context.endsWith("/")) {
                context = context.substring(0, context.length() - 1);
            }
        }
        if (context != null && (context.isEmpty() || "/".equals(context))) {
            context = null;
        }

        String host = uri.getHost();
        return new ServerURL(host, port, tls, context);
    }

    @Override
    public String toString() {
        String result;
        if (tls) {
            result = "https://" + host;
            if (port != 443) {
                result += ":" + port;
            }
        } else {
            result = "http://" + host;
            if (port != 80) {
                result += ":" + port;
            }
        }
        return context == null ? result : result + "/" + context;
    }
}
