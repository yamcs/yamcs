package org.yamcs.commanding;

import org.yamcs.cmdhistory.CommandHistoryPublisher;

import com.google.common.util.concurrent.Service;

/**
 * This is responsible for "releasing" a prepared command.
 * 
 *  We do this because the prepared command will not have all the  necessary attributes such as sequence count and checksum
 *  
 *  Those need to be computed separately anyway because the command goes into a queue and when it's released the sequence number has to be incremented.
 *  
 * 
 * @author nm
 *
 */
public interface CommandReleaser extends Service {
    /**
     * release a command. 
     * @param preparedCommand
     */
    void releaseCommand(PreparedCommand preparedCommand);

    /**
     * the command releaser has to add the command to the history when it is released. 
     * @param commandHistory
     */
    void setCommandHistory(CommandHistoryPublisher commandHistory);
}
