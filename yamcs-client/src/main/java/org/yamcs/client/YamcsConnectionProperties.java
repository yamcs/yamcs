package org.yamcs.client;

import java.net.URI;
import java.net.URISyntaxException;

public class YamcsConnectionProperties {

    public static enum AuthType {
        STANDARD,
        KERBEROS;
    }

    private String host = "localhost";
    private int port;
    private String context;
    private String username;
    private char[] password;

    private boolean tls;
    private AuthType authType = AuthType.STANDARD;

    public YamcsConnectionProperties() {

    }

    public YamcsConnectionProperties(String host, int port, String context) {
        this.host = host;
        this.port = port;
        this.context = context;
    }

    public YamcsConnectionProperties(String host, int port) {
        this(host, port, null, null);
    }

    public YamcsConnectionProperties(String host, int port, String username, char[] password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getContext() {
        return context;
    }

    public AuthType getAuthType() {
        return authType;
    }

    public URI webResourceURI(String relativePath) {
        if (!relativePath.startsWith("/")) {
            relativePath = "/" + relativePath;
        }
        try {
            if (context == null) {
                return new URI((tls ? "https" : "http") + "://" + host + ":" + port + "/api/" + relativePath);
            } else {
                return new URI((tls ? "https" : "http") + "://" + host + ":" + port
                        + "/" + context + "/api/" + relativePath);
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL", e);
        }
    }

    public void setContext(String context) {
        this.context = context;
    }

    public void setTls(boolean tls) {
        this.tls = tls;
    }

    public void setAuthType(AuthType authType) {
        this.authType = authType;
    }

    public YamcsConnectionProperties copy() {
        YamcsConnectionProperties ycp1 = new YamcsConnectionProperties(this.host, this.port, this.context);
        ycp1.tls = this.tls;
        ycp1.username = this.username;
        ycp1.password = this.password;
        ycp1.authType = this.authType;

        return ycp1;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getBaseURL() {
        if (context == null) {
            return (tls ? "https" : "http") + "://" + host + ":" + port;
        } else {
            return (tls ? "https" : "http") + "://" + host + ":" + port + "/" + context;
        }
    }

    /**
     * Return the base REST API URL for connecting to yamcs
     *
     * @return A string of the shape http://host:port/api
     */
    public String getRestApiUrl() {
        return getBaseURL() + "/api";
    }

    public String getUsername() {
        return username;
    }

    public char[] getPassword() {
        return password;
    }

    public void setCredentials(String username, char[] password) {
        this.username = username;
        this.password = password;
    }

    /**
     * uri is http[s]://[[username]:[password]@][host[:port]]/[context]
     * 
     * @param uri
     * @return an object containing the connection properties
     * @throws IllegalArgumentException
     */
    public static YamcsConnectionProperties parse(String uri) throws IllegalArgumentException {
        YamcsConnectionProperties ycd = new YamcsConnectionProperties();
        ycd.port = 8090; // Default

        URI u = null;
        try {
            u = new URI(uri);
            if (!"http".equalsIgnoreCase(u.getScheme()) && !"https".equalsIgnoreCase(u.getScheme())) {
                throw new IllegalArgumentException("URL must be of http or https scheme. Was given '" + uri + "'");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid address '" + uri + "'");
        }

        if ("https".equals(u.getScheme())) {
            ycd.tls = true;
        }
        if (u.getPort() != -1) {
            ycd.port = u.getPort();
        }
        ycd.host = u.getHost();

        if (u.getUserInfo() != null) {
            String[] ui = u.getRawUserInfo().split(":");
            String username = ui[0];
            char[] password = null;
            if (ui.length > 1) {
                password = ui[1].toCharArray();
            }
            ycd.username = username;
            ycd.password = password;
        }

        String[] pc = u.getPath().split("/");
        if (pc.length > 3) {
            throw new IllegalArgumentException("Invalid address '" + uri + "'");
        }
        if (pc.length > 1) {
            ycd.context = pc[1];
        }

        return ycd;
    }

    public String getUrl() {
        StringBuilder sb = new StringBuilder();
        sb.append(tls ? "https://" : "http://");
        if (host != null) {
            sb.append(host);
            if (port != -1) {
                sb.append(":" + port);
            }
        }
        sb.append("/");
        if (context != null) {
            sb.append(context);
        }
        return sb.toString();
    }

    public boolean isTls() {
        return tls;
    }

    @Override
    public String toString() {
        return getUrl();
    }
}
