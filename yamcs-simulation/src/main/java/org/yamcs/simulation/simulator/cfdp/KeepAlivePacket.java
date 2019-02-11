package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;

public class KeepAlivePacket extends Packet {

    private long progress;

    public KeepAlivePacket(ByteBuffer buffer, Header header) {
        super(buffer, header);

        this.progress = Utils.getUnsignedInt(buffer);
    }

}
