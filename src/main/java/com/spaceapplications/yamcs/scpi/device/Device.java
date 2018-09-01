package com.spaceapplications.yamcs.scpi.device;

import java.util.concurrent.TimeUnit;

public interface Device {
    public String id();

    public void connect();

    public void disconnect();

    public void write(String cmd);

    public String read(long timeout, TimeUnit unit) throws InterruptedException;
}
