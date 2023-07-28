package org.yamcs.ui.packetviewer;

public class ConnectData {
    AuthType authType;
    String serverUrl;
    String username;
    char[] password;

    boolean useServerMdb;
    String localMdbConfig;
    String streamName;
    String instance;
}
