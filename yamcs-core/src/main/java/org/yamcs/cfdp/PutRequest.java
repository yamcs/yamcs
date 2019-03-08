package org.yamcs.cfdp;

import org.yamcs.yarch.Bucket;

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
    private String objectName;
    private Bucket bucket;

    public PutRequest(int sourceId, int destinationId, String objectName, String targetPath, Bucket b, byte[] data) {
        super(CfdpRequestType.PUT);
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.objectName = objectName;
        this.targetPath = targetPath;
        this.bucket = b;
        this.packetData = data;
    }

    public int getSourceId() {
        return this.sourceId;
    }

    public int getDestinationId() {
        return destinationId;
    }

    public String getObjectName() {
        return objectName;
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

    public Bucket getBucket() {
        return bucket;
    }

}
