package org.yamcs.commanding;

public interface CommandQueueListener {
    default void commandQueueRegistered(String instance, String processorName, CommandQueue q) {}
    default void commandQueueUnregistered(String instance, String processorName, CommandQueue cq){}
    default void updateQueue(CommandQueue q) {}
    default void commandAdded(CommandQueue q, PreparedCommand pc) {}
    default void commandRejected(CommandQueue q, PreparedCommand pc){}
    default void commandSent(CommandQueue q, PreparedCommand pc){}
   
}
