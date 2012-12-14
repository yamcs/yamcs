package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.AbstractStream;
import org.yamcs.yarch.TupleDefinition;

import org.yamcs.yarch.streamsql.ExecutionContext;
import org.yamcs.yarch.streamsql.StreamSqlException;

/**
 * Expression that upon execution produces a stream. Only to be used from the *Statement classes.
 * The dict has to be locked when the execute is run.
 * @author nm
 *
 */
interface StreamExpression {
  public AbstractStream execute(ExecutionContext c) throws StreamSqlException;

  public void bind(ExecutionContext c) throws StreamSqlException;

  public TupleDefinition getOutputDefinition();
}
