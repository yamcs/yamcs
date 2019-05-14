package org.yamcs.tctm;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

/**
 * Receives CFDP packets via UDP. One UDP datagram = one CFDP packet.
 * 
 * @author ddw
 *
 */
public class CfdpUdpTmDataLink extends UdpTmDataLink {

    public CfdpUdpTmDataLink(String instance, String name, YConfiguration config) throws ConfigurationException {
        super(instance, name, config);
    }
}
