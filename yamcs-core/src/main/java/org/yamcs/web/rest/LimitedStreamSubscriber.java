package org.yamcs.web.rest;

import org.slf4j.LoggerFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;

/**
 * Reusable mechanism to process a paged set of stream data data. Should ultimately be replaced
 * by implementing the limit clause in StreamSQL.
 */
public abstract class LimitedStreamSubscriber implements StreamSubscriber {
    
    private final long start;
    private final int limit;
    
    private int rowNr = 0; // zero-based
    private int emitted = 0;
    
    public LimitedStreamSubscriber(long start, int limit) {
        this.start = Math.max(start, 0);
        this.limit = Math.max(limit, 0);
    }

    @Override
    public void onTuple(Stream stream, Tuple tuple) {
        if (rowNr >= start) {
            if (emitted < limit) {
                LoggerFactory.getLogger(getClass()).info("emitting because " + emitted + " < " + limit);
                emitted++;
                onTuple(tuple);
            } else {
                stream.close();
            }
        }
        rowNr++;
    }
    
    public abstract void onTuple(Tuple tuple);
}
