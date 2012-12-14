package org.yamcs.commanding;

public interface CommandQueueListener {
    public void updateQueue(CommandQueue q);
    public void commandAdded(CommandQueue q, PreparedCommand pc);
    public void commandRejected(CommandQueue q, PreparedCommand pc);
    public void commandSent(CommandQueue q, PreparedCommand pc);
}
