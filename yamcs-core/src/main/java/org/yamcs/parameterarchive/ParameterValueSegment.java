package org.yamcs.parameterarchive;

import org.yamcs.utils.TimeEncoding;

/**
 * Stores parameter values for one parameter over a time range.
 * <p>
 * It is composed of a time, engineering, raw and parameter status segments;
 */
public class ParameterValueSegment {
    SortedTimeSegment timeSegment;
    ValueSegment engValueSegment;
    ValueSegment rawValueSegment;
    ParameterStatusSegment parameterStatusSegment;

    public ParameterValueSegment(SortedTimeSegment timeSegment, ValueSegment engValueSegment,
            ValueSegment rawValueSegment,
            ParameterStatusSegment parameterStatus) {
        this.timeSegment = timeSegment;
        this.engValueSegment = engValueSegment;
        this.rawValueSegment = rawValueSegment;
        this.parameterStatusSegment = parameterStatus;
    }

    public ParameterValueSegment(SortedTimeSegment timeSegment) {
        this.timeSegment = timeSegment;
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

    @Override
    public String toString() {
        return "ParameterValueSegment[size: " + timeSegment.size() + ", start: "
                + TimeEncoding.toString(getSegmentStart())
                + ", end: " + TimeEncoding.toString(getSegmentEnd()) + "]";
    }
}
