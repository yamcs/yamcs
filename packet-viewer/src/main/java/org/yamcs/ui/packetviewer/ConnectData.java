package org.yamcs.ui.packetviewer;

public class ConnectData {
    String host;
    int port;
    boolean tls;
    String username;
    char[] password;
    String contextPath;

    boolean useServerMdb;
    String localMdbConfig;
    String streamName;
    String instance;
}
