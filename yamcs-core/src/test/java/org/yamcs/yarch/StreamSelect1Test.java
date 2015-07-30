package org.yamcs.yarch;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.yamcs.yarch.AbstractStream;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.streamsql.StreamSqlResult;

import static org.junit.Assert.*;

public class StreamSelect1Test extends YarchTestCase {
    StreamSqlResult res;
    final int n=200;

    public void createFeeder1() throws YarchException {
        AbstractStream s;
        YarchDatabase ydb=YarchDatabase.getInstance(context.getDbName());
        final TupleDefinition tpdef=new TupleDefinition();
        tpdef.addColumn("x", DataType.INT);
        tpdef.addColumn("y", DataType.STRING);

        s=(new AbstractStream(ydb,"stream_in",tpdef) {
            @Override
            public void start() {
                for (int i=0;i<n;i++) {
                    Integer x=i;
                    String y="s"+i;

                    Tuple t=new Tuple(tpdef, new Object[]{x,y});
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

        res=execute("create stream stream_out1 as select * from stream_in where y like \'s1%\'");
        YarchDatabase ydb=YarchDatabase.getInstance(context.getDbName());
        Stream s = ydb.getStream("stream_out1");
        final Semaphore finished=new Semaphore(0);
        final AtomicInteger counter = new AtomicInteger(0);
        s.addSubscriber(new StreamSubscriber() {
            @Override
            public void streamClosed(Stream stream) {
                finished.release();
            }

            int k=1;
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                int x=(Integer)tuple.getColumn(0);
                String y=(String)tuple.getColumn(1);
                assertEquals(k, x);
                assertEquals("s"+k, y);
                counter.incrementAndGet();
                do {
                    k+=1;
                } while(!("s"+k).startsWith("s1"));         
            }
        });
        s.start();
        assertTrue(finished.tryAcquire(5, TimeUnit.SECONDS));
        assertEquals(111, counter.get());
    }
    
    @Test
    public void testNotLike() throws Exception {
        createFeeder1();

        res=execute("create stream stream_out1 as select * from stream_in where y NOT LIKE \'s1%\'");
        YarchDatabase ydb=YarchDatabase.getInstance(context.getDbName());
        Stream s = ydb.getStream("stream_out1");
        final Semaphore finished=new Semaphore(0);
        final AtomicInteger counter = new AtomicInteger(0);
        s.addSubscriber(new StreamSubscriber() {
            @Override
            public void streamClosed(Stream stream) {
                finished.release();
            }

            int k=0;
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                int x=(Integer)tuple.getColumn(0);
                String y=(String)tuple.getColumn(1);
                assertEquals(k, x);
                assertEquals("s"+k, y);
                counter.incrementAndGet();
                do {
                    k++;
                } while(("s"+k).startsWith("s1"));         
            }
        });
        s.start();
        assertTrue(finished.tryAcquire(5, TimeUnit.SECONDS));
        assertEquals(89, counter.get());
    }
}
