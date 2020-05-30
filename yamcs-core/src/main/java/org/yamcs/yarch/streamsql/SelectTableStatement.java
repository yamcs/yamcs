package org.yamcs.yarch.streamsql;

import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class SelectTableStatement implements StreamSqlStatement {

    private SelectExpression expression;

    public SelectTableStatement(SelectExpression expression) {
        this.expression = expression;
    }

    @Override
    public void execute(ExecutionContext c, ResultListener resultListener) throws StreamSqlException {
        YarchDatabaseInstance dict = YarchDatabase.getInstance(c.getDbName());
        synchronized (dict) {
            if (dict.getTable(expression.tupleSourceExpression.objectName) == null) {
                throw new GenericStreamSqlException(String.format("Object %s does not exist or is not a table"));
            }
            if (resultListener == null) {
                throw new GenericStreamSqlException("Cannot select without a result listener");
            }
            expression.bind(c);
            Stream stream = expression.execute(c);
            stream.addSubscriber(new StreamSubscriber() {

                @Override
                public void onTuple(Stream stream, Tuple tuple) {
                    resultListener.next(tuple);
                }

                @Override
                public void streamClosed(Stream stream) {
                    resultListener.complete();
                }
            });
            stream.start();
        }
    }
}
