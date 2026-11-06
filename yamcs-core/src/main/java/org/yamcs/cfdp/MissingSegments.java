package org.yamcs.cfdp;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.yamcs.cfdp.pdu.SegmentRequest;

/**
 * Keeps track of the file segments that have to be retransmitted by the sender.
 * <p>
 * The segments are kept sorted, non-overlapping and non-adjacent (adjacent or overlapping segments are merged).
 * <p>
 * The latest NAK received is considered to give the complete list of missing segments within its scope, so when a new
 * NAK is received, the segments previously requested within that scope are dropped (the ones crossing the scope
 * borders are truncated) and replaced by the segments requested in the NAK.
 */
class MissingSegments {
    // start -> end (exclusive)
    private final TreeMap<Long, Long> segments = new TreeMap<>();

    /**
     * Removes the [start, end) range, truncating the segments crossing its borders.
     */
    void remove(long start, long end) {
        if (start >= end) {
            return;
        }
        var lower = segments.lowerEntry(start);
        if (lower != null && lower.getValue() > start) {
            long lowerEnd = lower.getValue();
            segments.put(lower.getKey(), start);
            if (lowerEnd > end) {
                segments.put(end, lowerEnd);
                return;
            }
        }
        var inside = segments.subMap(start, true, end, false);
        if (!inside.isEmpty()) {
            long lastEnd = inside.lastEntry().getValue();
            inside.clear();
            if (lastEnd > end) {
                segments.put(end, lastEnd);
            }
        }
    }

    /**
     * Adds the [start, end) range, merging it with the existing overlapping or adjacent segments.
     */
    void add(long start, long end) {
        if (start >= end) {
            return;
        }
        var floor = segments.floorEntry(start);
        if (floor != null && floor.getValue() >= start) {
            start = floor.getKey();
            end = Math.max(end, floor.getValue());
        }
        var inside = segments.subMap(start, true, end, true);
        if (!inside.isEmpty()) {
            end = Math.max(end, inside.lastEntry().getValue());
            inside.clear();
        }
        segments.put(start, end);
    }

    /**
     * Removes and returns the first segment, limited to maxSize bytes.
     * <p>
     * If the first segment is larger than maxSize, the remainder stays in the list.
     *
     * @return the segment or null if there is no missing segment
     */
    SegmentRequest poll(int maxSize) {
        var first = segments.pollFirstEntry();
        if (first == null) {
            return null;
        }
        long start = first.getKey();
        long end = Math.min(first.getValue(), start + maxSize);
        if (end < first.getValue()) {
            segments.put(end, first.getValue());
        }
        return new SegmentRequest(start, end);
    }

    boolean isEmpty() {
        return segments.isEmpty();
    }

    List<SegmentRequest> toList() {
        List<SegmentRequest> l = new ArrayList<>(segments.size());
        segments.forEach((s, e) -> l.add(new SegmentRequest(s, e)));
        return l;
    }

    @Override
    public String toString() {
        return toList().toString();
    }
}
