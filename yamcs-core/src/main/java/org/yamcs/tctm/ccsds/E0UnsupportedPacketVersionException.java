package org.yamcs.tctm.ccsds;

import org.yamcs.tctm.TcTmException;

@SuppressWarnings("serial")
public class E0UnsupportedPacketVersionException extends TcTmException {

    int packetVersion;

    public E0UnsupportedPacketVersionException(int packetVersion) {
        this.packetVersion = packetVersion;
    }

    @Override
    public String toString() {
        return "Unsupported packet type " + packetVersion;
    }
}
