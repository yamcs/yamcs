package org.yamcs.cfdp;

/**
 * A Put.request is a primitive that requests data delivery from a source to a destination
 * 
 * @author ddw
 *
 */

public class PutRequest extends CfdpRequest {

    private int sourceId;
    private int destinationId;
    private String targetPath;
    private byte[] packetData;

    public PutRequest(int sourceId, int destinationId, String targetPath, byte[] data) {
        super(CfdpRequestType.PUT);
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.targetPath = targetPath;
        this.packetData = data;
    }

    public int getSourceId() {
        return this.sourceId;
    }

    public int getDestinationId() {
        return destinationId;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public int getPacketLength() {
        return packetData.length;
    }

    public byte[] getPacketData() {
        return packetData;
    }

}
