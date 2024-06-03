package org.yamcs.tctm;

import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.management.LinkManager;

/**
 * Interface implemented by components that send commands to the outer universe
 * 
 * @author nm
 *
 */
public interface TcDataLink extends Link {
    /**
     * Attempt to send the command and return true if the command has been sent or its processing has finished.
     * <p>
     * If false is returned, the {@link LinkManager} will attempt to send the command via the next TC link (if any).
     * <p>
     * The link is expected to update the {@link CommandHistoryPublisher#AcknowledgeSent_KEY} ack in the command history
     * if the method returned true. If it returned false, the ack should not be updated (it will be updated by the next
     * link or by the Link Manager if there is other no link).
     * <p>
     * The link can update the {@link CommandHistoryPublisher#AcknowledgeSent_KEY} ack with a negative ack and return
     * true (i.e. the command has not been really sent but it has finished processing).
     * <p>
     * The return true/false has been introduced in Yamcs 5.6.0. Before that version, the old method sendTc was
     * implicitly returning true. As of Yamcs 5.6.0 most links return true even when they cannot send the command
     * (setting the negative Sent ack).
     * <p>
     * Throwing an exception is equivalent with returning false, except a error log will be printed (this is considered
     * a bug)
     * 
     * @param preparedCommand
     * @return
     */
    boolean sendCommand(PreparedCommand preparedCommand);

    void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryPublisher);

    default boolean isCommandingAvailable() {
        return !isEffectivelyDisabled();
    }

    /**
     * This method has been introduced to allow classes that implement multiple links (e.g. TM and TC) to not
     * effectively support one ore more of them (depending on configuration)
     * <p>
     * If this method returns false, the {@link LinkManager} skips the link configuration for TC purposes
     */
    default boolean isTcDataLinkImplemented() {
        return true;
    }
}
