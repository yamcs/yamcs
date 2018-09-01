package com.spaceapplications.yamcs.scpi.device;

public interface Device {
    public String id();

    public void open();

    public void close();

    public void write(String cmd);

    public String read();
}
