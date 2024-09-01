package org.yamcs.parameterarchive;

import java.util.ArrayList;

import java.util.List;

import org.yamcs.parameter.BasicParameterValue;
import org.yamcs.parameterarchive.ParameterGroupIdDb.ParameterGroup;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.IntHashSet;
import org.yamcs.utils.TimeEncoding;

/**
 * Parameter Group segment - keeps references to Time and Value segments for a given parameter group and segment.
 * <p>
 * This class is used during the parameter archive buildup.
 * <p>
 * In Yamcs 5.10 the RocksDB merge operator has been introduced. The operator is responsible for merging multiple
 * segnemtns into an interval.
 * <p>
 * Merging the time, engineering/raw values, and status segments is straightforward - they are just appended one after
 * the other.
 * <p>
 * Merging the gap segments is tricky: the gaps store the indices of the elements that are missing for one particular
 * parameter. When we merge segments into intervals, the indices change so we have to renumber all of them. For this
 * purpose the {@link #segmentIdxInsideInterval} is used - it represents where in the interval this segment starts.
 * <p>
 * In general we do not want to create records for parameters that do not appear at all in the interval (that's why this
 * class does not cater for all parameters that might be in a {@link ParameterGroup}). However if a parameter appears in
 * one of the segments of the interval, it has to be propagated throughout the interval - that is we have to create gaps
 * for those segments in the interval where it does not appear.
 * <p>
 * There are two situations to handle:
 * <ol>
 * <li>A segment seg1 contains a parameter p but the subsequent segment seg2 does not contain it. In this case we have
 * to add a full gap segment for seg2.</li>
 * 
 * <li>A segment seg1 does not contain a parameter p but the subsequent segment seg2 contains it. In this case we have
 * to add a full gap segment for seg1. Since when seg1 was created we did not know that the parameter p will be part of
 * the interval and seg1 may already be written in the archive when the subsequent segment is encountered, we need to
 * write the gap segment later.</li>
 * </ol>
 * 
 * 
 */
public class PGSegment {
    final int parameterGroupId;
    private SortedTimeSegment timeSegment;
    List<ParameterValueSegment> pvSegments;

    /**
     * This contains the parameters that have appeared in one of the previous segments of the interval and do not appear
     * in this segment
     */
    IntHashSet currentFullGaps;

    /**
     * This contains the parameters that appear for the first time in this segment (they haven't been part of the
     * previous segments in this interval); We need to create gaps to cover the previous segments; otherwise the merging
     * will go awry.
     */
    IntHashSet previousFullGaps;

    // for the first segment in the interval this is zero
    // for the subsequent segments it is the number of rows from the previous segments
    int segmentIdxInsideInterval;

    /**
     * as a safety measure we set this to true once no data is allowed in this segment. Then the next segment in the
     * interval can be initialised with the proper gap counting
     */
    boolean frozen = false;

    public PGSegment(int parameterGroupId, long interval) {
        this(parameterGroupId, interval, 1000);
    }

    public PGSegment(int parameterGroupId, long interval, int capacity) {
        this.parameterGroupId = parameterGroupId;
        this.timeSegment = new SortedTimeSegment(interval);
        this.pvSegments = new ArrayList<>(capacity);
    }

