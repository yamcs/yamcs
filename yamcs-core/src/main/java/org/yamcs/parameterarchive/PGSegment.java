package org.yamcs.parameterarchive;

import java.util.ArrayList;

import java.util.List;

import org.yamcs.parameter.BasicParameterValue;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.TimeEncoding;

/**
 * Parameter Group segment - keeps references to Time and Value segments for a given parameter group and segment.
 * <p>
 * This class is used during the parameter archive buildup
 * 
 */
public class PGSegment {
    final int parameterGroupId;
    private SortedTimeSegment timeSegment;
    List<ParameterValueSegment> pvSegments;
    // for the first segment in the interval this is zero
    // for the subsequent segments it is the number of rows from the previous segments
    int segmentIdxInsideInterval;

    public PGSegment(int parameterGroupId, long interval) {
        this(parameterGroupId, interval, 1000);
    }

    public PGSegment(int parameterGroupId, long interval, int capacity) {
        this.parameterGroupId = parameterGroupId;
        this.timeSegment = new SortedTimeSegment(interval);
        this.pvSegments = new ArrayList<>(capacity);
    }

    public void addRecord(long instant, BasicParameterList sortedPvList) {
        do {
            addRecord(instant, sortedPvList.getPids(), sortedPvList.pvList);
            sortedPvList = sortedPvList.next();
        } while (sortedPvList != null);
    }

    /**
     * Add a new record
     * <p>
     * instant goes into the timeSegment the values goes each into a value segment
     */
    public void addRecord(long instant, IntArray pids, List<BasicParameterValue> values) {
        int idx1 = 0; // tracks the existing data
        int idx2 = 0; // tracks the new data
        int pos = timeSegment.add(instant);

        while (idx1 < pvSegments.size() && idx2 < pids.size()) {
            BasicParameterValue pv = values.get(idx2);
            ParameterValueSegment pvs = pvSegments.get(idx1);

            if (pvs.pid < pids.get(idx2)) {
                // parameter not part of the new data, we have to insert a gap in the existing data
                pvs.insertGap(pos);
                idx1++;
            } else if (pvs.pid > pids.get(idx2)) {
                // new parameter, we have to shift all existing segments to the right and insert a new segment with gaps
                // in all positions except pos
                ParameterValueSegment newPvs = new ParameterValueSegment(pids.get(idx2), timeSegment, pos, pv);
                pvSegments.add(idx1, newPvs);
                idx1++;
                idx2++;
            } else {
                // happy case, parameter exists both in the segments and in the new data
                pvs.insert(pos, pv);
                idx1++;
                idx2++;
            }
        }
        while (idx1 < pvSegments.size()) {
            ParameterValueSegment pvs = pvSegments.get(idx1);
            // parameter not part of the new data, we have to insert a gap in the existing data
            pvs.insertGap(pos);
            idx1++;
        }

        while (idx2 < pids.size()) {
            BasicParameterValue pv = values.get(idx2);
            // new segment to add to the end of the segment list
            ParameterValueSegment newPvs = new ParameterValueSegment(pids.get(idx2), timeSegment, pos, pv);
            pvSegments.add(newPvs);
            idx2++;
        }

    }

    public void consolidate() {
        for (var pvs : pvSegments) {
            pvs.consolidate();
        }
    }

    public ParameterValueSegment getParameterValue(int pid) {
        for (var pvs : pvSegments) {
            if (pvs.pid == pid) {
                return pvs;
            }
        }
        return null;
    }

    public MultiParameterValueSegment getParametersValues(ParameterId[] pids) {
        List<ParameterValueSegment> filteredPVSegments = new ArrayList<>(pids.length);
        for (ParameterId pid : pids) {
            boolean found = false;
            for (var pvs : pvSegments) {
                if (pvs.pid == pid.getPid()) {
                    filteredPVSegments.add(pvs);
                    found = true;
                }
            }
            if (!found) {
                filteredPVSegments.add(null);
            }
        }

        return new MultiParameterValueSegment(pids, timeSegment, filteredPVSegments);
    }

    public long getInterval() {
        return timeSegment.getInterval();
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

    /**
     * returns the number of rows in the segment group
     */
    public int size() {
        return timeSegment.size();
    }

    /**
     * returns the number of parameter segments inside this segment group
     */
    public int numSegments() {
        return pvSegments.size();
    }

    public int getParameterId(int idx) {
        return pvSegments.get(idx).pid;
    }

    public boolean isFirstInInterval() {
        return segmentIdxInsideInterval == 0;
    }

    /**
     * In case the interval is composed of multiple segments, this returns the idx of the segment inside interval.
     * <p>
     * For the first segment this will be 0, for the following segments it is the sum of the number of elements of the
     * previous segments.
     * <p>
     * The number is used when merging the segments together in the interval to know where the gaps are in the combined
     * interval.
     * 
     */
    public int getSegmentIdxInsideInterval() {
        return segmentIdxInsideInterval;
    }

    public void setSegmentIdxInsideInterval(int segmentIdxInsideInterval) {
        this.segmentIdxInsideInterval = segmentIdxInsideInterval;
    }

    public String toString() {
        return "groupId: " + parameterGroupId + ", [" + TimeEncoding.toString(getSegmentStart()) + ", "
                + TimeEncoding.toString(getSegmentEnd()) + "], size: " + size();
    }
}
