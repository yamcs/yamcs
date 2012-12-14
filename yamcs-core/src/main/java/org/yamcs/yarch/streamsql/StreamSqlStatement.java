package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.streamsql.ExecutionContext;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;


/**
 * Superclass of all the StreamSQL statements. The execute method locks the dictionary for the period of execution.
 * @author nm
 *
 */
public abstract class StreamSqlStatement {
  public abstract StreamSqlResult execute(ExecutionContext c) throws StreamSqlException ;
}
