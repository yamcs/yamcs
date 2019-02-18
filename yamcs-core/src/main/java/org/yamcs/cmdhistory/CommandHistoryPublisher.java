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

    public final static String CommandComplete_KEY = "CommandComplete";
    public final static String CommandFailed_KEY = "CommandFailed";
    public final static String TransmissionContraints_KEY = "TransmissionConstraints";
    public final static String Verifier_KEY_PREFIX = "Verifier";
    public final static String CcsdsSeq_KEY = "ccsds-seqcount";
    
    /**
     * Used by the links to add entries in the command history when the command has been sent via the link.
     * <p>
     * The entries will be &lt;ACK_SENT_CNAME_PREFIX&gt;_Status and &lt;ACK_SENT_CNAME_PREFIX&gt;_Time
     */
    static final public String ACK_SENT_CNAME_PREFIX = "Acknowledge_Sent";

    public abstract void publish(CommandId cmdId, String key, String value);

    public abstract void publish(CommandId cmdId, String key, int value);

    public abstract void publish(CommandId cmdId, String key, long value);

    public abstract void publish(CommandId cmdId, String key, byte[] binary);

    public abstract void addCommand(PreparedCommand pc);

    default void publishWithTime(CommandId cmdId, String key, long time, String value) {
        publish(cmdId, key + "_Time", time);
        publish(cmdId, key + "_Status", value);
    }
    
    default void commandFailed(CommandId commandId, String reason) {
        publish(commandId, CommandFailed_KEY, reason);
    }
}
