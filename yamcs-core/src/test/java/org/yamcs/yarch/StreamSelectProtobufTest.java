package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.yamcs.protobuf.Event.EventSeverity;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.protobuf.Db.Event;
import org.yamcs.yarch.streamsql.GenericStreamSqlException;

public class StreamSelectProtobufTest extends YarchTestCase {

    final int n = 20;

    public void createFeeder1() throws YarchException {
        Stream s;
        YarchDatabaseInstance ydb = context.getDb();
        final TupleDefinition tpdef = new TupleDefinition();
        tpdef.addColumn("event", DataType.protobuf(Event.class.getName()));
        tpdef.addColumn("y", DataType.INT);

        s = (new Stream(ydb, "stream_in", tpdef) {
            @Override
            public void doStart() {
                for (int i = 0; i < n; i++) {
                    Event event = Event.newBuilder().setSource("test" + i).setSeqNumber(i)
                            .setGenerationTime(TimeEncoding.getWallclockTime())
                            .setReceptionTime(TimeEncoding.getWallclockTime())
                            .setMessage("msg" + i)
                            .setSeverity(i == 5 ? EventSeverity.INFO : EventSeverity.WARNING).build();
                    Integer y = i * 2;
                    Tuple t = new Tuple(tpdef, new Object[] { event, y });
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
    public void testSelectInvalidField() throws Exception {
        createFeeder1();
        GenericStreamSqlException ge = null;
        try {
            execute("create stream stream_out1 as select event from stream_in where event.invalidFieldName > 3");
        } catch (GenericStreamSqlException e) {
            ge = e;
        }

        assertNotNull(ge);
        assertTrue(ge.getMessage().contains("'event.invalidFieldName' is not an input column"));
    }

    @Test
    public void test1() throws Exception {
        createFeeder1();
        execute("create stream stream_out1 as select event from stream_in where event.seqNumber >= ?", 3);

        List<Tuple> tlist = fetchAll("stream_out1");

        assertEquals(n - 3, tlist.size());
        Tuple t0 = tlist.get(0);
        assertEquals(3, ((Event) t0.getColumn("event")).getSeqNumber());
    }

    @Test
    public void test2() throws Exception {
        createFeeder1();
        execute("create stream stream_out1 as select event from stream_in where event.message like '%15%'");

        List<Tuple> tlist = fetchAll("stream_out1");

        assertEquals(1, tlist.size());
        Tuple t0 = tlist.get(0);
        assertEquals(15, ((Event) t0.getColumn("event")).getSeqNumber());
    }

    @Test
    public void test3() throws Exception {
        createFeeder1();
        execute("create stream stream_out1 as select event from stream_in where event.severity in ('INFO')");

        List<Tuple> tlist = fetchAll("stream_out1");

        assertEquals(1, tlist.size());
        Tuple t0 = tlist.get(0);
        assertEquals(5, ((Event) t0.getColumn("event")).getSeqNumber());
    }

    @Test
    public void test4() throws Exception {
        createFeeder1();
        execute(
                "create stream stream_out1 as select event from stream_in where event.severity in (?) and event.message LIKE ? ",
                "WARNING", "%7%");

        List<Tuple> tlist = fetchAll("stream_out1");

        assertEquals(2, tlist.size());
        Tuple t0 = tlist.get(0);
        assertEquals(7, ((Event) t0.getColumn("event")).getSeqNumber());
        Tuple t1 = tlist.get(1);
        assertEquals(17, ((Event) t1.getColumn("event")).getSeqNumber());
    }
}
