package org.yamcs.yarch.streamsql;

/**
 * Tag interface for all StreamSQL statements. The execute method locks the dictionary for the period of execution.
 * 
 * @author nm
 */
public interface StreamSqlStatement {

    void execute(ExecutionContext c, ResultListener resultListener) throws StreamSqlException;
}
