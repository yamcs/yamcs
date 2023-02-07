package org.yamcs.cfdp.pdu;

import org.yamcs.cfdp.CfdpUtils;
import org.yamcs.cfdp.pdu.FinishedPacket.FileStatus;

public class ProxyPutResponse extends ReservedMessageToUser {
    private final ConditionCode conditionCode;
    private final boolean dataComplete; // delivery code (0 - true, 1 - false) TODO: maybe use correct name
    private final FileStatus fileStatus;

    public ProxyPutResponse(ConditionCode conditionCode, boolean dataComplete, FinishedPacket.FileStatus fileStatus) {
        super(MessageType.PROXY_PUT_RESPONSE, encode(conditionCode, dataComplete, fileStatus));
        this.conditionCode = conditionCode;
        this.dataComplete = dataComplete;
        this.fileStatus = fileStatus;
    }

    public ProxyPutResponse(byte[] content) {
        super(MessageType.PROXY_PUT_REQUEST, content);

        byte b = content[0];
        this.conditionCode = ConditionCode.readConditionCode(b);
        this.dataComplete = !CfdpUtils.isBitOfByteSet(b, 5);
        this.fileStatus = FinishedPacket.FileStatus.fromCode((byte) (b & 0x03));
    }

    private static byte[] encode(ConditionCode conditionCode, boolean dataComplete, FinishedPacket.FileStatus fileStatus) {
        byte b = (byte) ((conditionCode.getCode() << 4));
        b |= ((dataComplete ? 0 : 1) << 2);
        b |= (fileStatus.getCode() & 0x03);
        return new byte[]{ b };
    }

    public ConditionCode getConditionCode() {
        return conditionCode;
    }

    public boolean isDataComplete() {
        return dataComplete;
    }

    public int getDeliveryCode() {
        return dataComplete ? 0 : 1;
    }

    public FileStatus getFileStatus() {
        return fileStatus;
    }

    @Override
    public String toJson() {
        return "{type=" + getType() + ", length=" + getValue().length + ", messageType=" + MessageType.PROXY_PUT_RESPONSE
                + ", conditionCode=" + conditionCode + ", dataComplete=" + dataComplete + ", fileStatus=" + fileStatus + "}";
    }


    @Override
    public String toString() {
        return "ProxyPutResponse(conditionCode: " + conditionCode + ", dataComplete: " + dataComplete + ", fileStatus: " + fileStatus + ")";
    }
}
