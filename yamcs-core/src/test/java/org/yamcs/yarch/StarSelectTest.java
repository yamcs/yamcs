package org.yamcs.yarch;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.yamcs.yarch.AbstractStream;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.streamsql.StreamSqlResult;

import static org.junit.Assert.*;


public class StarSelectTest extends YarchTestCase {
    StreamSqlResult res;
    int n = 10;

    void createFeeder() throws Exception {
        final TupleDefinition tpdef = new TupleDefinition();
        tpdef.addColumn("time", DataType.TIMESTAMP);
        tpdef.addColumn("id", DataType.INT);

        AbstractStream s = (new AbstractStream(ydb, "tm_in", tpdef) {
            @Override
            public void start() {
                for (int i = 0; i < n; i++) {
                    Long time = (long) (i * 1000);
                    Tuple t = new Tuple(tpdef, new Object[] { time, i });
                    emitTuple(t);
                }
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
        res = execute("create stream tm_out1 as select 3,* from tm_in");
        Stream s = ydb.getStream("tm_out1");
        final Semaphore finished = new Semaphore(0);
        s.addSubscriber(new StreamSubscriber() {
            int k = 0;
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                int const_three = (Integer) tuple.getColumn(0);
                assertEquals(const_three, 3);
                long time = (Long) tuple.getColumn(1);
                assertEquals(1000 * k, time);
                int i = (Integer) tuple.getColumn(2);
                //System.out.println("id: "+i+", time: "+time);
                assertEquals(k, i);
                k++;
                if (k >= n) {
                    finished.release();
                }
            }
            @Override
            public void streamClosed(Stream stream) {
            }

        });
        s.start();

        assertTrue(finished.tryAcquire(10, TimeUnit.SECONDS));
        execute("close stream tm_in");
    }
}
