package org.yamcs.cmdhistory;

import java.util.List;

import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandId;

/**
 * Interface implemented by all the classes that want to receive command history events.
 *
 */
public interface CommandHistoryConsumer {
    /**
     * Called when a new command matching the filters has been added to the history
     * 
     * @param pc
     */
    void addedCommand(PreparedCommand pc);

    void updatedCommand(CommandId cmdId, long time, List<Attribute> attrs);

}
