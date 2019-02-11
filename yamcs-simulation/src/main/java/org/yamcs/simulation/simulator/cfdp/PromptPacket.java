package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;

public class PromptPacket extends Packet {

    private Header header;
    private ByteBuffer buffer;

    // 0 = NAK
    // 1 = Keep Alive
    private boolean responseRequired;

    public PromptPacket(ByteBuffer buffer, Header header) {
        super(buffer, header);

        this.responseRequired = (buffer.get() >> 7) == 1;
    }

}
