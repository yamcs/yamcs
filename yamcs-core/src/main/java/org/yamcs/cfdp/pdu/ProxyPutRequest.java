package org.yamcs.cfdp.pdu;

import com.google.common.primitives.Bytes;
import org.yamcs.cfdp.CfdpUtils;
import org.yamcs.utils.StringConverter;

import java.nio.ByteBuffer;

import static org.yamcs.cfdp.CfdpUtils.longToTrimmedBytes;

public class ProxyPutRequest extends ReservedMessageToUser {
    private final long destinationEntityId;
    private final String sourceFileName;
    private final String destinationFileName;

    public ProxyPutRequest(long destinationEntityId, String sourceFileName, String destinationFileName) {
        super(MessageType.PROXY_PUT_REQUEST, encode(destinationEntityId, sourceFileName, destinationFileName));
        this.destinationEntityId = destinationEntityId;
        this.sourceFileName = sourceFileName;
        this.destinationFileName = destinationFileName;
    }

    public ProxyPutRequest(byte[] content) {
        super(MessageType.PROXY_PUT_REQUEST, content);

        ByteBuffer buffer = ByteBuffer.wrap(content);
        this.destinationEntityId = CfdpUtils.getUnsignedLongFromByteArray(LV.readLV(buffer).getValue());
        this.sourceFileName = new String(LV.readLV(buffer).getValue());
        this.destinationFileName = new String(LV.readLV(buffer).getValue());
    }

    private static byte[] encode(long destinationEntityId, String sourceFileName, String destinationFileName) {
        return Bytes.concat(
            new LV(longToTrimmedBytes(destinationEntityId)).getBytes(),
            new LV(sourceFileName).getBytes(),
            new LV(destinationFileName).getBytes()
        );
    }

    public long getDestinationEntityId() {
        return destinationEntityId;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public String getDestinationFileName() {
        return destinationFileName;
    }


    @Override
    public String toJson() {
        return "{type=" + getType() + ", length=" + getValue().length + ", messageType=" + MessageType.PROXY_PUT_REQUEST
                + ", destinationEntityId=" + destinationEntityId + ", sourceFileName=" + sourceFileName + ", destinationFileName=" + destinationFileName + "}";
    }


    @Override
    public String toString() {
        return "ProxyPutRequest(destinationEntityId: " + destinationEntityId + ", sourceFileName: " + sourceFileName + ", destinationFileName: " + destinationFileName + ")";
    }
}
