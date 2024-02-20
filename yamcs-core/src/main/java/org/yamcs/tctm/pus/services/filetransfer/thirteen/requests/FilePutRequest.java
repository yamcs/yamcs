package org.yamcs.tctm.pus.services.filetransfer.thirteen.requests;

import org.yamcs.yarch.Bucket;

/**
 * A Put.request is a primitive that requests data delivery from a source to a
 * destination
 * 
 * @author ddw
 *
 */

public class FilePutRequest extends PutRequest {
    private long sourceId;
    private byte[] fileData;
    private Bucket bucket;

    public FilePutRequest(long sourceId, long destinationId, String sourceFileName,
            String destinationFileName, boolean createpath, Bucket b, byte[] data) {
        super(destinationId, sourceFileName, destinationFileName);
        this.sourceId = sourceId;
        this.bucket = b;
        this.fileData = data;
    }

    public long getSourceId() {
        return this.sourceId;
    }

    @Override
    public int getFileLength() {
        return fileData.length;
    }

    @Override
    public byte[] getFileData() {
        return fileData;
    }

    public Bucket getBucket() {
        return bucket;
    }
}
