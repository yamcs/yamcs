package org.yamcs.yarch;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.StringConverter;

/* 
 * keeps all the records in a {@value #GROUPING_FACTOR} millisec interval
 * 
 * */
public class HistogramSegment {
    byte[] columnv;
    long sstart; // segment start
    ArrayList<HistogramSegment.SegRecord> pps;
    public static final long GROUPING_FACTOR = 3600 * 1000;
    static final int REC_SIZE = 10; // 4 bytes for start and stop, 2 bytes for num
    static final int MAX_INTERVAL = 120000; // make two records if the time between packets is more than 2 minutes
                                            // (because the packets are not very related)
    private static long LOSS_TIME = 1000; // time in milliseconds above which we consider a packet loss

    /**
     * Constructs an empty segment
     * 
     * @param columnv
     *            - column value in binary
     * @param sstart
     */
    public HistogramSegment(byte[] columnv, long sstart) {
        this.columnv = columnv;
        this.sstart = sstart;
        pps = new ArrayList<>();
    }

    public HistogramSegment(byte[] columnv, long sstart, byte[] val) {
        ByteBuffer v = ByteBuffer.wrap(val);
        this.columnv = columnv;
        this.sstart = sstart;
        pps = new ArrayList<>();
        while (v.hasRemaining()) {
            pps.add(new SegRecord(v.getInt(), v.getInt(), v.getShort()));
        }
    }

    public HistogramSegment(byte[] key, byte[] val) {
        ByteBuffer k = ByteBuffer.wrap(key);
        ByteBuffer v = ByteBuffer.wrap(val);
        this.sstart = k.getLong(0);
        columnv = new byte[k.remaining()];
        k.get(columnv);
        pps = new ArrayList<>();
        while (v.hasRemaining()) {
            pps.add(new SegRecord(v.getInt(), v.getInt(), v.getShort()));
        }
    }

    public static long getSstart(byte[] key) {
        return ByteBuffer.wrap(key).getLong(0);
    }

    public static byte[] key(long sstart, byte[] columnv) {
        byte[] b = ByteArrayUtils.encodeLong(sstart, new byte[8 + columnv.length], 0);
        System.arraycopy(columnv, 0, b, 8, columnv.length);
        return b;
    }

    public byte[] val() {
        ByteBuffer bbv = ByteBuffer.allocate(REC_SIZE * pps.size());
        for (HistogramSegment.SegRecord p : pps) {
            bbv.putInt(p.dstart);
            bbv.putInt(p.dstop);
            bbv.putShort((short)p.num); //TODO fix overflow int->short (should convert all histograms to int)
        }
        return bbv.array();
    }

    public static long segmentStart(long instant) {
        return instant / HistogramSegment.GROUPING_FACTOR;
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
        for (int i = 0; i < pps.size(); i++) {
            HistogramSegment.SegRecord r = pps.get(i);
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
            pps.set(leftIndex, new SegRecord(left.dstart, right.dstop, left.num + right.num + 1));
            pps.remove(rightIndex);
        } else if (mergeLeft) {
            left.dstop = dtime;
            left.num++;
        } else if (mergeRight) {
            right.dstart = dtime;
            right.num++;
        } else { // add a new record
            HistogramSegment.SegRecord center = new SegRecord(dtime, dtime, 1);
            if (leftIndex != -1) {
                pps.add(leftIndex + 1, center);
            } else if (rightIndex != -1) {
                pps.add(rightIndex, center);
            } else {
                pps.add(center);
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
        pps.add(new SegRecord(dstart, dstop, num));

    }

    public int size() {
        return pps.size();
    }
    
    public long getSegmentStart() {
        return sstart;
    }
    
    @Override
    public String toString() {
        return "start: " + sstart + ", columnv: " + StringConverter.arrayToHexString(columnv) + " recs:" + pps;
    }

    static class SegRecord {
        int dstart, dstop; // deltas from the segment start in milliseconds
        int num;

        public SegRecord(int dstart, int dstop, int num) {
            this.dstart = dstart;
            this.dstop = dstop;
            this.num = num;
        }

        @Override
        public String toString() {
            return String.format("time:(%d,%d), nump: %d", dstart, dstop, num);
        }
    }

    
}
