package org.yamcs.tctm;

import org.yamcs.TmPacket;

/**
 * The packet preprocessor is responsible for extracting basic information required for yamcs packet processing:
 * <ul>
 * <li>packet generation time</li>
 * <li>packet acquisition time</li>
 * <li>sequence count</li>
 * </ul>
 * <br>
 * 
 * It is assumed that the (generation time, sequence count) uniquely identify the packet.
 * <br>
 * <br>
 * The implementing classes need to have a constructor with one or two arguments:
 * <ul>
 * <li>MyPackerPreprocessor (String yamcsInstance), or</li>
 * <li>MyPackerPreprocessor (String yamcsInstance, Map&lt;String, Object&gt; args)</li>
 * </ul>
 * <br>
 * 
 * The second one will be called if the preprocessor is declared with "args".
 *
 */
public interface PacketPreprocessor {

    /**
     * transforms a binary packet into a {@link TmPacket}
     * 
     * Can return null if the packet is corrupt
     * 
     * @deprecated please use {@link #process(TmPacket)} instead as it preserves packet properties such as earth
     *             reception time set by the frame link
     * @param packet
     * @return
     */
    @Deprecated
    default TmPacket process(byte[] packet) {
        throw new RuntimeException("Please implement or use the process(TmPacket) method instead");
    }

    /**
     * Processes the packet and returns it.
     * <p>
     * What this function does is project depended. However, we expect that the generation time and sequence count are
     * filled in.
     * <p>
     * Can return null if the packet is to be ignored.
     * 
     * @param pwt
     *            - the packet that has to be processed
     * @return the processed packet
     */
    default TmPacket process(TmPacket pwt) {
        return process(pwt.getPacket());
    }

    /**
     * The packet preprocessor processes multiple packets that should be in sequence. This flag can be used to check
     * that indeed the packets are in sequence and produce a warning otherwise.
     * 
     * @param checkForSequenceDiscontinuity
     */
    default void checkForSequenceDiscontinuity(boolean checkForSequenceDiscontinuity) {
    }
}
