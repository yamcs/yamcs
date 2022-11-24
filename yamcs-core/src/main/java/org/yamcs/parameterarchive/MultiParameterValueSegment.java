package org.yamcs.parameterarchive;

/**
 * Like {@link ParameterValueSegment} but stores segments of multiple parameters from the same group.
 * <p>
 * There is only one timeSegment common to all
 *
 */
public class MultiParameterValueSegment {
    SortedTimeSegment timeSegment;
    ValueSegment[] engValueSegments;
    ValueSegment[] rawValueSegments;
    ParameterStatusSegment[] parameterStatusSegments;

    public MultiParameterValueSegment(SortedTimeSegment timeSegment, ValueSegment[] engValueSegments,
            ValueSegment[] rawValueSegments, ParameterStatusSegment[] parameterStatuses) {
        this.timeSegment = timeSegment;
        this.engValueSegments = engValueSegments;
        this.rawValueSegments = rawValueSegments;
        this.parameterStatusSegments = parameterStatuses;
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
}
