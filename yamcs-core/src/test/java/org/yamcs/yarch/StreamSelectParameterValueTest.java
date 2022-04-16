package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.utils.ValueUtility;

public class StreamSelectParameterValueTest extends YarchTestCase {

    final int n = 20;

    public void createFeeder1() throws YarchException {
        Stream s;
        YarchDatabaseInstance ydb = context.getDb();
        final TupleDefinition tpdef = new TupleDefinition();
        tpdef.addColumn("pv", DataType.PARAMETER_VALUE);
        tpdef.addColumn("count", DataType.INT);

        s = (new Stream(ydb, "stream_in", tpdef) {
            @Override
            public void doStart() {
                for (int count = 0; count < n; count++) {
                    ParameterValue pv = new ParameterValue("/test/StreamSelectParameterValueTest");
                    pv.setEngineeringValue(ValueUtility.getDoubleValue(3.14));
                    Tuple t = new Tuple(tpdef, new Object[] { pv, count });
                    emitTuple(t);
                    close();
                }
            }

            @Override
            protected void doClose() {
            }
        });
        ydb.addStream(s);
    }

    @Test
    public void test1() throws Exception {
        createFeeder1();
        execute("create stream stream_out1 as select pv from stream_in where count >= ?", 3);

        List<Tuple> tlist = fetchAll("stream_out1");

        assertEquals(n - 3, tlist.size());
        Tuple t0 = tlist.get(0);
        ParameterValue pv = (ParameterValue) t0.getColumn("pv");
        assertEquals(3.14, pv.getEngValue().getDoubleValue(), 1e-6);
        assertEquals("/test/StreamSelectParameterValueTest", pv.getParameterQualifiedName());
    }
}
