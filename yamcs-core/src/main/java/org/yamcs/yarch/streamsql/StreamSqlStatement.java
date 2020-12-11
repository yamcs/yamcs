package org.yamcs.yarch.streamsql;

/**
 * Tag interface for all StreamSQL statements. The execute method locks the dictionary for the period of execution.
 * 
 * @author nm
 */
public interface StreamSqlStatement {
    /**
     * Execute query and limit the number of results returned.
     * <p>
     * Note that the update/delete/drop table queries that return one row are executed even if the limit is 0. The output however
     * is suppressed when the limit is set to 0.
     *
     * @param context
     * @param resultListener
     * @param limit
     * @throws StreamSqlException
     */
    void execute(ExecutionContext context, ResultListener resultListener, long limit) throws StreamSqlException;

    /**
     * Execute query and send the results to the result listener.
     *
     * @param context
     * @param resultListener
     * @throws StreamSqlException
     */
    default void execute(ExecutionContext context, ResultListener resultListener) throws StreamSqlException {
        execute(context, resultListener, Long.MAX_VALUE);
    }

    /**
     * Execute query and return a result. The result can be closed at any time.
     *
     * @param context
     * @return
     * @throws StreamSqlException
     */
    StreamSqlResult execute(ExecutionContext context) throws StreamSqlException;
}
