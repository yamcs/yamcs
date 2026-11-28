package org.yamcs.cfdp;

import java.io.IOException;
import java.util.List;

import org.yamcs.cfdp.pdu.FileDataPacket;
import org.yamcs.cfdp.pdu.SegmentRequest;

/**
 * A file being received via CFDP.
 * <p>
 * Keeps track of the received segments and maintains incrementally the modular checksum. Each byte of the file is
 * written only once: if a received File Data PDU overlaps with already received data, only the new parts are written
 * (and accounted for in the checksum).
 * <p>
 * Subclasses decide where the data is stored ({@link DataFile} in memory, {@link DiskDataFile} on disk).
 */
public abstract class AbstractDataFile {
    protected final SegmentTracker segments;
    // -1 means size unknown or unbounded
    protected long size = -1;
    // running modular checksum (not truncated, truncated to 32 bits when returned)
    protected long modularChecksum;

    protected AbstractDataFile(boolean trackCrc) {
        this.segments = new SegmentTracker(trackCrc);
    }

    public synchronized void addSegment(FileDataPacket fdp) {
        if (size != -1 && fdp.getEndOffset() > size) {
            throw new IllegalArgumentException("Segment falls beyond the end of the file");
        }
        final byte[] data = fdp.getData();
        final long offset = fdp.getOffset();
        segments.add(offset, fdp.getEndOffset(), (start, end) -> {
            int idx = (int) (start - offset);
            int len = (int) (end - start);
            int crc = writePiece(data, idx, start, len);
            modularChecksum += ModularChecksumCalculator.calculateChecksum(data, idx, start, len);
            return crc;
        });
    }

    /**
     * Store a piece of data which has not been received before.
     *
     * @return the CRC32C of the piece if the subclass tracks CRCs, otherwise 0
     */
    protected abstract int writePiece(byte[] data, int dataOffset, long fileOffset, int length);

    public synchronized List<SegmentRequest> getMissingChunks() {
        return getMissingChunks(true);
    }

    /**
     * Returns the missing data segments.
     * <p>
     * includeEnd = false is used when the file has been partially transfer to not return a segment covering the end of
     * the file
     */
    public synchronized List<SegmentRequest> getMissingChunks(boolean includeEnd) {
        if (includeEnd && size < 0) {
            throw new IllegalArgumentException("Size is not known, cannot pass includeEnd=true");
        }
        return segments.getMissing(size, includeEnd);
    }

    /**
     * returns the amount of bytes received of this Data Files. Missing intermediate chunks are not yet received and are
     * therefore not counted
     */
    public synchronized long getReceivedSize() {
        return segments.coveredSize();
    }

    /**
     * @return true if all the data has been received. If size is not known return false.
     */
    public synchronized boolean isComplete() {
        return segments.isComplete(size);
    }

    public synchronized long getModularChecksum() {
        return modularChecksum & 0xFFFFFFFFL;
    }

    /**
     * return end of the last segment or -1 if not known
     */
    public synchronized long endOfFileOffset() {
        return segments.endOffset();
    }

    public synchronized void setSize(long size) {
        long eof = endOfFileOffset();
        if (size < 0 || size < eof) {
            throw new IllegalArgumentException("Invalid size");
        }
        this.size = size;
    }

    public synchronized long getSize() {
        return size;
    }

    /**
     * Return the whole file content. The size has to be known.
     */
    public abstract byte[] getData();

    /**
     * Make sure the data received so far is on stable storage. No-op for in-memory files.
     */
    public void sync() throws IOException {
    }

    /**
     * Release the resources (open files) without deleting the data.
     */
    public void close() {
    }

    /**
     * Release the resources and delete the stored data (if any).
     */
    public void delete() {
    }
}
