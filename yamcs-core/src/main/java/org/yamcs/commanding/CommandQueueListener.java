package org.yamcs.commanding;

public interface CommandQueueListener {

    default void commandQueueRegistered(String instance, String processorName, CommandQueue q) {
    }

    default void commandQueueUnregistered(String instance, String processorName, CommandQueue cq) {
    }

    default void updateQueue(CommandQueue q) {
    }

    default void commandAdded(CommandQueue q, ActiveCommand pc) {
    }

    default void commandUpdated(CommandQueue q, ActiveCommand pc) {
    }

    default void commandRejected(CommandQueue q, ActiveCommand pc) {
    }

    default void commandSent(CommandQueue q, ActiveCommand pc) {
    }

    default void commandUnhandled(ActiveCommand pc) {
    }
}
