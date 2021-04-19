package org.yamcs.commanding;

import org.yamcs.ProcessorService;
import org.yamcs.cmdhistory.CommandHistoryPublisher;

/**
 * This is responsible for "releasing" a command.
 * 
 */
public interface CommandReleaser extends ProcessorService {
    /**
     * release a command.
     * 
     * @param preparedCommand
     */
    void releaseCommand(PreparedCommand preparedCommand);

    /**
     * the command releaser has to add the command to the history when it is released.
     * 
     * @param commandHistory
     */
    void setCommandHistory(CommandHistoryPublisher commandHistory);
}
