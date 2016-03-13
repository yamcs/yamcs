package org.yamcs.cmdhistory;

import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.parameter.Value;

/**
 * Interface implemented by all the classes that want to receive command history events.
 * @author mache
 *
 */
public interface CommandHistoryConsumer {
	/**
	 * Called when a new command matching the filters has been added to the history
	 * @param pc
	 */
	void addedCommand(PreparedCommand pc);
	
	
	/**
	 * Called when the history of a command matching the filters has been updated
	 *
	 */
	void updatedCommand(CommandId cmdId, long changeDate, String key, Value value);

}
