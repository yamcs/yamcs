package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchException;

/**
 * Expression that upon execution produces a stream. Only to be used from the *Statement classes.
 * 
 * @author nm
 *
 */
interface StreamExpression {

    public Stream execute(ExecutionContext c) throws StreamSqlException, YarchException;

    public void bind(ExecutionContext c) throws StreamSqlException;

    public TupleDefinition getOutputDefinition();

    /**
     * If the stream produced by the execute has a finite number of elements, return true;
     * <p>
     * For example a select from a table will produce a finite number of elements whereas a select from a stream not.
     * <p>
     * This is used in the statements to wait for the execution of the statement or run it in background.
     *
     * @return true if the stream created will contain a finite number of elements.
     *
     */
    boolean isFinite();
}
