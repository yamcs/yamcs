package org.yamcs.parameterarchive;

import org.yamcs.parameter.BasicParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.parameter.ValueArray;
import org.yamcs.protobuf.Pvalue.ParameterStatus;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.PeekingIterator;
import org.yamcs.utils.SortedIntArray;
import org.yamcs.utils.TimeEncoding;

import static org.yamcs.parameterarchive.ParameterArchive.STORE_RAW_VALUES;

import java.util.NoSuchElementException;

/**
 * Stores parameter values for one parameter over a time range.
 * <p>
 * It is composed of a time, engineering, raw and parameter status segments and possibly by a list of gaps;
 * <p>
 * The engineering, raw and parameter status contain only the data - gaps are not stored in the segment themselves (the
 * timeSegment has by definition no gap).
 * <p>
 * To convert from a position as used in the time segment to a position in the other segments, the gaps have to be taken
 * into account. Such conversion can naturally result in no value if the requested position is part of the gaps.
 * <p>
 * The time segment is shared with other objects of this class and is updated outside this class
 */
public class ParameterValueSegment {
    final int pid;
    final SortedTimeSegment timeSegment;

    // engValueSegment should not be null during buildup but maybe null during retrieval (if the retrieving of
    // engineering values is skipped)
    ValueSegment engValueSegment;
    private ValueSegment rawValueSegment;
    private ParameterStatusSegment parameterStatusSegment;

    // stores the indices (positions) of the gaps
    SortedIntArray gaps;

    public ParameterValueSegment(int pid, SortedTimeSegment timeSegment, ValueSegment engValueSegment,
            ValueSegment rawValueSegment, ParameterStatusSegment parameterStatusSegment, SortedIntArray gaps) {
        this.pid = pid;
        this.timeSegment = timeSegment;
        this.engValueSegment = engValueSegment;
        this.rawValueSegment = rawValueSegment;
        this.parameterStatusSegment = parameterStatusSegment;
        this.gaps = gaps;

    }

    /**
     * 
     * Creates a new segment and insert one value. The value is used to determine the individual engineering/raw segment
     * types. All future values are expected to be the same type.
     * <p>
     * The length of the segment (number of parameters) is given by the timeSegment length.
     * <p>
     * If the length is greater than 1, then all other positions will be initialised with gaps.
     */
    public ParameterValueSegment(int pid, SortedTimeSegment timeSegment, int pos, BasicParameterValue pv) {
        this.pid = pid;
        this.timeSegment = timeSegment;

        Value v = pv.getEngValue();
        if (v != null) {
            engValueSegment = getNewSegment(v.getType());
        } else {
            engValueSegment = null;
        }
        parameterStatusSegment = new ParameterStatusSegment(true);

        if (STORE_RAW_VALUES) {
            Value rawV = pv.getRawValue();

            if (rawV != null) {
                rawValueSegment = getNewSegment(rawV.getType());
            } else {
                rawValueSegment = null;
            }
        } else {
            rawValueSegment = null;
        }

        for (int i = 0; i < pos; i++) {
            insertGap(i);
        }
        insert(pos, pv);
        for (int i = pos + 1; i < timeSegment.size(); i++) {
            insertGap(i);
        }
    }

    public void insertGap(int pos) {
        if (gaps == null) {
            gaps = new SortedIntArray();
        } else {
            // the position of all the indices following the pos have to increase by 1
            gaps.addIfGreaterOrEqualThan(pos, 1);
        }
        gaps.insert(pos);
    }

    public void insert(int pos, BasicParameterValue pv) {
        if (pos == timeSegment.size()) {
            // fast path inserting data at the end, no need to care about gaps
            if (engValueSegment != null) {
                engValueSegment.add(pv.getEngValue());
            }
            parameterStatusSegment.addParameterValue(pv);
            if (rawValueSegment != null) {
                rawValueSegment.add(pv.getRawValue());
            }
        } else {
            if (gaps == null) {
                if (engValueSegment != null) {
                    engValueSegment.insert(pos, pv.getEngValue());
                }
                parameterStatusSegment.insertParameterValue(pos, pv);
                if (rawValueSegment != null) {
                    rawValueSegment.insert(pos, pv.getRawValue());
                }
            } else {
                int pos1;

                var idx = gaps.search(pos);

                if (idx < 0) {
                    pos1 = pos + idx + 1;
                } else {
                    pos1 = pos - idx;
                }
                if (engValueSegment != null) {
                    engValueSegment.insert(pos1, pv.getEngValue());
                }
                parameterStatusSegment.insertParameterValue(pos1, pv);
                if (rawValueSegment != null) {
                    rawValueSegment.insert(pos1, pv.getRawValue());
                }
                gaps.addIfGreaterOrEqualThan(pos, 1);
            }
        }
    }

