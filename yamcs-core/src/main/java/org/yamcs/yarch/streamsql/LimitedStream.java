package org.yamcs.yarch.streamsql;

import java.math.BigDecimal;

import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;

public class LimitedStream extends Stream implements StreamSubscriber {

    private Stream input;

    private long offset = 0;
    private long limit = Long.MAX_VALUE;

    protected LimitedStream(YarchDatabaseInstance ydb, Stream input, BigDecimal offset, BigDecimal limit,
            TupleDefinition definition) {
        super(ydb, input.getName() + "_limit", definition);
        this.input = input;
        if (offset != null) {
            this.offset = Math.abs(offset.longValue());
        }
        if (limit != null) {
            this.limit = Math.abs(limit.longValue());
        }
        input.addSubscriber(this);
    }

    @Override
    public void doStart() {
        if (input.getState() == SETUP) {
            input.start();
        }
    }

    // Called when the input stream received a tuple
    @Override
    public void onTuple(Stream stream, Tuple tuple) {
        long inputDataCount = stream.getDataCount();
        if (inputDataCount < offset + 1) {
            return;
        }
        if (getDataCount() < limit) {
            emitTuple(tuple);
        } else {
            input.close();
        }
    }

    // Called when the input stream is closed
    @Override
    public void streamClosed(Stream stream) {
        close();
    }

    @Override
    protected void doClose() {
        input.close();
    }
}
