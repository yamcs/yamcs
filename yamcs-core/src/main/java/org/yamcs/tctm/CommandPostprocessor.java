package org.yamcs.tctm;

import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;

/**
 * The command post processor is responsible to provide the binary packet that will be send out for a PreparedCommand.
 * 
 * It is used to add sequence counts, compute checkwords, etc
 * 
 * @author nm
 *
 */
public interface CommandPostprocessor {

    public byte[] process(PreparedCommand pc);

    /**
     * sets the command history listener which can be used by the preprocessor to provide command history entries
     * related to the command processed
     * 
     * @param commandHistoryListener
     */
    default void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryListener) {
    }

}
