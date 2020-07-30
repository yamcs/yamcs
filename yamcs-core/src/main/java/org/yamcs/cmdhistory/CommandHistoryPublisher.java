package org.yamcs.cmdhistory;

import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandId;

/**
 * Used by the commanding applications to save commands and commands acknowledgements into a history.
 * 
 * @author nm
 *
 */
public interface CommandHistoryPublisher {
    /**
     * provides acknowledgment status during execution of command
     *
     */
    enum AckStatus {
        NA, SCHEDULED, PENDING, OK, NOK, TIMEOUT, CANCELLED, DISABLED
    };

    public final static String CommandComplete_KEY = "CommandComplete";
    public final static String TransmissionContraints_KEY = "TransmissionConstraints";
    public final static String AcknowledgeQueued_KEY = "Acknowledge_Queued";
    public final static String AcknowledgeReleased_KEY = "Acknowledge_Released";
    
    /**
     * Used by the links to add entries in the command history when the command has been sent via the link.
     */
    public final static String AcknowledgeSent = "Acknowledge_Sent";
    public final static String Verifier_KEY_PREFIX = "Verifier";
    public final static String CcsdsSeq_KEY = "ccsds-seqcount";
    public final static String Queue_KEY = "queue";

    //these are used when publishing acks
    public final static String SUFFIX_STATUS="_Status";
    public final static String SUFFIX_TIME="_Time";
    public final static String SUFFIX_MESSAGE="_Message";

    

    public abstract void publish(CommandId cmdId, String key, String value);

    public abstract void publish(CommandId cmdId, String key, int value);

    public abstract void publish(CommandId cmdId, String key, long value);

    public abstract void publish(CommandId cmdId, String key, byte[] binary);

    public abstract void addCommand(PreparedCommand pc);

    /**
     * Publish an acknowledgement status to the command history.
     * <p>
     * Three entries (corresponding to command history columns) are created:
     * <ul>
     * <li>key_Time</li>
     * <li>key_Status</li>
     * <li>key_Message</li>
     * </ul>
     * 
     * @param cmdId
     * @param key
     * @param time
     * @param state
     * @param message
     */
    default void publishAck(CommandId cmdId, String key, long time, AckStatus state, String message) {
        publish(cmdId, key + "_Status", state.toString());
        publish(cmdId, key + "_Time", time);
        if (message != null) {
            publish(cmdId, key + "_Message", message);
        }
    }

    default void publishAck(CommandId cmdId, String key, long time, AckStatus state) {
        publishAck(cmdId, key, time, state, null);
    }

    default void commandFailed(CommandId cmdId, long time, String reason) {
        publishAck(cmdId, CommandComplete_KEY, time, AckStatus.NOK, reason);
    }

}
