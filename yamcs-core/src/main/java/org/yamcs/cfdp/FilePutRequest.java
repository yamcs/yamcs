package org.yamcs.cfdp;

import org.yamcs.buckets.Bucket;
import org.yamcs.cfdp.pdu.CfdpPacket;

/**
 * A Put.request is a primitive that requests data delivery from a source to a destination.
 * <p>
 * The request only references the object to be sent (bucket and object name); the data is read from the bucket when
 * the transfer starts.
 */
public class FilePutRequest extends PutRequest {
    private long sourceId;
    private Bucket bucket;
    private boolean overwrite;
    private boolean createpath;
    private long fileLength;

    /**
     * @param fileLength
     *            the size of the object at the time of the request (it is shown for queued transfers); the transfer
     *            uses the size of the object read when it starts.
     */
    public FilePutRequest(long sourceId, long destinationCfdpEntityId, String sourceFileName,
            String destinationFileName, boolean overwrite, boolean acknowledged, boolean closureRequested,
            boolean createpath, Bucket b, long fileLength) {
        super(destinationCfdpEntityId, sourceFileName, destinationFileName, null, null,
                null,
                acknowledged ? CfdpPacket.TransmissionMode.ACKNOWLEDGED : CfdpPacket.TransmissionMode.UNACKNOWLEDGED,
                closureRequested, null, null);
        this.sourceId = sourceId;
        this.overwrite = overwrite;
        this.createpath = createpath;
        this.bucket = b;
        this.fileLength = fileLength;
    }

    public long getSourceId() {
        return this.sourceId;
    }

    @Override
    public long getFileLength() {
        return fileLength;
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
