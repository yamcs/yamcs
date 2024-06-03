package org.yamcs.parameterarchive;

import java.util.List;

/**
 * A collection of ParameterValueSegment with a common timeSegment.
 * <p>
 * Some of the segments may be null in case they contained no data (i.e. only gaps)
 */
public class MultiParameterValueSegment {
    ParameterId[] pids;
    SortedTimeSegment timeSegment;
    List<ParameterValueSegment> pvSegments;

    public MultiParameterValueSegment(ParameterId[] pids, SortedTimeSegment timeSegment,
            List<ParameterValueSegment> pvSegments) {
        if (pids.length != pvSegments.size()) {
            throw new IllegalArgumentException("number of segments " + pvSegments.size()
                    + " does not correspond to the number of parameters " + pids.length);
        }
        this.timeSegment = timeSegment;
        this.pvSegments = pvSegments;
    }

    public MultiParameterValueSegment(SortedTimeSegment timeSegment) {
        this.timeSegment = timeSegment;
    }

    @Override
    public String toString() {
        return "ParameterValueSegment[size: " + timeSegment.size() + "]";
    }

    public long getSegmentStart() {
        return timeSegment.getSegmentStart();
    }

    public long getSegmentEnd() {
        return timeSegment.getSegmentEnd();
    }

    public int size() {
        return timeSegment.size();
    }

    public ParameterValueSegment getPvs(int idx) {
        return pvSegments.get(idx);
    }

    public int numParameters() {
        return pvSegments.size();
    }
}
