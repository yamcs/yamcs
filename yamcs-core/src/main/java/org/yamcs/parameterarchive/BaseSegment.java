package org.yamcs.parameterarchive;

import java.nio.ByteBuffer;

import org.yamcs.utils.DecodingException;

/**
 * Base class for all segments of values, timestamps or ParameterStatus
 */
public abstract class BaseSegment {
    // in SortedTimeValueSegmentV1, timestamps are relative to the segment start
    @Deprecated
    public static final byte FORMAT_ID_SortedTimeValueSegmentV1 = 1;

    public static final byte FORMAT_ID_ParameterStatusSegment = 2;
    public static final byte FORMAT_ID_GenericValueSegment = 10;
    public static final byte FORMAT_ID_IntValueSegment = 11;
    public static final byte FORMAT_ID_StringValueSegment = 13;

    // public static final byte FORMAT_ID_OldBooleanValueSegment = 15;
    public static final byte FORMAT_ID_FloatValueSegment = 16;
    public static final byte FORMAT_ID_DoubleValueSegment = 17;
    public static final byte FORMAT_ID_LongValueSegment = 18;
    public static final byte FORMAT_ID_BinaryValueSegment = 19;
    public static final byte FORMAT_ID_BooleanValueSegment = 20;

    // in _SortedTimeValueSegmentV2 timestamps are relative to the interval start
    // this has the advantage that we can merge the segments without change (in RocksDB)
    public static final byte FORMAT_ID_SortedTimeValueSegmentV2 = 21;

    // starting with Yamcs 5.9.8/5.10.0 we store in the gap segment the starting index of the segment into the interval
    // in order to allow merging segments later.
    public static final byte FORMAT_ID_GapSegment = 22;

    protected byte formatId;

    BaseSegment(byte formatId) {
        this.formatId = formatId;
    }

    public abstract void writeTo(ByteBuffer buf);



    public void makeWritable() {
    }
    /**
     *
     * @return a high approximation for the serialized size in order to allocate a ByteBuffer big enough
     */
    public abstract int getMaxSerializedSize();

    public void consolidate() {
    };

    public byte getFormatId() {
        return formatId;
    }

    public static BaseSegment parseSegment(byte formatId, long segmentStart, ByteBuffer bb) throws DecodingException {
        switch (formatId) {
        case FORMAT_ID_ParameterStatusSegment:
            return ParameterStatusSegment.parseFrom(bb);
        case FORMAT_ID_SortedTimeValueSegmentV1:
            return SortedTimeSegment.parseFromV1(bb, segmentStart);
        case FORMAT_ID_IntValueSegment:
            return IntValueSegment.parseFrom(bb);
        case FORMAT_ID_StringValueSegment:
            return StringValueSegment.parseFrom(bb);
        case FORMAT_ID_BooleanValueSegment:
            return BooleanValueSegment.parseFrom(bb);
        case FORMAT_ID_FloatValueSegment:
            return FloatValueSegment.parseFrom(bb);
        case FORMAT_ID_DoubleValueSegment:
            return DoubleValueSegment.parseFrom(bb);
        case FORMAT_ID_LongValueSegment:
            return LongValueSegment.parseFrom(bb);
        case FORMAT_ID_BinaryValueSegment:
            return BinaryValueSegment.parseFrom(bb);
        case FORMAT_ID_SortedTimeValueSegmentV2:
            return SortedTimeSegment.parseFromV2(bb, segmentStart);
        default:
            throw new DecodingException("Invalid format id " + formatId);
        }
    }

    public abstract int size();
}
