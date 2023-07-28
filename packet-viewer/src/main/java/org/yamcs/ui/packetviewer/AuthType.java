package org.yamcs.ui.packetviewer;

public enum AuthType {

    STANDARD("Standard"),
    KERBEROS("Kerberos"),
    BASIC_AUTH("Basic Auth");

    private String prettyName;

    AuthType(String prettyName) {
        this.prettyName = prettyName;
    }

    @Override
    public String toString() {
        return prettyName;
    }
}
