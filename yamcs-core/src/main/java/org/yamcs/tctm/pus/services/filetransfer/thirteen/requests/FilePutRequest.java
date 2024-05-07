package org.yamcs.tctm.pus.services.filetransfer.thirteen.requests;

import org.yamcs.yarch.Bucket;

/**
 * A Put.request is a primitive that requests data delivery from a source to a
 * destination
 * 
 * @author ddw
 *
 */

public class FilePutRequest extends S13Request {
    private long sourceId;
    private byte[] fileData;
    private Bucket bucket;

    // Required fields
    private final long destinationId;

    // Optional fields
    private String sourceFileName;
    private String destinationFileName;

    public FilePutRequest(long sourceId, long destinationId, String sourceFileName, String destinationFileName, Bucket bucket, byte[] data) {
        super(S13RequestType.PUT);

        this.sourceId = sourceId;
        this.destinationId = destinationId;

        this.bucket = bucket;
        this.fileData = data;

        this.sourceFileName = sourceFileName;
        this.destinationFileName = destinationFileName;
    }

    public long getSourceId() {
        return this.sourceId;
    }

    public int getFileLength() {
        return fileData.length;
    }

    public byte[] getFileData() {
        return fileData;
    }

    public Bucket getBucket() {
        return bucket;
    }

    public long getRemoteId() {
        return destinationId;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public String getDestinationFileName() {
        return destinationFileName;
    }
}
