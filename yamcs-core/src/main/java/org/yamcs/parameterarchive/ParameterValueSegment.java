package org.yamcs.parameterarchive;

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
}
