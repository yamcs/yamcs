package org.yamcs.cmdhistory;

import org.yamcs.commanding.PreparedCommand;

import org.yamcs.protobuf.Commanding.CommandId;

/**
 * Interface implemented by all the classes that want to receive command history events.
 * @author mache
 *
 */
public interface CommandHistoryConsumer {
	/**
	 * Called when a new command matching the filters has been added to the history
	 * @param che
	 */
	void addedCommand(PreparedCommand pc);
	
	/**
	 * Called command history deliveries - these are requested with 
	 *    subscribeCommandHistory or with getCommandHistory
	 * @param extract
	 */
	void commandHistoryDelivery(CommandHistoryExtract extract);
	
	/**
	 * Called when the history of a command matching the filters has been updated
	 * @param kvp
	 */
	void updatedCommand(CommandId cmdId, long changeDate, String key, String value);

}
