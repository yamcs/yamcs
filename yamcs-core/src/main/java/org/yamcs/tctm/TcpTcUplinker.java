package org.yamcs.tctm;

import org.yamcs.ConfigurationException;
import org.yamcs.utils.DeprecationInfo;

/**
 * Sends raw ccsds packets on Tcp socket.
 * @author nm
 * 
 * @deprecated this class has been renamed to {@TcpTcDataLink}
 */
@Deprecated
@DeprecationInfo(info = "this class has been renamed to TcpTcDataLink")
public class TcpTcUplinker extends TcpTcDataLink {
    public TcpTcUplinker(String yamcsInstance, String name, String spec) throws ConfigurationException {
      super(yamcsInstance, name, spec);
    }

    protected TcpTcUplinker() {
        super();
    }

    public TcpTcUplinker(String host, int port) {
        super(host, port);
    }
}

