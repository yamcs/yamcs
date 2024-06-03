package org.yamcs.cfdp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.yamcs.cfdp.pdu.FileDataPacket;
import org.yamcs.cfdp.pdu.SegmentRequest;

public class DataFile {
    List<Segment> dataFileSegments = new ArrayList<Segment>();
    // -1 means size unknown or unbounded
    private int size = -1;
    byte[] data;
    static int MAX_SIZE = Integer.MAX_VALUE;

    public DataFile() {
        this(-1);
    }

    public DataFile(long size) {
        this.size = checkMaxSize(size);
        if (size > MAX_SIZE) {
            throw new UnsupportedOperationException(
                    "file transfers larger than " + MAX_SIZE + " not supported");
        }
        if (size > 0) {
            this.data = new byte[(int) size];
        } else {
            this.data = new byte[1024];
        }
    }

    public synchronized void addSegment(FileDataPacket fdp) {
        if (size != -1 && fdp.getEndOffset() > size) {
            throw new IllegalArgumentException("Segment falls beyond the end of the file");
        }

        Segment newseg = new Segment(fdp.getOffset(), fdp.getEndOffset());

        if (dataFileSegments.size() == 0) {
            dataFileSegments.add(newseg);
            addData(fdp);
            return;
        }
        var lastSegment = dataFileSegments.get(dataFileSegments.size() - 1);
        if (lastSegment.end == newseg.start) {
            // happy case the new segment comes right after the last segment
            lastSegment.end = newseg.end;
            addData(fdp);
            return;
        }

        int idx = Collections.binarySearch(dataFileSegments, newseg,
                (s1, s2) -> Long.compare(s1.start, s2.start));

        if (idx >= 0) {// newseg offset coincides with an existing segment offset
            Segment seg1 = dataFileSegments.get(idx);
            assert (seg1.start == newseg.start);
            if (seg1.end < newseg.end) {
                // newseg is longer than seg1 and starts at the same offset
                // replace seg1 with newseg
                seg1.end = newseg.end;
            }
        } else { // the segment does not start at the same offset with an existing segment
                 // but it might still overlap with one
            idx = -(idx + 1);
            if (idx == dataFileSegments.size()) {

                Segment prevseg = dataFileSegments.get(idx - 1);
                if (prevseg.end > newseg.start) {
                    // overlaps with the last segment
                    // extend the last segment
                    if (prevseg.end < newseg.end) {
                        prevseg.end = newseg.end;
                    }
                } else {
                    dataFileSegments.add(newseg);
                }
            } else {
                if (idx == 0) {
                    Segment nextseg = dataFileSegments.get(0);
                    if (newseg.end >= nextseg.start) {
                        nextseg.start = newseg.start;
                    } else {
                        dataFileSegments.add(0, newseg);
                    }
                } else {
                    Segment prevseg = dataFileSegments.get(idx - 1);
                    Segment nextseg = dataFileSegments.get(idx);

                    if (prevseg.end >= newseg.start && newseg.end >= nextseg.start) {
                        // overlaps with both prev and next
                        prevseg.end = nextseg.end;
                        dataFileSegments.remove(idx);
                    } else if (prevseg.end >= newseg.start) {
                        // overlaps only with prev
                        if (newseg.end >= prevseg.end) {
                            prevseg.end = newseg.end;
                        }
                    } else if (newseg.end >= nextseg.start) {
                        // overlaps only with next
                        nextseg.start = newseg.start;
                    } else { // does not overlap
                        dataFileSegments.add(idx, newseg);
                    }
                }
            }
        }

        addData(fdp);
    }

    private void addData(FileDataPacket fdp) {
        if (data.length < fdp.getEndOffset()) {
            checkMaxSize(fdp.getEndOffset());
            var length = (int) Long.min(fdp.getEndOffset() + 1024 * 1024, MAX_SIZE);
            data = Arrays.copyOf(data, length);
        }
        System.arraycopy(fdp.getData(), 0, data, (int) fdp.getOffset(), fdp.getLength());
    }

    public synchronized List<SegmentRequest> getMissingChunks() {
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
    public synchronized List<SegmentRequest> getMissingChunks(boolean includeEnd) {
        List<SegmentRequest> toReturn = new ArrayList<SegmentRequest>();
        long startOffset = 0;
        long endOffset = 0;
        if (includeEnd && size < 0) {
            throw new IllegalArgumentException("Size is not known, cannot pass includeEnd=true");
        }
        for (Segment segment : dataFileSegments) {
            if (segment.start != startOffset) {
                endOffset = segment.start;
                toReturn.add(new SegmentRequest(startOffset, endOffset));
            }
            startOffset = segment.end;
        }
        if (startOffset != size && includeEnd) {
            toReturn.add(new SegmentRequest(startOffset, size));
        }
        return toReturn;
    }

    // returns the amount of bytes received of this Data Files.
    // Missing intermediate chunks are not yet received and are therefore not counted
    public synchronized long getReceivedSize() {
        return this.dataFileSegments.stream().mapToLong(Segment::length).sum();
    }

    public synchronized byte[] getData() {
        if (size == -1) {
            throw new IllegalStateException("Size unknown");
        }
        if (data.length > size) {
            return Arrays.copyOf(data, size);
        } else {
            return data;
        }
    }

    /**
     * 
     * @return true if all the data has been received. If size is not known return false.
     */
    public synchronized boolean isComplete() {
        if (size < 0) {
            return false;
        }
        if (dataFileSegments.size() != 1) {
            return false;
        }
        var seg0 = dataFileSegments.get(0);
        return seg0.start == 0 && seg0.end == size;
    }

    public synchronized long getChecksum() {
        long checksum = 0;
        for (Segment segment : this.dataFileSegments) {
            checksum += ChecksumCalculator.calculateChecksum(data, segment.start, segment.length());
        }
        return checksum & 0xFFFFFFFFl;
    }

    /**
     * return end of the last segment or -1 if not known
     *
     * @return
     */
    public synchronized long endOfFileOffset() {
        if (!dataFileSegments.isEmpty()) {
            Segment seg = dataFileSegments.get(dataFileSegments.size() - 1);
            return seg.end;
        } else {
            return -1;
        }
    }

    public synchronized void setSize(long size) {
        long eof = endOfFileOffset();
        if (size < 0 || size < eof) {
            throw new IllegalArgumentException("Invalid size");
        }
        this.size = checkMaxSize(size);
    }

    public synchronized long getSize() {
        return size;
    }

    public static int checkMaxSize(long size) {
        if (size > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "file transfers larger than " + Integer.MAX_VALUE + " not supported");
        }
        return (int) size;
    }

    static class Segment {
        long start;
        long end;

        public Segment(long start, long end) {
            this.start = start;
            this.end = end;
        }

        public int length() {
            return (int) (end - start);
        }
    }

}
