package org.yamcs.cfdp.pdu;

import com.google.common.primitives.Bytes;
import org.yamcs.cfdp.CfdpUtils;

import static org.yamcs.cfdp.CfdpUtils.longToTrimmedBytes;

public class ProxyPutRequest extends ReservedMessageToUser {
    public ProxyPutRequest(long destinationEntityId, String sourceFileName, String destinationFileName) {
        super(MessageType.PROXY_PUT_REQUEST, encode(destinationEntityId, sourceFileName, destinationFileName));
    }

    private static byte[] encode(long destinationEntityId, String sourceFileName, String destinationFileName) {
        return Bytes.concat(
            new LV(longToTrimmedBytes(destinationEntityId)).getBytes(),
            new LV(sourceFileName).getBytes(),
            new LV(destinationFileName).getBytes()
        );
    }
}
