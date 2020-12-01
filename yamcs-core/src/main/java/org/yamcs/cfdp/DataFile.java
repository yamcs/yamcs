package org.yamcs.cfdp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.yamcs.cfdp.pdu.SegmentRequest;
import org.yamcs.utils.ByteArray;

public class DataFile {
    private List<DataFileSegment> dataFileSegments = new ArrayList<DataFileSegment>();
    // -1 means size unknown or unbounded
    private long size = -1;

    public DataFile() {
        this.size = -1;
    }

    public DataFile(long size) {
        this.size = size;
    }

    public void addSegment(DataFileSegment newseg) {
        if (size != -1 && newseg.getLength() + newseg.getOffset() > size) {
            throw new IllegalArgumentException("Segment falls beyond the end of the file");
        }

        int idx = Collections.binarySearch(dataFileSegments, newseg,
                (s1, s2) -> Long.compare(s1.getOffset(), s2.getOffset()));

        if (idx >= 0) {// newseg offset coincides with an existing segment offset
            DataFileSegment seg1 = dataFileSegments.get(idx);
            assert (seg1.getOffset() == newseg.getOffset());
            if (newseg.getLength() > seg1.getLength()) {
                // newseg is longer than seg1 and starts at the same offset
                // add one small segment with the delta
                byte[] data = Arrays.copyOfRange(newseg.getData(), seg1.getLength(), newseg.getLength());
                DataFileSegment seg3 = new DataFileSegment(seg1.getOffset() + seg1.getLength(), data);
                dataFileSegments.add(idx + 1, seg3);
            }
        } else { // the segment does not start at the same offset with an existing segment
                 // but it might still overlap with one
            idx = -(idx + 1);
            if (idx == dataFileSegments.size()) {
                if (dataFileSegments.size() == 0) {
                    dataFileSegments.add(newseg);
                } else {
                    DataFileSegment prevseg = dataFileSegments.get(idx - 1);
                    if (prevseg.getOffset() + prevseg.getLength() > newseg.getOffset()) {
                        // overlaps with the last segment
                        int from = prevseg.getLength() - (int) (newseg.getOffset() - prevseg.getOffset());

                        byte[] data = Arrays.copyOfRange(newseg.getData(), from, newseg.getLength());
                        DataFileSegment seg2 = new DataFileSegment(prevseg.getOffset() + prevseg.getLength(), data);
                        dataFileSegments.add(seg2);
                    } else {
                        dataFileSegments.add(newseg);
                    }
                }
            } else {
                if (idx == 0) {
                    DataFileSegment nextseg = dataFileSegments.get(0);
                    if (newseg.getOffset() + newseg.getLength() > nextseg.getOffset()) {
                        int nl = (int) (nextseg.getOffset() - newseg.getOffset());
                        byte[] data = Arrays.copyOfRange(newseg.getData(), 0, nl);
                        DataFileSegment seg1 = new DataFileSegment(newseg.getOffset(), data);
                        dataFileSegments.add(0, seg1);
                    } else {
                        dataFileSegments.add(0, newseg);
                    }
                } else {
                    DataFileSegment prevseg = dataFileSegments.get(idx - 1);
                    DataFileSegment nextseg = dataFileSegments.get(idx);
                    int from, to;
                    long offset;
                    if (prevseg.getOffset() + prevseg.getLength() <= newseg.getOffset()) {
                        from = 0;
                        offset = newseg.getOffset();
                    } else {
                        from = prevseg.getLength() - (int) (newseg.getOffset() - prevseg.getOffset());
                        offset = prevseg.getOffset() + prevseg.getLength();
                    }

                    if (newseg.getOffset() + newseg.getLength() <= nextseg.getOffset()) {
                        to = newseg.getLength();
                    } else {
                        to = (int) (nextseg.getOffset() - newseg.getOffset());
                    }

                    if (from == 0 && to == newseg.getLength()) {
                        dataFileSegments.add(idx, newseg);
                    } else {
                        byte[] data = Arrays.copyOfRange(newseg.getData(), from, to);
                        dataFileSegments.add(idx, new DataFileSegment(offset, data));
                    }
                }
            }
        }
    }

    public List<SegmentRequest> getMissingChunks() {
        return getMissingChunks(true);
    }

    /**
     * Returns the missing data segments.
     * <p>
     * includeEnd = false is used when the file has been partially transfer to not return a segment covering the end of
     * the file
     *
     * @param includeEnd
     * @return
     */
    public List<SegmentRequest> getMissingChunks(boolean includeEnd) {
        List<SegmentRequest> toReturn = new ArrayList<SegmentRequest>();
        long startOffset = 0;
        long endOffset = 0;
        if (includeEnd && size < 0) {
            throw new IllegalArgumentException("Size is not known, cannot pass includeEnd=true");
        }
        for (DataFileSegment segment : dataFileSegments) {
            if (segment.getOffset() != startOffset) {
                endOffset = segment.getOffset();
                toReturn.add(new SegmentRequest(startOffset, endOffset));
            }
            startOffset = segment.getOffset() + segment.getLength();
        }
        if (startOffset != size && includeEnd) {
            toReturn.add(new SegmentRequest(startOffset, size));
        }
        return toReturn;
    }

    // returns the amount of bytes received of this Data Files.
    // Missing intermediate chunks are not yet received and are therefore not counted
    public long getReceivedSize() {
        return this.dataFileSegments.stream().mapToLong(DataFileSegment::getLength).sum();
    }

    public byte[] getData() {
        ByteArray baos = new ByteArray();
        dataFileSegments.stream().forEach(e -> {
            baos.add(e.getData());
        });
        return baos.toArray();
    }

    List<DataFileSegment> getSegments() {
        return dataFileSegments;
    }

    /**
     * 
     * @return true if all the data has been received. If size is not known return false.
     */
    public boolean isComplete() {
        if (size < 0) {
            return false;
        }
        long startOffset = 0;
        for (DataFileSegment segment : this.dataFileSegments) {
            if (segment.getOffset() != startOffset) {
                return false;
            }
            startOffset = segment.getOffset() + segment.getLength();
        }
        if (startOffset != size) {
            return false;
        }
        return true;
    }

    public long getChecksum() {
        long checksum = 0;
        for (DataFileSegment segment : this.dataFileSegments) {
            checksum += ChecksumCalculator.calculateChecksum(segment);
        }
        return checksum & 0xFFFFFFFFl;
    }

    /**
     * return end of the last segment or -1 if not known
     *
     * @return
     */
    public long endOfFileOffset() {
        if (!dataFileSegments.isEmpty()) {
            DataFileSegment dfs = dataFileSegments.get(dataFileSegments.size() - 1);
            return dfs.getOffset() + dfs.getLength();
        } else {
            return -1;
        }
    }

    public void setSize(long size) {
        long eof = endOfFileOffset();
        if (size < 0 || size < eof) {
            throw new IllegalArgumentException("Invalid size");
        }
        this.size = size;
    }

    public long getSize() {
        return size;
    }

}
