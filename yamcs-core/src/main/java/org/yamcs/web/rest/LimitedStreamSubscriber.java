package org.yamcs.web.rest;

import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;

/**
 * Reusable mechanism to process a paged set of stream data data. Should ultimately be replaced
 * by implementing the limit clause in StreamSQL.
 */
public abstract class LimitedStreamSubscriber implements StreamSubscriber {
    
    private final long pos;
    private final int limit;
    
    private int rowNr = 0; // zero-based
    private int emitted = 0;
    
    public LimitedStreamSubscriber(long pos, int limit) {
        this.pos = Math.max(pos, 0);
        this.limit = Math.max(limit, 0);
    }

    @Override
    public void onTuple(Stream stream, Tuple tuple) {
        if (rowNr >= pos) {
            if (emitted < limit) {
                emitted++;
                onTuple(tuple);
            } else {
                stream.close();
            }
        }
        rowNr++;
    }
    
    @Override
    public void streamClosed(Stream stream) {
        // NOP
    }
    
    public abstract void onTuple(Tuple tuple);
}
