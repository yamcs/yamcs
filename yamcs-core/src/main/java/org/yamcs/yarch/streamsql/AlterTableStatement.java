package org.yamcs.yarch.streamsql;

import java.util.function.Consumer;

import org.yamcs.yarch.DataType;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabaseInstance;

/**
 * Alter table supports only
 * <code>
 * ALTER TABLE &lt;name&gt; RENAME TO &lt;new_name&gt;
 * </code>
 */
public class AlterTableStatement extends SimpleStreamSqlStatement {
    final String oldName;
    final String newName;

    public AlterTableStatement(String name, String newName) {
        this.oldName = name;
        this.newName = newName;
    }

    private static final TupleDefinition TDEF = new TupleDefinition();
    static {
        TDEF.addColumn("oldName", DataType.STRING);
        TDEF.addColumn("newName", DataType.STRING);
    }

    @Override
    protected void execute(ExecutionContext context, Consumer<Tuple> consumer) throws StreamSqlException {
        YarchDatabaseInstance ydb = context.getDb();
        ydb.renameTable(oldName, newName);
        Tuple tuple = new Tuple(TDEF, new Object[] { oldName, newName });
        consumer.accept(tuple);
    }

    @Override
    protected TupleDefinition getResultDefinition() {
        return TDEF;
    }
}
