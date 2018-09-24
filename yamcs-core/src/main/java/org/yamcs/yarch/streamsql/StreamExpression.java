package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TupleDefinition;

/**
 * Expression that upon execution produces a stream. Only to be used from the *Statement classes. The dict has to be
 * locked when the execute is run.
 * 
 * @author nm
 *
 */
interface StreamExpression {

    public Stream execute(ExecutionContext c) throws StreamSqlException;

    public void bind(ExecutionContext c) throws StreamSqlException;

    public TupleDefinition getOutputDefinition();
}
