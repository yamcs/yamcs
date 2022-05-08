package org.yamcs.tse;

import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.tse.api.TseCommand;

public interface TsePostprocessor {

    /**
     * Process an instruction passed to TseCommander.
     */
    TseCommand process(TseCommand.Builder commandBuilder);

    /**
     * Sets the command history listener which can be used to provide command history entries related to the command
     * processed.
     */
    default void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryListener) {
    }
}
