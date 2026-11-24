package org.yamcs.cfdp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.yamcs.cfdp.pdu.SegmentRequest;

/**
 * Keeps track of the received parts of a file as a set of disjoint, non-adjacent segments.
 * <p>
 * When a new range is added, only the parts which were not already covered are passed to a {@link PieceWriter}; this
 * allows the callers to write each byte of the file only once and to maintain incremental checksums.
 * <p>
 * Optionally, for each segment the CRC32C of its data is maintained (computed by combining the CRCs of the pieces
 * without accessing the data).
 * <p>
 * Not thread safe.
 */
public class SegmentTracker {

    public static final class Segment {
        long start;
        long end;
        int crc;

        Segment(long start, long end, int crc) {
            this.start = start;
            this.end = end;
            this.crc = crc;
        }

        public long start() {
            return start;
        }

        public long end() {
            return end;
        }

        public long length() {
            return end - start;
        }

        /**
         * CRC32C of the data in this segment (only maintained if the tracker has been created with trackCrc = true)
         */
        public int crc() {
            return crc;
        }

        @Override
        public String toString() {
            return "[" + start + ", " + end + ")";
        }
    }

    @FunctionalInterface
    public interface PieceWriter {
        /**
         * Called for each piece [start, end) of a newly added range which was not previously covered.
         *
         * @return the CRC32C of the data in the piece if the tracker tracks CRCs, otherwise anything.
         */
        int write(long start, long end);
    }

    private final TreeMap<Long, Segment> segments = new TreeMap<>();
    private final boolean trackCrc;
    private long coveredSize = 0;

    public SegmentTracker(boolean trackCrc) {
        this.trackCrc = trackCrc;
    }

    /**
     * Add the range [start, end). For each sub-range not already covered, the writer is called (in increasing offset
     * order) before the sub-range is marked as covered. If the writer throws an exception, the sub-ranges already
     * written stay covered and the exception is propagated.
     *
     * @return the number of newly covered bytes
     */
    public long add(long start, long end, PieceWriter writer) {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid range [" + start + ", " + end + ")");
        }
        long added = 0;
        long cur = start;
        Map.Entry<Long, Segment> fl = segments.floorEntry(cur);
        if (fl != null && fl.getValue().end > cur) {
            cur = fl.getValue().end;
        }
        while (cur < end) {
            Map.Entry<Long, Segment> next = segments.ceilingEntry(cur);
            long gapEnd = next == null ? end : Math.min(end, next.getKey());
            long nextEnd = next == null ? end : next.getValue().end;
            if (gapEnd > cur) {
                int crc = writer.write(cur, gapEnd);
                insert(cur, gapEnd, crc);
                added += gapEnd - cur;
            }
            cur = nextEnd;
        }
        return added;
    }

    /**
     * Adds a range which is known not to overlap with any existing segment (used when restoring).
     *
     * @return false if the range overlaps with an existing segment, in which case nothing is added.
     */
    public boolean addDisjoint(long start, long end, int crc) {
        if (start < 0 || end <= start || overlaps(start, end)) {
            return false;
        }
        insert(start, end, crc);
        return true;
    }

    /**
     * @return true if [start, end) overlaps with any existing segment
     */
    public boolean overlaps(long start, long end) {
        Map.Entry<Long, Segment> fl = segments.floorEntry(start);
        if (fl != null && fl.getValue().end > start) {
            return true;
        }
        Map.Entry<Long, Segment> next = segments.higherEntry(start);
        return next != null && next.getKey() < end;
    }

    private void insert(long s, long e, int crc) {
        Segment seg;
        Map.Entry<Long, Segment> le = segments.lowerEntry(s);
        if (le != null && le.getValue().end == s) {
            seg = le.getValue();
            if (trackCrc) {
                seg.crc = Crc32cUtils.combine(seg.crc, crc, e - s);
            }
            seg.end = e;
        } else {
            seg = new Segment(s, e, crc);
            segments.put(s, seg);
        }
        Segment right = segments.get(e);
        if (right != null) {
            segments.remove(e);
            if (trackCrc) {
                seg.crc = Crc32cUtils.combine(seg.crc, right.crc, right.length());
            }
            seg.end = right.end;
        }
        coveredSize += e - s;
    }

    /**
     * Returns the gaps between segments.
     *
     * @param size
     *            size of the file; if includeEnd is true, the last gap up to the size is also returned.
     */
    public List<SegmentRequest> getMissing(long size, boolean includeEnd) {
        List<SegmentRequest> toReturn = new ArrayList<>();
        long startOffset = 0;
        for (Segment segment : segments.values()) {
            if (segment.start != startOffset) {
                toReturn.add(new SegmentRequest(startOffset, segment.start));
            }
            startOffset = segment.end;
        }
        if (includeEnd && startOffset < size) {
            toReturn.add(new SegmentRequest(startOffset, size));
        }
        return toReturn;
    }

    /**
     * @return true if the file of the given size is entirely covered
     */
    public boolean isComplete(long size) {
        if (size < 0) {
            return false;
        }
        if (segments.isEmpty()) {
            return size == 0;
        }
        if (segments.size() != 1) {
            return false;
        }
        Segment seg0 = segments.firstEntry().getValue();
        return seg0.start == 0 && seg0.end == size;
    }

    /**
     * @return end of the last segment or -1 if there is no segment
     */
    public long endOffset() {
        return segments.isEmpty() ? -1 : segments.lastEntry().getValue().end;
    }

    /**
     * @return the total number of bytes covered
     */
    public long coveredSize() {
        return coveredSize;
    }

    public int count() {
        return segments.size();
    }

    public Collection<Segment> segments() {
        return segments.values();
    }

    @Override
    public String toString() {
        return segments.values().toString();
    }
}
