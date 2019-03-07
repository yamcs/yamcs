package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;

public class PromptPacket extends CfdpPacket {

    // 0 = NAK
    // 1 = Keep Alive
    private boolean responseRequired;

    public PromptPacket(boolean responseRequired, CfdpHeader header) {
        super(header);
        this.responseRequired = responseRequired;
        finishConstruction();
    }

    public PromptPacket(ByteBuffer buffer, CfdpHeader header) {
        super(buffer, header);

        this.responseRequired = (buffer.get() >> 7) == 1;
    }

    @Override
    protected void writeCFDPPacket(ByteBuffer buffer) {
        buffer.put(FileDirectiveCode.Prompt.getCode());
        buffer.put((byte) ((responseRequired ? 1 : 0) << 7));
    }

    @Override
    protected int calculateDataFieldLength() {
        return 2;
    }

}
