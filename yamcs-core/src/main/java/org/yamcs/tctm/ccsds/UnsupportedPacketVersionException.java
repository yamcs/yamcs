package org.yamcs.tctm.ccsds;

import org.yamcs.tctm.TcTmException;

public class UnsupportedPacketVersionException extends TcTmException {
    int packetVersion;
    
    public UnsupportedPacketVersionException(int packetVersion) {
        this.packetVersion = packetVersion;
    }
    
    public String toString() {
        return "Unsupported packet type "+packetVersion;
    }
}
