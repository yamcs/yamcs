package com.spaceapplications.yamcs.scpi.device;

public interface Device {
    public String id();

    public void connect();

    public void disconnect();

    public void write(String cmd);

    public String read();
}
