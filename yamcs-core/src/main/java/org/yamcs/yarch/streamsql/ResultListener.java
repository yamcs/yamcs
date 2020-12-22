package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

public interface ResultListener {
    /**
     * Called at the beginning to provide the schema of the result.
     * Not all tuples will have all columns.
     * 
     * @param tdef
     */
    default void start(TupleDefinition tdef) {
    }

    void next(Tuple tuple);

    void completeExceptionally(Throwable t);

    void complete();
}