    // returns the modified position after gaps are eliminated or -1 if the position corresponds to a gap
    private int gaplessPosition(int pos) {
        if (gaps == null) {
            return pos;
        } else {
            var idx = gaps.search(pos);
            if (idx < 0) {
                return pos + idx + 1;
            } else {
                return -1;
            }
        }
    }

    /**
     * returns the modified position after gaps are eliminated
     * <p>
     * if the position corresponds to a gap, return the next position or the segment size if the gap is at the end
     * <p>
     * it assumes gaps is non null
     */
    int nextAfterGap(int pos) {
        var idx = gaps.search(pos);
        if (idx < 0) {
            return pos + idx + 1;
        } else {
            return pos - idx;
        }
    }

    /**
     * returns the modified position after gaps are eliminated
     * <p>
     * if the position corresponds to a gap, return the previous position or -1 if the gap is at the beginning
     * <p>
     * it assumes gaps is non null
     */
    int previousBeforeGap(int pos) {
        var idx = gaps.search(pos);
        if (idx < 0) {
            return pos + idx + 1;
        } else {
            int x = pos - idx - 1;
            return x < 0 ? -1 : x;
        }
    }

    /**
     * <p>
     * Optimise for writing to archive
     */
    public void consolidate() {
        parameterStatusSegment.consolidate();
        if (engValueSegment != null) {
            engValueSegment.consolidate();
        }

        if (STORE_RAW_VALUES) {
            if (rawValueSegment != null) {
                // the raw values will only be stored if they are different than the engineering values
                if (rawValueSegment.equals(engValueSegment)) {
                    rawValueSegment = null;
                } else {
                    rawValueSegment.consolidate();
                }
            }
        }
    }

    public ParameterValueArray getRange(int posStart, int posStop, boolean ascending, boolean retrieveParameterStatus) {
        long[] timestamps;
        if (gaps == null) {
            timestamps = timeSegment.getRange(posStart, posStop, ascending);
        } else {
            timestamps = timeSegment.getRangeWithGaps(posStart, posStop, ascending, gaps);
            if (ascending) {
                posStart = nextAfterGap(posStart);
                posStop = nextAfterGap(posStop);
            } else {
                posStart = previousBeforeGap(posStart);
                posStop = previousBeforeGap(posStop);
            }
        }

        if (posStart >= posStop) {// only gaps
            return null;
        }
        ValueArray engValues = null;
        if (engValueSegment != null) {
            engValues = engValueSegment.getRange(posStart, posStop, ascending);
        }

        ValueArray rawValues = null;
        if (rawValueSegment == engValueSegment) {
            rawValues = engValues;
        } else if (rawValueSegment != null) {
            rawValues = rawValueSegment.getRange(posStart, posStop, ascending);
        }

        ParameterStatus[] paramStatus = null;
        if (retrieveParameterStatus) {
            paramStatus = parameterStatusSegment.getRangeArray(posStart, posStop, ascending);
        }
        return new ParameterValueArray(timestamps, engValues, rawValues, paramStatus);
    }

    public long getSegmentStart() {
        return timeSegment.getSegmentStart();
    }

    public long getSegmentEnd() {
        return timeSegment.getSegmentEnd();
    }

    public int numGaps() {
        return gaps == null ? 0 : gaps.size();
    }

    public int numValues() {
        int numGaps = gaps == null ? 0 : gaps.size();
        return timeSegment.size() - numGaps;
    }

    public BaseSegment getConsolidatedEngValueSegment() {
        return (BaseSegment) engValueSegment;
    }

    public BaseSegment getConsolidatedRawValueSegment() {
        return (BaseSegment) rawValueSegment;
    }

    public BaseSegment getConsolidatedParmeterStatusSegment() {
        return (BaseSegment) parameterStatusSegment;
    }

    static private ValueSegment getNewSegment(Type type) {
        switch (type) {
        case BINARY:
            return new BinaryValueSegment(true);
        case STRING:
        case ENUMERATED:
            return new StringValueSegment(true);
        case SINT32:
            return new IntValueSegment(true);
        case UINT32:
            return new IntValueSegment(false);
        case FLOAT:
            return new FloatValueSegment();
        case SINT64:
        case UINT64:
        case TIMESTAMP: // intentional fall through
            return new LongValueSegment(type);
        case DOUBLE:
            return new DoubleValueSegment();
        case BOOLEAN:
            return new BooleanValueSegment();

        default:
            throw new IllegalStateException("Unknown type " + type);
        }
    }

