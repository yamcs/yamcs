package org.yamcs.tctm.ccsds;

import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

/**
 * Stores configuration related to Virtual Channels
 * @author nm
 *
 */
public class VcManagedParameters {
    int vcId;
    //if set to true, the encapsulation packets sent to the preprocessor will be without the encapsulation header(CCSDS 133.1-B-2)
    boolean stripEncapsulationHeader;
    

    // if service = M_PDU
    int maxPacketLength;
    String packetPreprocessorClassName;
    Map<String, Object> packetPreprocessorArgs;
    final YConfiguration config;
    
    public VcManagedParameters(YConfiguration config) {
        this.config = config;
        this.vcId = config.getInt("vcId");
    }
    
    
    protected void parsePacketConfig() {
        maxPacketLength = config.getInt("maxPacketLength", 65536);
        if (maxPacketLength < 7) {
            throw new ConfigurationException("invalid maxPacketLength: " + maxPacketLength);
        }

        packetPreprocessorClassName = config.getString("packetPreprocessorClassName");
        if (config.containsKey("packetPreprocessorArgs")) {
            packetPreprocessorArgs = config.getMap("packetPreprocessorArgs");
        }
        stripEncapsulationHeader = config.getBoolean("stripEncapsulationHeader", false);
    }
}
