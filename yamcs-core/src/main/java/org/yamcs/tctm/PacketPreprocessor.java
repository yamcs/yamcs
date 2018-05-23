package org.yamcs.tctm;

import org.yamcs.archive.PacketWithTime;

/**
 * The packet preprocessor is responsible for extracting basic information required for yamcs packet processing:
 * - packet generation time
 * - packet acquisition time
 * - sequence count
 * 
 * It is assumed that the (generation time, sequence count) uniquely identify the packet. 
 * 
 ** The implementing classes need to have a constructor with one or two arguments:
 * MyPackerPreprocessor (String yamcsInstance)
 * or
 * MyPackerPreprocessor (String yamcsInstance, Map&lt;String, Object&gt; args)
 * 
 * The second one will be called if the preprocessor is declared with "args". 
 *
 */
public interface PacketPreprocessor {
       
    /**
     * transforms a binary packet into a {@link PacketWithTime}
     * 
     * Can return null if the packet is corrupt
     * 
     * @param packet
     * @return
     */
    public PacketWithTime process(byte[] packet);
}
