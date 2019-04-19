package org.yamcs.cfdp;

import java.util.Arrays;

import org.yamcs.yarch.Bucket;

/**
 * A Put.request is a primitive that requests data delivery from a source to a destination
 * 
 * @author ddw
 *
 */

public class PutRequest extends CfdpRequest {

    private long sourceId;
    private long destinationId;
    private String targetPath;
    private byte[] packetData;
    private String objectName;
    private Bucket bucket;
    private boolean overwrite;
    private boolean createpath;
    private long checksum;
    private boolean acknowledged;

    public PutRequest(long sourceId, long destinationId, String objectName, String targetPath, boolean overwrite,
            boolean acknowledged, boolean createpath, Bucket b, byte[] data) {
        super(CfdpRequestType.PUT);
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.objectName = objectName;
        this.targetPath = targetPath;
        this.overwrite = overwrite;
        this.createpath = createpath;
        this.bucket = b;
        this.packetData = data;
        this.acknowledged = acknowledged;
        this.checksum = calculateChecksum(data);
    }

    public long getSourceId() {
        return this.sourceId;
    }

    public long getDestinationId() {
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

    public long getChecksum() {
        return this.checksum;
    }

    public byte[] getPacketData() {
        return packetData;
    }

    public Bucket getBucket() {
        return bucket;
    }

    public boolean getOverwrite() {
        return overwrite;
    }

    public boolean isAcknowledged() {
        return this.acknowledged;
    }

    public boolean getCreatePath() {
        return createpath;
    }

    private long calculateChecksum(byte[] data) {
        long toReturn = 0;
        for (int i = 0; i < data.length; i += 4) {
            toReturn = addPartToChecksum(toReturn, Arrays.copyOfRange(data, i, i + 4));
        }
        return toReturn;
    }

    private long addPartToChecksum(long checksum, byte[] partOfData) {
        checksum += ((partOfData[0] & 0xFF) << 24) & 0xFF000000L
                | ((partOfData[1] & 0xFF) << 16) & 0xFF0000L
                | ((partOfData[2] & 0xFF) << 8) & 0xFF00L
                | (partOfData[3] & 0xFF);
        return checksum & 0x00000000FFFFFFFFL;
    }

}
