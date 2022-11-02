package org.yamcs.yarch.streamsql;

import java.math.BigDecimal;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.MergeStream;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;

class MergeExpression implements StreamExpression {
    ArrayList<TupleSourceExpression> sources = new ArrayList<>();
    String mergeColumn;
    boolean ascending = true;
    BigDecimal offset;
    BigDecimal limit;
    static Logger log = LoggerFactory.getLogger(MergeExpression.class.getName());

    public void setMergeColumn(String name) {
        mergeColumn = name;
    }

    public void setAscending(boolean ascending) {
        this.ascending = ascending;
    }

    public void setLimit(BigDecimal offset, BigDecimal limit) {
        this.offset = offset;
        this.limit = limit;
    }

    @Override
    public void bind(ExecutionContext c) throws StreamSqlException {
        for (TupleSourceExpression tps : sources) {
            tps.bind(c);
        }
    }

    @Override
    public Stream execute(ExecutionContext c) throws StreamSqlException {
        YarchDatabaseInstance ydb = c.getDb();
        Stream[] streams = new Stream[sources.size()];
        for (int i = 0; i < streams.length; i++) {
            streams[i] = sources.get(i).execute(c);
        }

        Stream stream;
        if (streams.length == 1) {
            stream = streams[0];
        } else {
            stream = new MergeStream(ydb, streams, mergeColumn, ascending);
        }

        if (limit != null || offset != null) {
            return new LimitedStream(ydb, stream, offset, limit, stream.getDefinition());
        } else {
            return stream;
        }
    }

    public void addTupleSourceExpression(TupleSourceExpression tsrc) {
        sources.add(tsrc);
    }

    @Override
    public TupleDefinition getOutputDefinition() {
        return null;
    }

    @Override
    public boolean isFinite() {
        for (TupleSourceExpression tsrc : sources) {
            if (!tsrc.isFinite()) {
                return false;
            }
        }
        return true;
    }
}
