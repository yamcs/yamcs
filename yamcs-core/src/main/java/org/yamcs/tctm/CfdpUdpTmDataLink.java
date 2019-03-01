package org.yamcs.tctm;

import java.util.Map;

import org.yamcs.ConfigurationException;

/**
 * Receives CFDP packets via UDP. One UDP datagram = one CFDP packet.
 * 
 * @author ddw
 *
 */
public class CfdpUdpTmDataLink extends UdpTmDataLink {

    public CfdpUdpTmDataLink(String instance, String name, Map<String, Object> args) throws ConfigurationException {
        super(instance, name, args);
    }
}
