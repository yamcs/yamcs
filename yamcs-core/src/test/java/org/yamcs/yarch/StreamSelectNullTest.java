package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

public class StreamSelectNullTest extends YarchTestCase {

    final int n = 200;

    public void createFeeder1() throws YarchException {
        Stream s;
        final TupleDefinition tpdef1 = new TupleDefinition();
        final TupleDefinition tpdef2 = new TupleDefinition();
        tpdef1.addColumn("x", DataType.INT);
        tpdef1.addColumn("y", DataType.STRING);
        tpdef2.addColumn("x", DataType.INT);

        s = (new Stream(ydb, "stream_in", tpdef1) {
            @Override
            public void doStart() {
                for (int i = 0; i < n; i++) {
                    Tuple t;
                    if (i % 2 == 0) {
                        String y = "s" + i;
                        t = new Tuple(tpdef1, new Object[] { i, y });
                    } else {
                        t = new Tuple(tpdef2, new Object[] { i });
                    }

                    emitTuple(t);
                }
                close();
            }

            @Override
            protected void doClose() {
            }
        });
        ydb.addStream(s);
    }

    @Test
    public void testNotNull() throws Exception {
        createFeeder1();

        execute("create stream stream_out1 as select * from stream_in where y is not NULL");
        final List<Tuple> tuples = fetchAll("stream_out1");
        assertEquals(n / 2, tuples.size());
        for (int i = 0; i < n / 2; i++) {
            int x = (Integer) tuples.get(i).getColumn("x");
            assertEquals(2 * i, x);
        }

    }

    @Test
    public void testNull() throws Exception {
        createFeeder1();

        execute("create stream stream_out1 as select * from stream_in where y is NULL");
        final List<Tuple> tuples = fetchAll("stream_out1");
        assertEquals(n / 2, tuples.size());
        for (int i = 0; i < n / 2; i++) {

            int x = (Integer) tuples.get(i).getColumn("x");
            assertEquals(2 * i + 1, x);
        }

    }
}
