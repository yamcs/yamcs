package org.yamcs.ui;

import org.yamcs.utils.TimeEncoding;

/**
 * Time interval where both ends can be open
 */
public class TimeInterval {
    private long start;
    private long end;
    private boolean hasStart = false;
    private boolean hasEnd = false;

    public TimeInterval(long start, long end) {
        setStart(start);
        setEnd(end);
    }

    /**
     * Creates a TimeInterval with no start and no end
     */
    public TimeInterval() {
    }

    public boolean hasStart() {
        return hasStart;
    }

    public boolean hasEnd() {
        return hasEnd;
    }

    public void setStart(long start) {
        hasStart = true;
        this.start = start;
    }

    public long getStart() {
        return start;
    }

    public void setEnd(long end) {
        hasEnd = true;
        this.end = end;
    }

    public long getEnd() {
        return end;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        if (hasStart) {
            sb.append(start);
        }
        sb.append(",");
        if (hasEnd) {
            sb.append(end);
        }
        sb.append(")");
        return sb.toString();
    }

    public String toStringEncoded() {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        if (hasStart) {
            sb.append(TimeEncoding.toString(start));
        }
        sb.append(",");
        if (hasEnd) {
            sb.append(TimeEncoding.toString(end));
        }
        sb.append(")");
        return sb.toString();
    }

}
