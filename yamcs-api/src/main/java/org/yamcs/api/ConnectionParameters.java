package org.yamcs.api;


abstract public class ConnectionParameters {
    public String host;
    public int port;
    //TODO: get rid of isOK, the classes needing it should use the return from the dialog
    public boolean isOk=false;
    public abstract String getUrl();
}
