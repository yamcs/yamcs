package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;

import org.yamcs.cfdp.FileDirective;

public class PromptPacket extends CfdpPacket implements FileDirective {

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
        buffer.put(getFileDirectiveCode().getCode());
        buffer.put((byte) ((responseRequired ? 1 : 0) << 7));
    }

    @Override
    protected int calculateDataFieldLength() {
        return 2;
    }

    @Override
    public FileDirectiveCode getFileDirectiveCode() {
        return FileDirectiveCode.Prompt;
    }

    @Override
    public String toString() {
        return "PromptPacket [responseRequired=" + responseRequired + "]";
    }

}
