package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.YarchDatabaseInstance;

/**
 * Tag interface for all StreamSQL statements.
 */
public interface StreamSqlStatement {
    /**
     * Execute query and limit the number of results returned.
     * <p>
     * Note that the update/delete/drop table queries that return one row are executed even if the limit is 0. The
     * output however is suppressed when the limit is set to 0.
     *

     * @param ydb
     * @param resultListener
     * @param limit
     * @throws StreamSqlException
     */
    void execute(YarchDatabaseInstance ydb, ResultListener resultListener, long limit) throws StreamSqlException;

    /**
     * Execute query and send the results to the result listener.
     *
     * @param ydb
     * @param resultListener
     * @throws StreamSqlException
     */
    default void execute(YarchDatabaseInstance ydb, ResultListener resultListener) throws StreamSqlException {
        execute(ydb, resultListener, Long.MAX_VALUE);
    }

    /**
     * Execute query and return a result. The result can be closed at any time.

     * @param ydb
     * @return
     * @throws StreamSqlException
     */
    StreamSqlResult execute(YarchDatabaseInstance ydb) throws StreamSqlException;
}
