package org.yamcs.cfdp.pdu;

import com.google.common.primitives.Bytes;
import org.yamcs.cfdp.CfdpUtils;
import org.yamcs.utils.StringConverter;

import static org.yamcs.cfdp.CfdpUtils.longToTrimmedBytes;

public class ProxyPutRequest extends ReservedMessageToUser {
    private long destinationEntityId;
    private String sourceFileName;
    private String destinationFileName;

    public ProxyPutRequest(long destinationEntityId, String sourceFileName, String destinationFileName) {
        super(MessageType.PROXY_PUT_REQUEST, encode(destinationEntityId, sourceFileName, destinationFileName));
        this.destinationEntityId = destinationEntityId;
        this.sourceFileName = sourceFileName;
        this.destinationFileName = destinationFileName;
    }

    private static byte[] encode(long destinationEntityId, String sourceFileName, String destinationFileName) {
        return Bytes.concat(
            new LV(longToTrimmedBytes(destinationEntityId)).getBytes(),
            new LV(sourceFileName).getBytes(),
            new LV(destinationFileName).getBytes()
        );
    }

    @Override
    public String toJson() {
        return "{type=" + getType() + ", length=" + getValue().length + ", messageType=" + MessageType.PROXY_PUT_REQUEST
                + ", destinationEntityId=" + destinationEntityId + ", sourceFileName=" + sourceFileName + ", destinationFileName=" + destinationFileName + "}";
    }
}
