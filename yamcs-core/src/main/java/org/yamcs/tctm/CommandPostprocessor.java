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

    /**
     * processes the command and returns the binary buffer.
     * 
     * Returns null if the command cannot be processed (e.g. its size does not correspond to what this processor expects).
     * In this case the postprocessor is expected to fail the command in the command history (also filling in an appropiate reason)
     * @param pc
     * @return
     */
    public byte[] process(PreparedCommand pc);

    /**
     * sets the command history listener which can be used by the preprocessor to provide command history entries
     * related to the command processed
     * 
     * @param commandHistoryListener
     */
    default void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryListener) {
    }


    /**
     * Return the size of the binary packet for this command.
     * <p>
     * This is required in the frame links which bundle multiple commands together to know if the command will fit into
     * the frame before post-processing it.
     * 
     * @param pc
     * @return the size of the binary packet which the method {@link #process(PreparedCommand)} will return.
     */
    default int getBinaryLength(PreparedCommand pc) {
        return pc.getBinary().length;
    }

}
