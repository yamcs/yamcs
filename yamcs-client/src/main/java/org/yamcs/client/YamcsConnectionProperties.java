package org.yamcs.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

public class YamcsConnectionProperties {

    public static enum AuthType {
        STANDARD,
        KERBEROS;
    }

    private String host = "localhost";
    private int port;
    private String instance;
    private String username;
    private char[] password;

    private boolean tls;
    private AuthType authType = AuthType.STANDARD;

    static final private String PREF_FILENAME = "YamcsConnectionProperties"; // relative to the <home>/.yamcs directory

    public YamcsConnectionProperties() {

    }

    public YamcsConnectionProperties(String host, int port, String instance) {
        this.host = host;
        this.port = port;
        this.instance = instance;
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

    public String getInstance() {
        return instance;
    }

    public AuthType getAuthType() {
        return authType;
    }

    public URI webResourceURI(String relativePath) {
        if (!relativePath.startsWith("/")) {
            relativePath = "/" + relativePath;
        }
        try {
            return new URI((tls ? "https" : "http") + "://" + host + ":" + port + "/api/" + relativePath);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL", e);
        }
    }

    public URI webSocketURI() {
        String urlString = (tls ? "wss" : "ws") + "://" + host + ":" + port + "/_websocket";
        if (instance != null) {
            urlString += "/" + instance;
        }
        try {
            return new URI(urlString);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL", e);
        }
    }

    public static File getPreferenceFile() {
        String home = System.getProperty("user.home") + "/.yamcs";
        return new File(home, PREF_FILENAME);
    }

    public void load() throws FileNotFoundException, IOException {
        Properties p = new Properties();
        p.load(new FileInputStream(getPreferenceFile()));
        host = p.getProperty("host");
        try {
            port = Integer.parseInt(p.getProperty("port"));
        } catch (NumberFormatException e) {
        }

        instance = p.getProperty("instance");
        if (p.containsKey("username")) {
            username = p.getProperty("username");
            password = null;
        }
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("host", host);
        p.setProperty("port", Integer.toString(port));
        if (instance != null) {
            p.setProperty("instance", instance);
        }
        if (username != null) {
            p.setProperty("username", username);
        }
        try {
            String home = System.getProperty("user.home") + "/.yamcs";
            (new File(home)).mkdirs();
            p.store(new FileOutputStream(home + "/" + PREF_FILENAME), null);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public void setTls(boolean tls) {
        this.tls = tls;
    }

    public void setAuthType(AuthType authType) {
        this.authType = authType;
    }

    public YamcsConnectionProperties copy() {
        YamcsConnectionProperties ycp1 = new YamcsConnectionProperties(this.host, this.port, this.instance);
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
        return (tls ? "https" : "http") + "://" + host + ":" + port;
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
     * uri is http[s]://[[username]:[password]@][host[:port]]/[instance]
     * 
     * @param uri
     * @return an object containing the connection properties
     * @throws URISyntaxException
     */
    public static YamcsConnectionProperties parse(String uri) throws URISyntaxException {
        YamcsConnectionProperties ycd = new YamcsConnectionProperties();
        ycd.port = 8090; // Default

        URI u = new URI(uri);
        if (!"http".equalsIgnoreCase(u.getScheme()) && !"https".equalsIgnoreCase(u.getScheme())) {
            throw new URISyntaxException(uri, "URL must be of http or https scheme");
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
            throw new URISyntaxException(uri, "Can only support instance/address paths");
        }
        if (pc.length > 1) {
            ycd.instance = pc[1];
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
