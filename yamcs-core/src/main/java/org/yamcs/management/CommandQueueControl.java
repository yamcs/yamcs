package org.yamcs.management;

public interface CommandQueueControl {
    public String getName();
    public String getState();
    public int getCommandCount();
}
