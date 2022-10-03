package org.yamcs.cfdp;

import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.yarch.Bucket;

/**
 * A Put.request is a primitive that requests data delivery from a source to a destination
 * 
 * @author ddw
 *
 */

public class FilePutRequest extends PutRequest {
    private long sourceId;
    private byte[] fileData;
    private Bucket bucket;
    private boolean overwrite;
    private boolean createpath;
    private long checksum;

    public FilePutRequest(long sourceId, long destinationCFDPEntityId, String sourceFileName, String destinationFileName, boolean overwrite,
            boolean acknowledged, boolean closureRequested, boolean createpath, Bucket b, byte[] data) {
        super(destinationCFDPEntityId, sourceFileName, destinationFileName, null, null,
                null, acknowledged ? CfdpPacket.TransmissionMode.ACKNOWLEDGED : CfdpPacket.TransmissionMode.UNACKNOWLEDGED,
                closureRequested, null, null);
        this.sourceId = sourceId;
        this.overwrite = overwrite;
        this.createpath = createpath;
        this.bucket = b;
        this.fileData = data;
        this.checksum = ChecksumCalculator.calculateChecksum(data);
    }

    public long getSourceId() {
        return this.sourceId;
    }

    public int getFileLength() {
        return fileData.length;
    }

    public long getChecksum() {
        return this.checksum;
    }

    public byte[] getFileData() {
        return fileData;
    }

    public Bucket getBucket() {
        return bucket;
    }

    public boolean getOverwrite() {
        return overwrite;
    }

    public boolean getCreatePath() {
        return createpath;
    }
}
