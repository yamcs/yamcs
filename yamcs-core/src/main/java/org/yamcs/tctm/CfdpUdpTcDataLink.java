package org.yamcs.tctm;

import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;

public class CfdpUdpTcDataLink extends UdpTcDataLink {
    public CfdpUdpTcDataLink(String yamcsInstance, String name, Map<String, Object> config)
            throws ConfigurationException {
        super(yamcsInstance, name, config);
    }

    public CfdpUdpTcDataLink(String yamcsInstance, String name, String spec) throws ConfigurationException {
        this(yamcsInstance, name, YConfiguration.getConfiguration("cfdp-udp").getMap(spec));
    }
}
