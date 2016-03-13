package org.yamcs.parameterarchive;

import java.nio.ByteBuffer;
import java.util.PrimitiveIterator;

import me.lemire.integercompression.FastPFOR128;
import me.lemire.integercompression.IntWrapper;

import org.yamcs.protobuf.ValueHelper;
import org.yamcs.parameter.Value;
import org.yamcs.utils.DecodingException;
import org.yamcs.utils.SortedIntArray;
import org.yamcs.utils.ValueUtility;
import org.yamcs.utils.VarIntUtil;

/**
 * TimeSegment stores timestamps relative to a t0. 
 * The timestamps are stored in a sorted int array.
 * 
 * @author nm
 *
 */
public class SortedTimeSegment extends BaseSegment implements ValueSegment {
    public static final int NUMBITS_MASK = 22; //2^22 millisecons =~ 70 minutes per segment    
    public static final int TIMESTAMP_MASK = (0xFFFFFFFF>>>(32-NUMBITS_MASK));
    public static final long SEGMENT_MASK = ~TIMESTAMP_MASK;
    
    final static byte SUBFORMAT_ID_DELTAZG_FPF128_VB = 1; //compressed with DeltaZigzag and then FastPFOR128 plus VarInt32 for remaining
    final static byte SUBFORMAT_ID_DELTAZG_VB = 2; //compressed with DeltaZigzag plus VarInt32


    public static final int VERSION = 0;

    final private long segmentStart;    
    private SortedIntArray tsarray;


    public SortedTimeSegment(long segmentStart) {
        super(FORMAT_ID_SortedTimeValueSegment);
        if((segmentStart & TIMESTAMP_MASK) !=0) throw new IllegalArgumentException("t0 must be 0 in last "+NUMBITS_MASK+" bits");

        tsarray = new SortedIntArray();
        this.segmentStart = segmentStart;
    }

    /**
     * Insert instant into the array and return the position at which it has been inserted.
     * 
     * @param instant
     */
    public int add(long instant) {
        if((instant&SEGMENT_MASK) != segmentStart) {
            throw new IllegalArgumentException("This timestamp does not fit into this segment");
        }
        return tsarray.insert((int)(instant & TIMESTAMP_MASK));
    }

    /**
     * get timestamp at position idx
     * @param idx
     * @return
     */
    public long getTime(int idx) {
        return tsarray.get(idx) | segmentStart;
    }

    /**
     * Constructs an ascending iterator starting from a specified value (inclusive) 
     * 
     * @param startFrom
     * @return
     */
    public PrimitiveIterator.OfLong getAscendingIterator(long startFrom) {
        return new PrimitiveIterator.OfLong() {
            PrimitiveIterator.OfInt it = tsarray.getAscendingIterator((int)(startFrom&TIMESTAMP_MASK));

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public long nextLong() {
                return segmentStart+ it.nextInt();

            }
        };
    }

    /**
     * Constructs an descending iterator starting from a specified value (exclusive) 
     * 
     * @param startFrom
     * @return
     */
    public PrimitiveIterator.OfLong getDescendingIterator(long startFrom) {
        return new PrimitiveIterator.OfLong() {
            PrimitiveIterator.OfInt it = tsarray.getDescendingIterator((int)(startFrom&TIMESTAMP_MASK));

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public long nextLong() {
                return segmentStart + it.nextInt();

            }
        };
    }


    public long getT0() {
        return segmentStart;
    }

    /**
     * returns the start of the segment where instant fits
     * @param instant
     * @return
     */
    public static long getSegmentStart(long instant) {
        return instant & SEGMENT_MASK;
    }

    /**
     * returns the ID of the segment where the instant fits - this is the same with segment start
     * @param instant
     * @return
     */
    public static long getSegmentId(long instant) {
        return instant & SEGMENT_MASK;
    }
    /**
     * returns the end of the segment where the instant fits
     * @param instant
     * @return
     */
    public static long getSegmentEnd(long instant) {
        return instant  | TIMESTAMP_MASK;
    }

