package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchException;

/**
 * Expression that upon execution produces a stream. Only to be used from the *Statement classes. The dict has to be
 * locked when the execute is run.
 * 
 * @author nm
 *
 */
interface StreamExpression {

    public Stream execute(ExecutionContext c) throws StreamSqlException, YarchException;

    public void bind(ExecutionContext c) throws StreamSqlException;

    public TupleDefinition getOutputDefinition();
}