    public PGSegment(int parameterGroupId, SortedTimeSegment timeSegment, List<ParameterValueSegment> pvSegments) {
        this.parameterGroupId = parameterGroupId;
        this.timeSegment = timeSegment;
        this.pvSegments = pvSegments;
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
        if (frozen) {
            throw new UnsupportedOperationException("The segment is frozen, new data is not accepted");
        }
        int idx1 = 0; // tracks the existing data
        int idx2 = 0; // tracks the new data
        int pos = timeSegment.add(instant);
        while (idx1 < pvSegments.size() && idx2 < pids.size()) {
            var pid2 = pids.get(idx2);
            BasicParameterValue pv = values.get(idx2);
            ParameterValueSegment pvs = pvSegments.get(idx1);

            if (pvs.pid < pid2) {
                // parameter not part of the new data, we have to insert a gap in the existing data
                pvs.insertGap(pos);
                idx1++;
            } else if (pvs.pid > pid2) {
                // new parameter, we have to shift all existing segments to the right and insert a new segment with gaps
                // in all positions except pos
                ParameterValueSegment newPvs = new ParameterValueSegment(pid2, timeSegment, pos, pv);
                pvSegments.add(idx1, newPvs);
                if (currentFullGaps != null && !currentFullGaps.remove(pid2)) {
                    // pid2 is part of this segment and was not part of the previous segments
                    // it means we need to generate gaps for it in the previous segments when merging them
                    previousFullGaps.add(pid2);
                }
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
            var pid2 = pids.get(idx2);
            // new segment to add to the end of the segment list
            ParameterValueSegment newPvs = new ParameterValueSegment(pid2, timeSegment, pos, pv);
            pvSegments.add(newPvs);
            if (currentFullGaps != null && !currentFullGaps.remove(pid2)) {
                // pid2 is added to this segment but was not part of the previous segments
                previousFullGaps.add(pid2);
            }
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

    /**
     * 
     * populate the currentFullGaps and previousFullGaps based on the previous segment parameters and gaps
     * <p>
     * sets also the segmentIdxInsideInterval
     */
    public void continueSegment(PGSegment prevSegment) {
        assert (prevSegment.isFrozen());
        this.segmentIdxInsideInterval = prevSegment.getSegmentIdxInsideInterval() + prevSegment.size();
        var pvl1 = prevSegment.pvSegments;
        var pvl2 = pvSegments;
        int idx1 = 0; // tracks the previous segment
        int idx2 = 0; // tracks this segment

        if (prevSegment.currentFullGaps == null) {
            currentFullGaps = new IntHashSet();
        } else {
            currentFullGaps = prevSegment.currentFullGaps.clone();
        }

        previousFullGaps = new IntHashSet();
        while (idx1 < pvl1.size() && idx2 < pvl2.size()) {
            var pid1 = pvl1.get(idx1).pid;
            var pid2 = pvl2.get(idx2).pid;

            if (pid1 < pid2) {
                // pid1 not part of this segment
                currentFullGaps.add(pid1);
                idx1++;
            } else if (pid1 > pid2) {
                // pid2 not part of the previous segment
                previousFullGaps.add(pid2);
                idx2++;
            } else {
                // happy case, parameter exists both in the segments and in the new data
                idx1++;
                idx2++;
            }
        }
        while (idx1 < pvl1.size()) {
            var pid1 = pvl1.get(idx1).pid;
            // pid1 not part of this segment
            currentFullGaps.add(pid1);

            idx1++;
        }

        while (idx2 < pvl2.size()) {
            var pid2 = pvl2.get(idx2).pid;
            previousFullGaps.add(pid2);
            idx2++;
        }
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
    public int numParameters() {
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

    public boolean isFrozen() {
        return frozen;
    }

    /**
     * after this is called, no more data can be inserted in the segment
     * <p>
     * It is used as a safety check when initialising the next segment in the interval
     */
    public void freeze() {
        this.frozen = true;
    }

    public String toString() {
        return "groupId: " + parameterGroupId + ", [" + TimeEncoding.toString(getSegmentStart()) + ", "
                + TimeEncoding.toString(getSegmentEnd()) + "], size: " + size();
    }

    /**
     * returns true if this segment contains the first value of this parameter for this interval
     */
    public boolean wasPreviousGap(int pid) {
        return previousFullGaps != null && previousFullGaps.contains(pid);
    }

    /**
     * In rare circumstances, a segment read from the archive has to be modified.
     * <p>
     * This method updates the object such that it can be modified
     */
    public void makeWritable() {
        for (var pvs : pvSegments) {
            pvs.makeWritable();
        }
    }
}
