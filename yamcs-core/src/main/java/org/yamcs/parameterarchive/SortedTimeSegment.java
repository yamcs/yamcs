package org.yamcs.parameterarchive;

import java.nio.ByteBuffer;
import java.util.Arrays;

import me.lemire.integercompression.FastPFOR128;
import me.lemire.integercompression.IntWrapper;

import org.yamcs.utils.DecodingException;
import org.yamcs.utils.SortedIntArray;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.VarIntUtil;

import static org.yamcs.parameterarchive.ParameterArchive.*;

/**
 * TimeSegment stores timestamps relative to a segmentStart. The timestamps are stored in a sorted int array.
 * <p>
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
        if (getIntervalStart(instant) != getIntervalStart(segmentStart)) {
            throw new IllegalArgumentException("This timestamp does not fit into this interval;"
                    + " intervalStart: " + TimeEncoding.toString(getIntervalStart(segmentStart))
                    + ", instant: " + TimeEncoding.toString(instant));
        }

        if (instant < segmentStart) {
            tsarray.addToAll((int) (segmentStart - instant));
            segmentStart = instant;
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
     * <p>
     * Note that this works even if the value would not fit in the same interval, which would cause a subsequent add
     * operation to fail.
     * 
     * @see java.util.Arrays#binarySearch(int[], int)
     * @param instant
     * @return
     */
    public int search(long instant) {
        if (getInterval(segmentStart) != getInterval(instant)) {
            if (instant < segmentStart) {
                return -1;
            } else {
                return -tsarray.size() - 1;
            }
        }

        return tsarray.search((int) (instant - segmentStart));
    }

    public int size() {
        return tsarray.size();
    }

    public long getSegmentStart() {
        return segmentStart;
    }

    @Override
    public void writeTo(ByteBuffer bb) {
        writeTo(tsarray, bb);
    }

    /**
     * Encode the time array
     */
    public static void writeTo(SortedIntArray tsarray, ByteBuffer bb) {
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
     */
    static SortedIntArray parse(ByteBuffer bb) throws DecodingException {
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

        return new SortedIntArray(VarIntUtil.decodeDeltaDeltaZigZag(ddz));
    }

    public static SortedTimeSegment parseFrom(ByteBuffer bb, long segmentStart) throws DecodingException {
        SortedTimeSegment r = new SortedTimeSegment(segmentStart);
        r.tsarray = parse(bb);
        return r;
    }

    @Override
    public int getMaxSerializedSize() {
        return 4 * (tsarray.size()) + 3;
    }

    public long getSegmentEnd() {
        int size = tsarray.size();
        if (size == 0) {
            return segmentStart;
        } else {
            return getTime(size - 1);
        }
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
     * Get the range between posStart and posStop skipping the positions that are in the gaps array
     */
    public long[] getRangeWithGaps(int posStart, int posStop, boolean ascending, SortedIntArray gaps) {
        long[] r = new long[posStop - posStart];
        int j = 0;
        if (ascending) {
            int k = 0;
            for (int i = posStart; i < posStop; i++) {
                while (k < gaps.size() && gaps.get(k) < i) {
                    k++;
                }
                if (k >= gaps.size() || gaps.get(k) != i) {
                    r[j++] = tsarray.get(i) + segmentStart;
                }
            }
        } else {
            int k = gaps.size() - 1;
            for (int i = posStop; i > posStart; i--) {
                while (k >= 0 && gaps.get(k) > i) {
                    k--;
                }
                if (k < 0 || gaps.get(k) != i) {
                    r[j++] = tsarray.get(i) + segmentStart;
                }
            }
        }
        return Arrays.copyOf(r, j);
    }

    public String toString() {
        return "[TimeSegment: id:" + segmentStart + ", relative times: " + tsarray.toString() + "]";
    }

}