    public static long getNextSegmentStart(long instant) {
        return (instant  | TIMESTAMP_MASK) +1;
    }

    /**
     * returns true if the segment overlaps the [start,stop) interval
     * @param segmentId
     * @param start
     * @param stop
     * @return
     */
    public static boolean overlap(long segmentId, long start, long stop) {
        long segmentStart = segmentId;
        long segmentStop = getSegmentEnd(segmentId);

        return start<segmentStop && stop>segmentStart;

    }


    /**
     * performs a binary search in the time segment and returns the position of t or where t would fit in. 
     * 
     * @see java.util.Arrays#binarySearch(int[], int)
     * @param instant
     * @return
     */
    public int search(long instant) {
        if((instant&SEGMENT_MASK) != segmentStart) {
            throw new IllegalArgumentException("This timestamp does not fit into this segment");
        }
        return tsarray.search((int)(instant&TIMESTAMP_MASK));
    }

    public int size() {
        return tsarray.size();
    }

    public long getSegmentStart() {
        return segmentStart;
    }

    public String toString() {
        return "[TimeSegment: t0:"+segmentStart+", relative times: "+ tsarray.toString()+"]";
    }

    /**
     * Encode the time array
     */
    @Override
    public void writeTo(ByteBuffer bb) {
        if(tsarray.size()==0) throw new IllegalStateException(" the time segment has no data");
        int[] ddz = VarIntUtil.encodeDeltaDeltaZigZag(tsarray);
        int position = bb.position();
        bb.put(SUBFORMAT_ID_DELTAZG_FPF128_VB);
        
        int size = ddz.length;
        
        VarIntUtil.writeVarInt32(bb,size);
        
        
        FastPFOR128 fastpfor = FastPFORFactory.get();
        
        IntWrapper inputoffset = new IntWrapper(0);
        IntWrapper outputoffset = new IntWrapper(0);
        int[] out = new int[size];
        fastpfor.compress(ddz, inputoffset, size, out, outputoffset);
        if (outputoffset.get() == 0) { 
            //fastpfor didn't compress anything, probably there were too few datapoints
            bb.put(position, SUBFORMAT_ID_DELTAZG_VB);
        } else {
            //write the fastpfor output
            for(int i=0; i<outputoffset.get(); i++) {
                bb.putInt(out[i]);
            }
        }
        //write the remaining bytes varint compressed
        for(int i = inputoffset.get(); i<size; i++) {
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

        if(subFormatId==SUBFORMAT_ID_DELTAZG_FPF128_VB) {
            int[] x = new int[(bb.limit()-bb.position())/4];
            for(int i=0; i<x.length;i++) {
                x[i]=bb.getInt();
            }
            
            FastPFOR128 fastpfor = FastPFORFactory.get();
            fastpfor.uncompress(x, inputoffset, x.length, ddz, outputoffset);
            bb.position(position+inputoffset.get()*4);
        }
        
        for(int i = outputoffset.get(); i<n; i++) {
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
        return 4*(tsarray.size())+3;
    }

    @Override
    public Value getValue(int index) {        
        return ValueUtility.getTimestampValue(getTime(index));
    }

    public long getSegmentEnd() {
        return getSegmentEnd(segmentStart);
    }

    public long[] getRange(int posStart, int posStop, boolean ascending) {
        long[] r = new long[posStop-posStart];
        if(ascending) {
            for(int i = posStart; i<posStop; i++) {
                r[i-posStart] = tsarray.get(i)|segmentStart;
            }
        } else {
            for(int i = posStop; i>posStart; i--) {
                r[posStop-i] = tsarray.get(i)|segmentStart;
            }
        }
        return r;
    }

    @Override
    public void add(int pos, Value engValue) {
        throw new UnsupportedOperationException("add not supported");

    }

    @Override
    public BaseSegment consolidate() {
        throw new UnsupportedOperationException("consolidate not supported");
    }

    /**
     * duration in milliseconds of one segment
     * @return
     */
    public static long getSegmentDuration() {
        return TIMESTAMP_MASK+1;
    }
}
