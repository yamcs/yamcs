package org.yamcs.cmdhistory;

import org.yamcs.commanding.PreparedCommand;

import org.yamcs.protobuf.Commanding.CommandId;

/**
 * Used by the commanding applications to save commands and commands acknowledgements into a history.
 * @author nm
 *
 */
public interface CommandHistoryPublisher {
    public static String CommandComplete_KEY = "CommandComplete";
    public static String CommandFailed_KEY = "CommandFailed";
    public static String TransmissionContraints_KEY = "TransmissionConstraints";
    public static String Verifier_KEY_PREFIX = "Verifier";
    
    public abstract void publish(CommandId cmdId, String key, String value);
    public abstract void publish(CommandId cmdId, String key, int value);
    public abstract void publish(CommandId cmdId, String key, long value);	
    public abstract void addCommand(PreparedCommand pc);
    
    default void publishWithTime(CommandId cmdId, String key, long time, String value) {
        publish(cmdId, key+"_Time", time);
        publish(cmdId, key+"_Status", value);
    }
}