    public TimedValue getTimedValue(int pos) {
        pos = gaplessPosition(pos);
        if (pos < 0) {
            return null;
        }
        long t = timeSegment.getTime(pos);

        Value ev = (engValueSegment == null) ? null : engValueSegment.getValue(pos);
        Value rv = (rawValueSegment == null) ? null : rawValueSegment.getValue(pos);
        ParameterStatus ps = (parameterStatusSegment == null) ? null : parameterStatusSegment.get(pos);

        return new TimedValue(t, ev, rv, ps);
    }

    public Value getEngValue(int pos) {
        pos = gaplessPosition(pos);
        if (pos < 0) {
            return null;
        }
        return engValueSegment.getValue(pos);
    }

    public Value getRawValue(int pos) {
        pos = gaplessPosition(pos);
        if (pos < 0) {
            return null;
        }
        return rawValueSegment.getValue(pos);
    }

    public SortedIntArray getGaps() {
        return gaps;
    }

    public PeekingIterator<TimedValue> newAscendingIterator(long t0) {
        return new AscendingIterator(t0);
    }

    public PeekingIterator<TimedValue> newDescendingIterator(long t0) {
        return new DescendingIterator(t0);
    }

    /**
     * In rare circumstances, a segment read from the archive has to be modified.
     * <p>
     * This method updates the object such that it can be modified
     */
    public void makeWritable() {
        parameterStatusSegment.makeWritable();
        engValueSegment.makeWritable();
        if (rawValueSegment != null) {
            rawValueSegment.makeWritable();
        }
    }

    @Override
    public String toString() {
        return "ParameterValueSegment[size: " + timeSegment.size() + ", start: "
                + TimeEncoding.toString(getSegmentStart())
                + ", end: " + TimeEncoding.toString(getSegmentEnd()) + "]";
    }

    class AscendingIterator implements PeekingIterator<TimedValue> {
        private int idxT;
        private int idxV;
        private int idxG;
        private TimedValue currentValue = null;

        public AscendingIterator(long t0) {
            idxT = timeSegment.lowerBound(t0);

            if (gaps == null) {
                idxV = idxT;
            } else {
                idxG = gaps.search(idxT);
                if (idxG < 0) {
                    idxG = -(idxG + 1);
                }
                idxV = idxT - idxG;

            }
            next();
        }

        public boolean isValid() {
            return currentValue != null;
        }

        public TimedValue value() {
            if (!isValid()) {
                throw new NoSuchElementException();
            }
            return currentValue;
        }

        public void next() {
            while (gaps != null && idxG < gaps.size() && idxT == gaps.get(idxG)) {
                idxT++;
                idxG++;
            }

            if (idxV < numValues()) {
                Value ev = (engValueSegment == null) ? null : engValueSegment.getValue(idxV);
                Value rv = (rawValueSegment == null) ? null : rawValueSegment.getValue(idxV);
                ParameterStatus ps = (parameterStatusSegment == null) ? null : parameterStatusSegment.get(idxV);

                currentValue = new TimedValue(timeSegment.getTime(idxT), ev, rv, ps);
                idxT++;
                idxV++;
            } else {
                currentValue = null;
            }
        }
    }

    class DescendingIterator implements PeekingIterator<TimedValue> {
        private int idxT;
        private int idxV;
        private int idxG;
        private TimedValue currentValue = null;

        public DescendingIterator(long t0) {
            idxT = timeSegment.higherBound(t0);

            if (gaps == null) {
                idxV = idxT;
            } else {
                idxG = gaps.search(idxT);
                if (idxG < 0) {
                    idxG = -(idxG + 2);
                }
                idxV = idxT - idxG - 1;
            }
            next();
        }

        public boolean isValid() {
            return currentValue != null;
        }

        public TimedValue value() {
            if (!isValid()) {
                throw new NoSuchElementException();
            }
            return currentValue;
        }

        public void next() {

            while (gaps != null && idxG >= 0 && idxT == gaps.get(idxG)) {
                idxT--;
                idxG--;
            }

            if (idxT >= 0 && idxV >= 0) {
                Value ev = (engValueSegment == null) ? null : engValueSegment.getValue(idxV);
                Value rv = (rawValueSegment == null) ? null : rawValueSegment.getValue(idxV);
                ParameterStatus ps = (parameterStatusSegment == null) ? null : parameterStatusSegment.get(idxV);

                currentValue = new TimedValue(timeSegment.getTime(idxT), ev, rv, ps);
                idxT--;
                idxV--;
            } else {
                currentValue = null;
            }
        }
    }
}
