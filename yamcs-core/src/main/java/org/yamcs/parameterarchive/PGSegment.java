package org.yamcs.parameterarchive;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.parameter.BasicParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.TimeEncoding;

/**
 * Parameter Group segment - keeps references to Time and Value segments for a given parameter group and segment.
 * <p>
 * This class is used during the parameter archive buildup
 * 
 * @author nm
 *
 */
public class PGSegment {
    final int parameterGroupId;
    final IntArray parameterIds; // sorted array of parameter ids
    private SortedTimeSegment timeSegment;
    private List<ParameterValueSegment> pvSegments;

    private List<BaseSegment> consolidatedValueSegments;
    private List<BaseSegment> consolidatedRawValueSegments;
    private List<ParameterStatusSegment> consolidatedParameterStatusSegments;

    private final boolean storeRawValues = ParameterArchive.STORE_RAW_VALUES;

    public PGSegment(int parameterGroupId, long segmentStart, IntArray parameterIds) {
        this.parameterGroupId = parameterGroupId;
        this.parameterIds = parameterIds;
        timeSegment = new SortedTimeSegment(segmentStart);
    }

    private void init(List<BasicParameterValue> sortedPvList) {
        pvSegments = new ArrayList<>(parameterIds.size());

        for (int i = 0; i < parameterIds.size(); i++) {
            ParameterValueSegment pvs = new ParameterValueSegment(timeSegment);

            BasicParameterValue pv = sortedPvList.get(i);
            Value v = pv.getEngValue();
            if (v != null) {
                pvs.engValueSegment = getNewSegment(v.getType());
            }
            pvs.parameterStatusSegment = new ParameterStatusSegment(true);
            Value rawV = pv.getRawValue();
            if (storeRawValues) {
                if (rawV != null) {
                    pvs.rawValueSegment = getNewSegment(rawV.getType());
                }
            }

            pvSegments.add(pvs);
        }
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

    /**
     * Add a new record
     * instant goes into the timeSegment
     * the values goes each into a value segment
     * 
     * the sortedPvList list has to be already sorted according to the definition of the ParameterGroup
     * 
     * 
     * @param instant
     * @param sortedPvList
     */
    public void addRecord(long instant, List<BasicParameterValue> sortedPvList) {

        if (sortedPvList.size() != parameterIds.size()) {
            throw new IllegalArgumentException(
                    "Wrong number of values passed: " + sortedPvList.size() + ";expected " + parameterIds.size());
        }

        if (pvSegments == null) {
            init(sortedPvList);
        }

        int pos = timeSegment.add(instant);
        for (int i = 0; i < pvSegments.size(); i++) {
            ParameterValueSegment pvs = pvSegments.get(i);
            BasicParameterValue pv = sortedPvList.get(i);
            pvs.engValueSegment.add(pos, pv.getEngValue());
            Value rawValue = pv.getRawValue();
            if (storeRawValues && (rawValue != null)) {
                pvs.rawValueSegment.add(pos, rawValue);
            }
            pvs.parameterStatusSegment.addParameterValue(pos, pv);
        }
    }

    public void consolidate() {
        consolidatedValueSegments = new ArrayList<BaseSegment>(parameterIds.size());
        if (storeRawValues) {
            consolidatedRawValueSegments = new ArrayList<BaseSegment>(parameterIds.size());
        }
        consolidatedParameterStatusSegments = new ArrayList<>(parameterIds.size());

        for (ParameterValueSegment pvs : pvSegments) {
            BaseSegment bs = pvs.engValueSegment.consolidate();
            consolidatedValueSegments.add(bs);

            if (storeRawValues) {
                // the raw values will only be stored if they are different than the engineering values
                ValueSegment evs = pvs.engValueSegment;
                ValueSegment rvs = pvs.rawValueSegment;
                if ((rvs == null) || rvs.equals(evs)) {
                    consolidatedRawValueSegments.add(null);
                } else {
                    consolidatedRawValueSegments.add(rvs.consolidate());
                }
            }
            consolidatedParameterStatusSegments.add(pvs.parameterStatusSegment.consolidate());
        }
    }

    public ParameterValueSegment getParameterValue(int pid) {
        int idx = parameterIds.indexOf(pid);
        if (idx < 0) {
            return null;
        }
        return pvSegments.get(idx);
    }

    public MultiParameterValueSegment getParametersValues(ParameterId[] pids) {

        List<ValueSegment> engValueSegments = new ArrayList<>(pids.length);
        List<ValueSegment> rawValueSegments = new ArrayList<>(pids.length);
        List<ParameterStatusSegment> parameterStatusSegments = new ArrayList<>(pids.length);
        for (ParameterId pid : pids) {
            int idx = parameterIds.indexOf(pid.getPid());
            if (idx >= 0) {
                var pvs = pvSegments.get(idx);
                engValueSegments.add(pvs.engValueSegment);
                rawValueSegments.add(pvs.rawValueSegment);
                parameterStatusSegments.add(pvs.parameterStatusSegment);
            }
        }

        return new MultiParameterValueSegment(timeSegment,
                engValueSegments.toArray(new ValueSegment[0]),
                rawValueSegments.toArray(new ValueSegment[0]),
                parameterStatusSegments.toArray(new ParameterStatusSegment[0]));
    }

    public long getInterval() {
        return ParameterArchive.getInterval(timeSegment.getSegmentStart());
    }

    public long getSegmentStart() {
        return timeSegment.getSegmentStart();
    }

    /**
     * 
     * @return timestamp of the last parameter in this segment
     */
    public long getSegmentEnd() {
        return timeSegment.getSegmentEnd();
    }

    public SortedTimeSegment getTimeSegment() {
        return timeSegment;
    }

    public int getParameterGroupId() {
        return parameterGroupId;
    }

    public int getParameterId(int index) {
        return parameterIds.get(index);
    }

    public List<BaseSegment> getConsolidatedValueSegments() {
        return consolidatedValueSegments;
    }

    public List<BaseSegment> getConsolidatedRawValueSegments() {
        return consolidatedRawValueSegments;
    }

    public List<ParameterStatusSegment> getConsolidatedParameterStatusSegments() {
        return consolidatedParameterStatusSegments;
    }

    public int size() {
        return timeSegment.size();
    }

    public String toString() {
        return "groupId: " + parameterGroupId + ", [" + TimeEncoding.toString(getSegmentStart()) + ", "
                + TimeEncoding.toString(getSegmentEnd()) + "], size: " + size();
    }
}
