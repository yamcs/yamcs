package org.yamcs.parameterarchive;

import java.nio.ByteBuffer;

import me.lemire.integercompression.FastPFOR128;
import me.lemire.integercompression.IntWrapper;

import org.yamcs.utils.DecodingException;
import org.yamcs.utils.SortedIntArray;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.VarIntUtil;

import static org.yamcs.parameterarchive.ParameterArchive.INTERVAL_MASK;

/**
 * TimeSegment stores timestamps relative to a segmentStart. The timestamps are stored in a sorted int array.
 * 
 * The timestamps have to be larger than the segmentStart and have to be part of the same interval (see
 * {@link ParameterArchive#INTERVAL_MASK}.
 * 
 * @author nm
 *
 */
public class SortedTimeSegment extends BaseSegment {

    static final byte SUBFORMAT_ID_DELTAZG_FPF128_VB = 1; // compressed with DeltaZigzag and then FastPFOR128 plus
                                                          // VarInt32 for remaining
    static final byte SUBFORMAT_ID_DELTAZG_VB = 2; // compressed with DeltaZigzag plus VarInt32

    public static final int VERSION = 0;
    private long segmentStart;
    private SortedIntArray tsarray;

    public SortedTimeSegment(long segmentStart) {
        super(FORMAT_ID_SortedTimeValueSegment);

        tsarray = new SortedIntArray();
        this.segmentStart = segmentStart;
    }

    /**
     * Insert instant into the array and return the position at which it has been inserted.
     * 
     * @param instant
     */
    public int add(long instant) {
        if ((instant & INTERVAL_MASK) != (segmentStart & INTERVAL_MASK)) {
            throw new IllegalArgumentException("This timestamp does not fit into this interval;"
                    + " intervalStart: " + TimeEncoding.toString(ParameterArchive.getIntervalStart(segmentStart))
                    + ", instant: " + TimeEncoding.toString(instant));
        }
        if (instant < segmentStart) {
            throw new IllegalArgumentException("The timestamp has to be bigger than the segmentStart");
        }

        return tsarray.insert((int) (instant - segmentStart));
    }

    /**
     * get timestamp at position idx
     * 
     * @param idx
     * @return
     */
    public long getTime(int idx) {
        return segmentStart + tsarray.get(idx);
    }

    /**
     * performs a binary search in the time segment and returns the position of t or where t would fit in.
     * 
     * @see java.util.Arrays#binarySearch(int[], int)
     * @param instant
     * @return
     */
    public int search(long instant) {
        if ((instant & INTERVAL_MASK) != (segmentStart & INTERVAL_MASK)) {
            throw new IllegalArgumentException("This timestamp does not fit into this segment");
        }

        return tsarray.search((int) (instant - segmentStart));
    }

    public int size() {
        return tsarray.size();
    }

    public long getSegmentStart() {
        return segmentStart;
    }

    public String toString() {
        return "[TimeSegment: id:" + segmentStart + ", relative times: " + tsarray.toString() + "]";
    }

    /**
     * Encode the time array
     */
    @Override
    public void writeTo(ByteBuffer bb) {
        if (tsarray.size() == 0) {
            throw new IllegalStateException(" the time segment has no data");
        }
        int[] ddz = VarIntUtil.encodeDeltaDeltaZigZag(tsarray);
        int position = bb.position();
        bb.put(SUBFORMAT_ID_DELTAZG_FPF128_VB);

        int size = ddz.length;

        VarIntUtil.writeVarInt32(bb, size);

        FastPFOR128 fastpfor = FastPFORFactory.get();

        IntWrapper inputoffset = new IntWrapper(0);
        IntWrapper outputoffset = new IntWrapper(0);
        int[] out = new int[size];
        fastpfor.compress(ddz, inputoffset, size, out, outputoffset);
        if (outputoffset.get() == 0) {
            // fastpfor didn't compress anything, probably there were too few datapoints
            bb.put(position, SUBFORMAT_ID_DELTAZG_VB);
        } else {
            // write the fastpfor output
            for (int i = 0; i < outputoffset.get(); i++) {
                bb.putInt(out[i]);
            }
        }
        // write the remaining bytes varint compressed
        for (int i = inputoffset.get(); i < size; i++) {
            VarIntUtil.writeVarInt32(bb, ddz[i]);
        }
    }

    /**
     * Creates a TimeSegment by decoding the buffer
     * this is the reverse of the {@link #encode()} operation
     * 
     * @param buf
     * @return
     * @throws DecodingException
     */
    private void parse(ByteBuffer bb) throws DecodingException {
        byte subFormatId = bb.get();
        int n = VarIntUtil.readVarInt32(bb);
        int position = bb.position();

        IntWrapper inputoffset = new IntWrapper(0);
        IntWrapper outputoffset = new IntWrapper(0);
        int[] ddz = new int[n];

        if (subFormatId == SUBFORMAT_ID_DELTAZG_FPF128_VB) {
            int[] x = new int[(bb.limit() - bb.position()) / 4];
            for (int i = 0; i < x.length; i++) {
                x[i] = bb.getInt();
            }

            FastPFOR128 fastpfor = FastPFORFactory.get();
            fastpfor.uncompress(x, inputoffset, x.length, ddz, outputoffset);
            bb.position(position + inputoffset.get() * 4);
        }

        for (int i = outputoffset.get(); i < n; i++) {
            ddz[i] = VarIntUtil.readVarInt32(bb);
        }

        tsarray = new SortedIntArray(VarIntUtil.decodeDeltaDeltaZigZag(ddz));
    }

    public static SortedTimeSegment parseFrom(ByteBuffer bb, long segmentStart) throws DecodingException {
        SortedTimeSegment r = new SortedTimeSegment(segmentStart);
        r.parse(bb);
        return r;
    }

    @Override
    public int getMaxSerializedSize() {
        return 4 * (tsarray.size()) + 3;
    }

    public long getSegmentEnd() {
        return getTime(tsarray.size() - 1);
    }

    public long[] getRange(int posStart, int posStop, boolean ascending) {
        long[] r = new long[posStop - posStart];
        if (ascending) {
            for (int i = posStart; i < posStop; i++) {
                r[i - posStart] = tsarray.get(i) + segmentStart;
            }
        } else {
            for (int i = posStop; i > posStart; i--) {
                r[posStop - i] = tsarray.get(i) + segmentStart;
            }
        }
        return r;
    }

    /**
     * sets the segmentStart to the first timestamp of the segment (and adjusts all the other times accordingly)
     */
    void trimSegmentStart() {
        int diff = tsarray.get(0);
        if (diff != 0) {
            tsarray.add(-diff);
            segmentStart += diff;
        }
    }
}
