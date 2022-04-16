package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class CreateStreamAsSelectTest extends YarchTestCase {

    int n = 10;

    void createFeeder() throws Exception {
        final TupleDefinition tpdef = new TupleDefinition();
        tpdef.addColumn("time", DataType.TIMESTAMP);
        tpdef.addColumn("id", DataType.INT);
        tpdef.addColumn("id1", DataType.UUID);

        Stream s = (new Stream(ydb, "tm_in", tpdef) {
            @Override
            public void doStart() {
                for (int i = 0; i < n; i++) {
                    Long time = (long) (i * 1000);
                    Tuple t = new Tuple(tpdef, new Object[] { time, i, UUID.randomUUID() });
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
        execute("create stream tm_out1 as select 3, \'cucu\' as bau, * from tm_in");
        List<Tuple> tlist = fetchAll("tm_out1");
        assertEquals(n, tlist.size());
        for (int k = 0; k < n; k++) {
            Tuple tuple = tlist.get(k);
            int const_three = (Integer) tuple.getColumn(0);
            assertEquals(const_three, 3);
            assertEquals("cucu", tuple.getColumn("bau"));

            long time = (Long) tuple.getColumn(2);
            assertEquals(1000 * k, time);

            int i = (Integer) tuple.getColumn(3);
            assertEquals(k, i);

            UUID uuid = (UUID) tuple.getColumn(4);
            assertNotNull(uuid);
        }
    }
}
