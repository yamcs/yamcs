package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.Tuple;

public interface ResultListener {

    void next(Tuple tuple);

    void completeExceptionally(Throwable t);

    void complete();
}
