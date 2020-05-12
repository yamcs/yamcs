package org.yamcs.api.artemis;

import java.net.URI;
import java.net.URISyntaxException;

public class ArtemisConnectionProperties {

    private String host = "localhost";
    private int port;
    private String instance;
    private String username;
    private char[] password;

    private boolean tls;

    public ArtemisConnectionProperties() {
    }

    public ArtemisConnectionProperties(String host, int port, String instance) {
        this.host = host;
        this.port = port;
        this.instance = instance;
    }

    public ArtemisConnectionProperties(String host, int port) {
        this(host, port, null, null);
    }

    public ArtemisConnectionProperties(String host, int port, String username, char[] password) {
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

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public void setTls(boolean tls) {
        this.tls = tls;
    }

    public ArtemisConnectionProperties copy() {
        ArtemisConnectionProperties ycp1 = new ArtemisConnectionProperties(this.host, this.port, this.instance);
        ycp1.tls = this.tls;
        ycp1.username = this.username;
        ycp1.password = this.password;

        return ycp1;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
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
     * uri is artemis[s]://[[username]:[password]@][host[:port]]/[instance]
     * 
     * @param uri
     * @return an object containing the connection properties
     * @throws URISyntaxException
     */
    public static ArtemisConnectionProperties parse(String uri) throws URISyntaxException {
        ArtemisConnectionProperties ycd = new ArtemisConnectionProperties();
        ycd.port = 5445;

        URI u = new URI(uri);
        if (!"yamcs".equalsIgnoreCase(u.getScheme()) && !"artemis".equalsIgnoreCase(u.getScheme())) {
            throw new URISyntaxException(uri, "only yamcs/artemis  scheme allowed");
        }

        if ("yamcss".equals(u.getScheme())) {
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
            throw new URISyntaxException(uri, "Can only support instance/address paths");
        }
        if (pc.length > 1) {
            ycd.instance = pc[1];
        }

        return ycd;
    }

    public String getUrl() {
        StringBuilder sb = new StringBuilder();
        sb.append(tls ? "yamcss://" : "yamcs://");

        if (host != null) {
            sb.append(host);
            if (port != -1) {
                sb.append(":" + port);
            }
        }
        sb.append("/");
        if (instance != null) {
            sb.append(instance);
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
