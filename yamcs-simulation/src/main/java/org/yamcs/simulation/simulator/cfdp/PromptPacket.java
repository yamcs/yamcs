package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;

public class PromptPacket extends Packet {

    // 0 = NAK
    // 1 = Keep Alive
    private boolean responseRequired;

    public PromptPacket(ByteBuffer buffer, Header header) {
        super(buffer, header);

        this.responseRequired = (buffer.get() >> 7) == 1;
    }

    @Override
    protected void writeCFDPPacket(ByteBuffer buffer) {
        super.writeCFDPPacket(buffer);
        buffer.put((byte) ((responseRequired ? 1 : 0) << 7));
    }

}
