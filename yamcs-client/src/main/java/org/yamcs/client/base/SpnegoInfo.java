package org.yamcs.client.base;

/**
 * Data holder for SPNEGO information.
 */
public class SpnegoInfo {

    private ServerURL serverURL;
    private boolean verifyTls;
    private String principal;

    public SpnegoInfo(ServerURL serverURL, boolean verifyTls, String principal) {
        this.serverURL = serverURL;
        this.verifyTls = verifyTls;
        this.principal = principal;
    }

    public ServerURL getServerURL() {
        return serverURL;
    }

    public boolean isVerifyTLS() {
        return verifyTls;
    }

    public String getPrincipal() {
        return principal;
    }
}
