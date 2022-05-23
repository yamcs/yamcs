package org.yamcs.yarch.streamsql;

import java.util.function.Consumer;

import org.yamcs.yarch.DataType;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.SequenceInfo;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;

public class ShowSequencesStatement extends SimpleStreamSqlStatement {

    private static final TupleDefinition TDEF = new TupleDefinition();
    static {
        TDEF.addColumn("name", DataType.STRING);
        TDEF.addColumn("value", DataType.LONG);
    }

    @Override
    protected void execute(ExecutionContext context, Consumer<Tuple> consumer) throws StreamSqlException {
        YarchDatabaseInstance ydb = context.getDb();
        for (SequenceInfo seq : ydb.getSequencesInfo()) {
            Tuple tuple = new Tuple(TDEF, new Object[] { seq.getName(), seq.getValue() });
            consumer.accept(tuple);
        }
    }

    @Override
    protected TupleDefinition getResultDefinition() {
        return TDEF;
    }
}
