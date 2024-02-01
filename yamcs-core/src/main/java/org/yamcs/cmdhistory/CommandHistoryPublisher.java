package org.yamcs.cmdhistory;

import org.yamcs.commanding.PreparedCommand;
import org.yamcs.parameter.ParameterValue;
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
    public final static String TransmissionConstraints_KEY = "TransmissionConstraints";
    public final static String AcknowledgeQueued_KEY = "Acknowledge_Queued";
    public final static String AcknowledgeReleased_KEY = "Acknowledge_Released";

    /**
     * Used by the links to add entries in the command history when the command has been sent via the link.
     */
    public final static String AcknowledgeSent_KEY = "Acknowledge_Sent";
    public final static String Verifier_KEY_PREFIX = "Verifier";
    public final static String CcsdsSeq_KEY = "ccsds-seqcount";
    public final static String Queue_KEY = "queue";

    // these are used when publishing acks
    public final static String SUFFIX_STATUS = "_Status";
    public final static String SUFFIX_TIME = "_Time";
    public final static String SUFFIX_MESSAGE = "_Message";
    public final static String SUFFIX_RETURN = "_Return";

    public abstract void publish(CommandId cmdId, String key, String value);

    public abstract void publish(CommandId cmdId, String key, int value);

    public abstract void publish(CommandId cmdId, String key, long value);

    public abstract void publish(CommandId cmdId, String key, byte[] binary);

    public default void publish(CommandId cmdId, String key, ParameterValue returnPv) {
    };

    public abstract void addCommand(PreparedCommand pc);

    default void publishAck(CommandId cmdId, String key, long time, AckStatus state) {
        publishAck(cmdId, key, time, state, null, null);
    }

    default void publishAck(CommandId cmdId, String key, long time, AckStatus state, String message) {
        publishAck(cmdId, key, time, state, message, null);
    }

    /**
     * Publish an acknowledgement status to the command history.
     * <p>
     * Entries (corresponding to command history columns) are created:
     * <ul>
     * <li>key_Time</li>
     * <li>key_Status</li>
     * <li>key_Message</li>
     * <li>key_Return</li>
     * </ul>
     */
    default void publishAck(CommandId cmdId, String key, long time, AckStatus state,
            String message, ParameterValue returnPv) {
        publish(cmdId, key + SUFFIX_STATUS, state.toString());
        publish(cmdId, key + SUFFIX_TIME, time);

        if (message != null) {
            publish(cmdId, key + SUFFIX_MESSAGE, message);
        }
        if (returnPv != null) {
            publish(cmdId, key + SUFFIX_RETURN, returnPv);
        }
    }

    default void commandFailed(CommandId cmdId, long time, String reason) {
        publishAck(cmdId, CommandComplete_KEY, time, AckStatus.NOK, reason);
    }

}
