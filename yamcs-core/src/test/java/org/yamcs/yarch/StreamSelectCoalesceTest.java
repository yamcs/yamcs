package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

public class StreamSelectCoalesceTest extends YarchTestCase {

    final int n = 3;

    public void createFeeder1() throws YarchException {
        Stream s;
        final TupleDefinition tpdef1 = new TupleDefinition();
        final TupleDefinition tpdef2 = new TupleDefinition();
        final TupleDefinition tpdef3 = new TupleDefinition();
        tpdef1.addColumn("x", DataType.INT);
        tpdef1.addColumn("y", DataType.STRING);
        tpdef2.addColumn("y", DataType.INT);

        s = (new Stream(ydb, "stream_in", tpdef1) {
            @Override
            public void doStart() {
                for (int i = 0; i < n; i++) {
                    Tuple t;
                    String y = "s" + i;
                    if (i % 3 == 0) {
                        t = new Tuple(tpdef1, new Object[] { i, y });
                    } else if (i % 3 == 1) {
                        t = new Tuple(tpdef2, new Object[] { y });
                    } else {
                        t = new Tuple(tpdef3, new Object[] {});
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
    public void test1() throws Exception {
        createFeeder1();

        execute("create stream stream_out1 as select coalesce(x,y) from stream_in");
        final List<Tuple> tuples = fetchAll("stream_out1");
        assertEquals(n, tuples.size());
        for (int i = 0; i < n; i++) {
            Tuple t = tuples.get(i);
            if (i % 3 == 0) {
                Integer x = (Integer) t.getColumn(0);
                assertEquals(i, x.intValue());
            } else if (i % 3 == 1) {
                String y = (String) t.getColumn(0);
                assertEquals("s" + i, y);
            } else {
                Object o = t.getColumn(0);
                assertNull(o);
            }
        }
    }

    @Test
    public void test2() throws Exception {
        createFeeder1();

        execute("create stream stream_out1 as select * from stream_in where coalesce(x, y) is NULL");
        final List<Tuple> tuples = fetchAll("stream_out1");
        assertEquals(n / 3, tuples.size());
        for (int i = 0; i < n / 2; i++) {
            Integer x = tuples.get(i).getColumn("x");
            String y = tuples.get(i).getColumn("y");
            assertNull(x);
            assertNull(y);
        }

    }
}
