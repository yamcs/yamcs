package org.yamcs.yarch.streamsql;

import java.util.function.Consumer;

import org.yamcs.yarch.DataType;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.Sequence;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;

public class AlterSequenceStatement  extends SimpleStreamSqlStatement {
    final String seqName;
    final long value;
    
    public AlterSequenceStatement(String seqName, long withValue) {
        this.seqName = seqName;
        this.value = withValue;
    }
    
    private static final TupleDefinition TDEF = new TupleDefinition();
    static {
        TDEF.addColumn("name", DataType.STRING);
        TDEF.addColumn("value", DataType.LONG);
    }

    @Override
    protected void execute(ExecutionContext context, Consumer<Tuple> consumer) throws StreamSqlException {
        YarchDatabaseInstance ydb = context.getDb();
        Sequence seq = ydb.getSequence(seqName, false);
        if(seq==null) {
            throw new GenericStreamSqlException("Unknown sequence "+seqName);
        }
        seq.reset(value);
        Tuple tuple = new Tuple(TDEF, new Object[] { seqName, value});
        consumer.accept(tuple);
    }

    @Override
    protected TupleDefinition getResultDefinition() {
        return TDEF;
    }
}
