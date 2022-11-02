package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

public class StreamSelect1Test extends YarchTestCase {

    final int n = 200;

    public void createFeeder1() throws YarchException {
        Stream s;
        final TupleDefinition tpdef = new TupleDefinition();
        tpdef.addColumn("x", DataType.INT);
        tpdef.addColumn("y", DataType.STRING);

        s = (new Stream(ydb, "stream_in", tpdef) {
            @Override
            public void doStart() {
                for (int i = 0; i < n; i++) {
                    Integer x = i;
                    String y = "s" + i;

                    Tuple t = new Tuple(tpdef, new Object[] { x, y });
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
    public void testLike() throws Exception {
        createFeeder1();

        execute("create stream stream_out1 as select * from stream_in where y like \'s1%\'");
        Stream s = ydb.getStream("stream_out1");
        final Semaphore finished = new Semaphore(0);
        final AtomicInteger counter = new AtomicInteger(0);
        s.addSubscriber(new StreamSubscriber() {
            @Override
            public void streamClosed(Stream stream) {
                finished.release();
            }

            int k = 1;

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                int x = (Integer) tuple.getColumn(0);
                String y = (String) tuple.getColumn(1);
                assertEquals(k, x);
                assertEquals("s" + k, y);
                counter.incrementAndGet();
                do {
                    k += 1;
                } while (!("s" + k).startsWith("s1"));
            }
        });
        s.start();
        assertTrue(finished.tryAcquire(5, TimeUnit.SECONDS));
        assertEquals(111, counter.get());
    }

    @Test
    public void testNotLike() throws Exception {
        createFeeder1();

        execute("create stream stream_out1 as select * from stream_in where y NOT LIKE \'s1%\'");
        Stream s = ydb.getStream("stream_out1");
        final Semaphore finished = new Semaphore(0);
        final AtomicInteger counter = new AtomicInteger(0);
        s.addSubscriber(new StreamSubscriber() {
            @Override
            public void streamClosed(Stream stream) {
                finished.release();
            }

            int k = 0;

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                int x = (Integer) tuple.getColumn(0);
                String y = (String) tuple.getColumn(1);
                assertEquals(k, x);
                assertEquals("s" + k, y);
                counter.incrementAndGet();
                do {
                    k++;
                } while (("s" + k).startsWith("s1"));
            }
        });
        s.start();
        assertTrue(finished.tryAcquire(5, TimeUnit.SECONDS));
        assertEquals(89, counter.get());
    }

    @Test
    public void testDoubleParanthesis() throws Exception {
        createFeeder1();
        execute("create stream stream_out1 as select * from stream_in where (x>3 and (x<5 or y = 's5'))");
        List<Tuple> tlist = fetchAll("stream_out1");
        assertEquals(2, tlist.size());
        assertEquals(4, tlist.get(0).getIntColumn("x"));
        assertEquals(5, tlist.get(1).getIntColumn("x"));
    }

    @Test
    public void testArgCols() throws Exception {
        createFeeder1();
        execute("create stream stream_out1 as select ?,? from stream_in", "x", "y");
        assertEquals(n, fetchAll("stream_out1").size());
    }
}
