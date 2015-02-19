package org.yamcs.yarch;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.yamcs.yarch.streamsql.StreamSqlResult;
import org.yamcs.yarch.streamsql.StreamSqlStatement;



public class StreamMergeTest extends YarchTestCase {
    StreamSqlStatement statement;
    StreamSqlResult res;
    String cmd;


    private void populate(String tblName, int start, int stop, int step, int apidSeqCount) throws Exception {
        ydb.execute("create table "+tblName+"(\"time\" timestamp, apidSeqCount int, packet binary, primary key(\"time\", apidSeqCount))");
        ydb.execute("create stream tm_in(\"time\" timestamp, apidSeqCount int, packet binary)");
        ydb.execute("insert into "+tblName+" select * from tm_in");
        Stream s=ydb.getStream("tm_in");

        ByteBuffer bb=ByteBuffer.allocate(2000);

        for (int i=start;i<stop;i+=step) {
            bb.position(0);
            long time=1000*i;
            while(bb.remaining()>0) bb.putInt(i);
            Tuple t=new Tuple(s.getDefinition(), new Object[]{time, apidSeqCount, bb.array()});
            s.emitTuple(t);
        }
        res=execute("close stream tm_in");
    }

    private void verify(String streamQuery, final Checker c) throws Exception {
        ydb.execute("create stream tm_out as "+streamQuery);

        final AtomicInteger ai=new AtomicInteger(0);
        final Semaphore semaphore=new Semaphore(0);

        Stream s=ydb.getStream("tm_out");
        s.addSubscriber(new StreamSubscriber() {
            @Override
            public void streamClosed(Stream stream) {
                semaphore.release();
            }
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
              //  System.out.println("got tuple: "+tuple);
                int i=ai.getAndIncrement();
                long time=(Long)tuple.getColumn(0);
                int apidSeqCount=(Integer)tuple.getColumn(1);
                byte[]b=(byte[]) tuple.getColumn(2);

                c.check(i, time, apidSeqCount);

                assertEquals(b.length,2000);
                ByteBuffer bb=ByteBuffer.wrap(b);
                while(bb.remaining()>0) {
                    int k=bb.getInt();
                    assertEquals(k, i);
                }
            }
        });
        s.start();
        semaphore.tryAcquire(30, TimeUnit.SECONDS);
        assertEquals(1000, ai.get());
    }

    @Test
    public void testTableMerge0() throws Exception {
        populate("tm1",0,1000,1,1000);

        verify("merge tm1 using \"time\"", 
                new Checker() {
            @Override
            public void check(int i, long time, int apidSeqCount) {
                assertEquals(1000*i,time);
                assertEquals(1000,apidSeqCount);
            }
        });
        execute("drop table tm1");
    }

    @Test   
    public void testTableMerge1() throws Exception {
        populate("tm1", 0, 1000, 2, 1000);
        populate("tm2", 1, 1000, 4, 2000);
        populate("tm3", 3, 1000, 4, 3000);

        verify("merge tm1,tm2,tm3 using \"time\"",
                new Checker() {
            @Override
            public void check(int i, long time, int apidSeqCount) {
                assertEquals(1000*i,time);
                if(i%2==0) {
                    assertEquals(1000,apidSeqCount);
                } else if(i%4==1) {
                    assertEquals(2000,apidSeqCount);
                } else {
                    assertEquals(3000,apidSeqCount);
                }
            }
        });
        execute("drop table tm1");
        execute("drop table tm2");
        execute("drop table tm3");
    }

    @Test
    public void testTableMerge2() throws Exception {
        populate("tm1",0,200,1,1000);
        populate("tm2",200,500,1,2000);
        populate("tm3",500,1000,1,3000);

        verify("merge tm1,tm2,tm3 using \"time\"",
                new Checker() {
            @Override
            public void check(int i, long time, int apidSeqCount) {
                assertEquals(1000*i,time);
                if(i<200) {
                    assertEquals(1000,apidSeqCount);
                } else if(i<500) {
                    assertEquals(2000,apidSeqCount);
                } else {
                    assertEquals(3000,apidSeqCount);
                }
            }
        });
        execute("drop table tm1");
        execute("drop table tm2");
        execute("drop table tm3");
    }

    @Test
    public void testTableMerge3() throws Exception {
        populate("tm1",0,1000,1,1000);
        populate("tm2",0,1000,1,2000);
        populate("tm3",0,1000,1,3000);

        verify("merge (select * from tm1 where \"time\"<199999+1), (select * from tm2 where \"time\">=200000 and \"time\"<500000), (select * from tm3 where \"time\">=500000) using \"time\"",
                new Checker() {
            @Override
            public void check(int i, long time, int apidSeqCount) {
                assertEquals(1000*i,time);
                if(i<200) {
                    assertEquals(1000,apidSeqCount);
                } else if(i<500) {
                    assertEquals(2000,apidSeqCount);
                } else {
                    assertEquals(3000,apidSeqCount);
                }
            }
        });
        execute("drop table tm1");
        execute("drop table tm2");
        execute("drop table tm3");
    }


    interface Checker {
        public void check(int i, long time, int apidSeqCount);
    }
}