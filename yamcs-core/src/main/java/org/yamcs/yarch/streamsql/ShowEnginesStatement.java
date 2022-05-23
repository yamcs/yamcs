package org.yamcs.yarch.streamsql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.yamcs.yarch.DataType;
import org.yamcs.yarch.ExecutionContext;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;

public class ShowEnginesStatement extends SimpleStreamSqlStatement {

    private static final TupleDefinition TDEF = new TupleDefinition();
    static {
        TDEF.addColumn("engine", DataType.STRING);
        TDEF.addColumn("default", DataType.STRING);
    }

    @Override
    protected void execute(ExecutionContext context, Consumer<Tuple> c) {
        List<String> engines = new ArrayList<>(YarchDatabase.getStorageEngineNames());
        Collections.sort(engines);
        for (String engine : engines) {
            String def = engine.equals(YarchDatabase.getDefaultStorageEngineName()) ? "*" : null;
            Tuple tuple = new Tuple(TDEF, new Object[] { engine, def });
            c.accept(tuple);
        }
    }

    @Override
    protected TupleDefinition getResultDefinition() {
        return TDEF;
    }
}
