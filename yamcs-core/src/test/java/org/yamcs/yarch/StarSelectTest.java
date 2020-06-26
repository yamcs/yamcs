package org.yamcs.yarch;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public class StarSelectTest extends YarchTestCase {

    int n = 10;

    void createFeeder() throws Exception {
        final TupleDefinition tpdef = new TupleDefinition();
        tpdef.addColumn("gentime", DataType.TIMESTAMP);
        tpdef.addColumn("id", DataType.INT);

        Stream s = (new Stream(ydb, "tm_in", tpdef) {
            @Override
            public void doStart() {
                for (int i = 0; i < n; i++) {
                    Long time = (long) (i * 1000);
                    Tuple t = new Tuple(tpdef, new Object[] { time, i });
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
    public void testStar1() throws Exception {
        createFeeder();
        execute("create stream tm_out (cucu STRING, gentime TIMESTAMP, id int)");

        execute("insert into tm_out select 'cucu' as bau,* from tm_in");

        List<Tuple> tlist = fetch("tm_out", "tm_in");
        assertEquals(n, tlist.size());
        for (int k = 0; k < n; k++) {
            Tuple tuple = tlist.get(k);
            assertEquals("cucu", tuple.getColumn("bau"));
            long time = (Long) tuple.getColumn(1);
            assertEquals(1000 * k, time);
            int i = (Integer) tuple.getColumn(2);
            assertEquals(k, i);
        }
    }
}
