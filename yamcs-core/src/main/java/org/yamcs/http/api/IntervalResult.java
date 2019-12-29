package org.yamcs.http.api;

import org.yamcs.utils.TimeEncoding;

public class IntervalResult {

    private long start;
    private long stop;
    private boolean inclusiveStart = true;
    private boolean inclusiveStop = false;

    public IntervalResult(long start, long stop) {
        this.start = start;
        this.stop = stop;
    }

    public boolean hasInterval() {
        return start != TimeEncoding.INVALID_INSTANT || stop != TimeEncoding.INVALID_INSTANT;
    }

    public boolean hasStart() {
        return start != TimeEncoding.INVALID_INSTANT;
    }

    public boolean hasStop() {
        return stop != TimeEncoding.INVALID_INSTANT;
    }

    public long getStart() {
        return start;
    }

    public long getStop() {
        return stop;
    }

    public void setStart(long start, boolean inclusive) {
        this.start = start;
        this.inclusiveStart = inclusive;
    }

    public void setStop(long stop, boolean inclusive) {
        this.stop = stop;
        this.inclusiveStop = inclusive;
    }

    public String asSqlCondition(String col) {
        StringBuilder buf = new StringBuilder();
        if (start != TimeEncoding.INVALID_INSTANT) {
            buf.append(col);
            buf.append(inclusiveStart ? " >= " : " > ");
            buf.append(start);
            if (stop != TimeEncoding.INVALID_INSTANT) {
                buf.append(" and ").append(col);
                buf.append(inclusiveStop ? " <= " : " < ");
                buf.append(stop);
            }
        } else {
            buf.append(col);
            buf.append(inclusiveStop ? " <= " : " < ");
            buf.append(stop);
        }
        return buf.toString();
    }
}
