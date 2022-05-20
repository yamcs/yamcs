package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.yamcs.yarch.streamsql.StreamSqlResult;

public class TableRowDeleteTest extends YarchTestCase {
    Random random = new Random();

    private void populate(String tblName, int start, int stop, int step, int apidSeqCount) throws Exception {
        execute("create table " + tblName
                + "(\"time\" timestamp, apidSeqCount int, pname enum, packet binary, primary key(\"time\", apidSeqCount))");
        execute("create stream tm_in(\"time\" timestamp, apidSeqCount int, pname enum, packet binary)");
        execute("insert into " + tblName + " select * from tm_in");
        Stream s = ydb.getStream("tm_in");

        ByteBuffer bb = ByteBuffer.allocate(2000);

        for (int i = start; i < stop; i += step) {
            bb.position(0);
            long time = 1000 * i;
            random.nextBytes(bb.array());
            Tuple t = new Tuple(s.getDefinition(),
                    new Object[] { time, apidSeqCount, "packet" + (i % 10), bb.array() });
            s.emitTuple(t);
        }
        execute("close stream tm_in");
    }

    private void verify(String streamQuery, final Checker c, int numRows) throws Exception {
        execute("create stream tm_out as " + streamQuery);

        final AtomicInteger ai = new AtomicInteger(0);
        final Semaphore semaphore = new Semaphore(0);

        Stream s = ydb.getStream("tm_out");
        s.addSubscriber(new StreamSubscriber() {
            @Override
            public void streamClosed(Stream stream) {
                semaphore.release();
            }

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                // System.out.println("got tuple: "+tuple);
                int i = ai.getAndIncrement();
                long time = (Long) tuple.getColumn(0);
                int apidSeqCount = (Integer) tuple.getColumn(1);
                String pname = (String) tuple.getColumn(2);
                byte[] b = (byte[]) tuple.getColumn(3);

                c.check(i, time, apidSeqCount, pname);
                assertEquals(b.length, 2000);
            }
        });
        s.start();
        semaphore.tryAcquire(30, TimeUnit.SECONDS);
        assertEquals(numRows, ai.get());
    }

    @Test
    public void testDeleteAll() throws Exception {
        populate("tm1", 0, 1000, 1, 1000);
        execute("delete from tm1");

        verify("select * from tm1",
                (i, time, apidSeqCount, pname) -> {
                    fail();
                }, 0);
        execute("drop table tm1");
    }

    @Test
    public void testDeleteWithIndexCondition1() throws Exception {
        populate("tm2", 0, 1000, 1, 1000);

        execute("delete from tm2 where \"time\" < 100000");
        // Thread.sleep(1000);

        verify("select * from tm2",
                (i, time, apidSeqCount, pname) -> {
                    assertTrue(time >= 100000);
                }, 900);
        execute("drop table tm2");
    }

    @Test
    public void testDeleteWithIndexCondition2() throws Exception {
        populate("tm3", 0, 1000, 1, 1000);
        StreamSqlResult result = ydb.execute("delete from tm3 where \"time\" >= 100000");
        Tuple t = result.next();
        assertEquals(900l, t.getLongColumn("deleted"));

        verify("select * from tm3",
                (i, time, apidSeqCount, pname) -> {
                    assertTrue(time <= 100000);
                }, 100);
        execute("drop table tm3");
    }

    @Test
    public void testDeleteWithIndexCondition3() throws Exception {
        populate("tm4", 0, 1000, 1, 1000);
        execute("delete from tm4 where \"time\" = 1000");

        verify("select * from tm4",
                (i, time, apidSeqCount, pname) -> {
                    assertTrue(time != 1000);
                }, 999);
        execute("drop table tm4");
    }

    @Test
    public void testDeleteWithFilter() throws Exception {
        populate("tmf1", 0, 1000, 1, 1000);
        execute("delete from tmf1 where pname = 'packet1'");

        verify("select * from tmf1",
                (i, time, apidSeqCount, pname) -> {
                    assertTrue(!pname.equals("packet1"));
                }, 900);

        execute("drop table tmf1");
    }

    @Test
    public void testDeleteWithFilter2() throws Exception {
        populate("tmf2", 0, 1000, 1, 1000);
        execute("delete from tmf2 where pname = 'packet1' limit 3");
        AtomicInteger pkt1count = new AtomicInteger();

        verify("select * from tmf2",
                (i, time, apidSeqCount, pname) -> {
                    if (pname.equals("packet1")) {
                        pkt1count.incrementAndGet();
                    }
                }, 997);
        assertEquals(97, pkt1count.get());

        execute("drop table tmf2");
    }

    interface Checker {
        public void check(int i, long time, int apidSeqCount, String pname);
    }
}
