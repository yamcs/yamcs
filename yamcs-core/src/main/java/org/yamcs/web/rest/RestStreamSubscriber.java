package org.yamcs.web.rest;

import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;

public abstract class RestStreamSubscriber implements StreamSubscriber {
    
    private final boolean paginate;
    private final long pos;
    private final int limit;
    
    private int rowNr = 0; // zero-based
    private int emitted = 0;
    
    /**
     * process a paged set of stream data data
     */
    public RestStreamSubscriber(long pos, int limit) {
        paginate = true;
        this.pos = Math.max(pos, 0);
        this.limit = Math.max(limit, 0);
    }
    
    public RestStreamSubscriber() {
        paginate = false;
        pos = -1;
        limit = -1;
    }

    @Override
    public void onTuple(Stream stream, Tuple tuple) {
        if (paginate) {
            if (rowNr >= pos) {
                if (emitted < limit) {
                    emitted++;
                    processTuple(stream, tuple);
                } else {
                    stream.close();
                }
            }
            rowNr++;
        } else {
            processTuple(stream, tuple);
        }
    }
    
    public abstract void processTuple(Stream stream, Tuple tuple);

}
