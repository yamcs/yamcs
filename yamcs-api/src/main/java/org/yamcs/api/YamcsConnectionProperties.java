package org.yamcs.api;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

import org.yamcs.ConfigurationException;

public class YamcsConnectionProperties {
    private String host = "localhost";
    private int port;
    private String instance;
    private String username;
    private char[] password;

    public static enum Protocol {
        http, artemis;
    }

    private Protocol protocol;
    private boolean ssl;

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

    public URI webResourceURI(String relativePath) {
        if (!relativePath.startsWith("/")) {
            relativePath = "/" + relativePath;
        }
        try {
            return new URI("http://" + host + ":" + port + "/api/" + relativePath);
        } catch (URISyntaxException e) {
            throw new ConfigurationException("Invalid URL", e);
        }
    }

    public URI webSocketURI() {
        try {
            return new URI("ws://" + host + ":" + port + "/_websocket/" + instance);
        } catch (URISyntaxException e) {
            throw new ConfigurationException("Invalid URL", e);
        }
    }

    @Deprecated
    public URI webSocketURI(boolean legacyMode) {
        if (legacyMode) {
            try {
                return new URI("ws://" + host + ":" + port + "/" + instance + "/_websocket");
            } catch (URISyntaxException e) {
                throw new ConfigurationException("Invalid URL", e);
            }
        } else {
            return webSocketURI();
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

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    @Override
    public YamcsConnectionProperties clone() {
        YamcsConnectionProperties ycp1 = new YamcsConnectionProperties(this.host, this.port, this.instance);
        ycp1.ssl = this.ssl;
        ycp1.protocol = this.protocol;
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

    /**
     * Return the base REST API URL for connecting to yamcs
     *
     * @return A sting of the shape http://host:port/api
     */
    public String getRestApiUrl() {
        return "http://" + host + ":" + port + "/api";
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
     * uri is protocol://[[username]:[password]@][host[:port]]/[instance]
     * 
     * @param uri
     * @return an object containing the connection properties
     * @throws URISyntaxException
     */
    public static YamcsConnectionProperties parse(String uri) throws URISyntaxException {
        YamcsConnectionProperties ycd = new YamcsConnectionProperties();
        URI u = new URI(uri);
        if ("yamcs".equalsIgnoreCase(u.getScheme()) || "artemis".equalsIgnoreCase(u.getScheme())) {
            ycd.protocol = Protocol.artemis;
            ycd.port = 5445; // default port, might be overwritten below if the port is part of the URI
        } else if ("http".equalsIgnoreCase(u.getScheme()) || "https".equalsIgnoreCase(u.getScheme())) {
            ycd.protocol = Protocol.http;
            ycd.port = 8090; // default port, might be overwritten below if the port is part of the URI
        } else {
            throw new URISyntaxException(uri, "only http, https or yamcs/artemis  scheme allowed");
        }

        if ("https".equals(u.getScheme()) || "yamcss".equals(u.getScheme())) {
            ycd.ssl = true;
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

    public Protocol getProtocol() {
        return protocol;
    }

    public String getUrl() {
        StringBuilder sb = new StringBuilder();
        sb.append(protocol + "://");
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

    @Override
    public String toString() {
        return getUrl();
    }
}
