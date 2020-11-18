package org.yamcs.client.base;

/**
 * Data holder for SPNEGO information.
 */
public class SpnegoInfo {

    private String host;
    private int port;
    private boolean tls;
    private String principal;

    public SpnegoInfo(String host, int port, boolean tls, String principal) {
        this.host = host;
        this.port = port;
        this.tls = tls;
        this.principal = principal;
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

    public String getPrincipal() {
        return principal;
    }
}
