package org.yamcs.yarch;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.utils.StringConverter;

/**
 * Note that in the yamcs 5.12.0 and older, the encoding was broken for negative timestamps and record count was also
 * stored on 16 bits.
 * <p>
 * Yamcs 5.12.1 introduced a new encoding scheme, see {@link HistogramEncoder}
 * 
 */
public class HistogramSegment {
    byte[] columnv;
    long segIndex; // segment index
    final List<HistogramSegment.SegRecord> records;
    static final int MAX_INTERVAL = 120000; // make two records if the time between packets is more than 2 minutes
                                            // (because the packets are not very related)
    private static long LOSS_TIME = 1000; // time in milliseconds above which we consider a packet loss

    /**
     * Constructs an empty segment
     * 
     * @param columnv
     *            column value in binary
     * @param segIndex
     *            segment index
     */
    public HistogramSegment(byte[] columnv, long segIndex) {
        this.columnv = columnv;
        this.segIndex = segIndex;
        records = new ArrayList<>();
    }

    /**
     * Constructs a segment with pre-decoded records
     * 
     * @param columnv
     *            column value in binary
     * @param segIndex
     *            segment index
     * @param records
     *            list of segment records
     */
    public HistogramSegment(byte[] columnv, long segIndex, List<SegRecord> records) {
        this.columnv = columnv;
        this.segIndex = segIndex;
        this.records = records;
    }

    /**
     * Inverts the sign of a long value to handle negative timestamp sorting.
     */
    public static long invertSignI64(long value) {
        return value ^ Long.MIN_VALUE;
    }

    public byte[] columnv() {
        return columnv;
    }

    // used for merging
    private boolean mergeLeft, mergeRight;

    HistogramSegment.SegRecord left, right;
    int dtime;

    /**
     * @param dtime1
     *            delta time from segment start in milliseconds
     */
    public void merge(int dtime1) {
        mergeLeft = mergeRight = false;
        int leftIndex = -1;
        int rightIndex = -1;

        this.dtime = dtime1;
        for (int i = 0; i < records.size(); i++) {
            HistogramSegment.SegRecord r = records.get(i);
            if (dtime >= r.dstart) {
                if (dtime <= r.dstop) { // inside left
                    r.num++;
                    return;
                }
                left = r;
                leftIndex = i;
                continue;
            }
            if (dtime < r.dstart) {
                rightIndex = i;
                right = r;
                break;
            }
        }

        if (leftIndex != -1) {
            checkMergeLeft();
        }

        if (rightIndex != -1) {
            checkMergeRight();
        }

        if (mergeLeft && mergeRight) {
            selectBestMerge();
        }
        // based on the information collected above, compute the new records
        if (mergeLeft && mergeRight) {
            records.set(leftIndex, new SegRecord(left.dstart, right.dstop, left.num + right.num + 1));
            records.remove(rightIndex);
        } else if (mergeLeft) {
            left.dstop = dtime;
            left.num++;
        } else if (mergeRight) {
            right.dstart = dtime;
            right.num++;
        } else { // add a new record
            HistogramSegment.SegRecord center = new SegRecord(dtime, dtime, 1);
            if (leftIndex != -1) {
                records.add(leftIndex + 1, center);
            } else if (rightIndex != -1) {
                records.add(rightIndex, center);
            } else {
                records.add(center);
            }
        }
    }

    int leftInterval = -1;
    int rightInterval = -1;

    private void checkMergeLeft() { // check if it can be merged to left
        if ((dtime - left.dstop) < MAX_INTERVAL) {
            if (left.num == 1) {
                mergeLeft = true;
            } else {
                leftInterval = (left.dstop - left.dstart) / (left.num - 1);
                if ((dtime - left.dstop) < leftInterval + LOSS_TIME) {
                    mergeLeft = true;
                }
            }
        }
    }

    private void checkMergeRight() { // check if it can be merged to right
        if ((right.dstart - dtime) < MAX_INTERVAL) {
            if (right.num == 1) {
                mergeRight = true;
            } else {
                rightInterval = (right.dstop - right.dstart) / (right.num - 1);
                if ((right.dstart - dtime) < rightInterval + LOSS_TIME) {
                    mergeRight = true;
                }
            }
        }
    }

    private void selectBestMerge() {
        int intervalToLeft = dtime - left.dstop;
        int intervalToRight = right.dstart - dtime;
        if (Math.abs(intervalToLeft - intervalToRight) >= LOSS_TIME) {
            if (intervalToLeft < intervalToRight) {
                mergeRight = false;
            } else {
                mergeLeft = false;
            }
        }
    }

    // add a new record to the segment (to be used for testing only
    void add(int dstart, int dstop, int num) {
        records.add(new SegRecord(dstart, dstop, num));

    }

    public int size() {
        return records.size();
    }

    public long segIndex() {
        return segIndex;
    }

    public void addRecord(int dstart, int dstop, int num) {
        records.add(new SegRecord(dstart, dstop, num));
    }

    public List<SegRecord> getRecords() {
        return records;
    }

    @Override
    public String toString() {
        return "segIndex: " + segIndex + ", columnv: " + StringConverter.arrayToHexString(columnv) + " recs:" + records;
    }

    public static class SegRecord {
        int dstart; // deltas from the segment start in milliseconds
        int dstop;
        int num;

        public SegRecord(int dstart, int dstop, int num) {
            this.dstart = dstart;
            this.dstop = dstop;
            this.num = num;
        }

        public int dstart() {
            return dstart;
        }

        public int dstop() {
            return dstop;
        }

        public int num() {
            return num;
        }

        @Override
        public String toString() {
            return String.format("time:(%d,%d), nump: %d", dstart, dstop, num);
        }
    }

}
