package org.yamcs.client;

import org.yamcs.protobuf.Commanding.CommandHistoryEntry;

@FunctionalInterface
public interface CommandListener {

    /**
     * Called when a new command is received and also updated.
     * 
     * @param command
     */
    void onUpdate(Command command);

    /**
     * Same as above but also provides the list of new/updated attributes
     * 
     * @param command
     *            command including all attributes
     * @param cmdHistEntry
     *            the list of new or updated attributes
     */
    default void onUpdate(Command command, CommandHistoryEntry cmdHistEntry) {
    }

    /**
     * Called when an exception claused the call to abort.
     */
    default void onError(Throwable t) {
    }
}
