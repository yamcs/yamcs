package org.yamcs.yarch;


import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


import org.junit.Test;
import org.yamcs.yarch.streamsql.StreamSqlResult;
import org.yamcs.yarch.streamsql.StreamSqlStatement;



import static org.junit.Assert.*;
public class HistogramStreamTest extends YarchTestCase {
    StreamSqlStatement statement;
    StreamSqlResult res;
    String cmd;
    int n=1000;
    int m=3;

    private void populate(String tblName) throws Exception {
        String query="create table "+tblName+"(gentime timestamp, seqNum int, name string, primary key(gentime, seqNum)) histogram(name) partition by time(gentime) table_format=compressed";
        ydb.execute(query);
        
        execute("create stream "+tblName+"_in(gentime timestamp, seqNum int, name string)");
        execute("insert into "+tblName+" select * from "+tblName+"_in");

        Stream s=ydb.getStream(tblName+"_in");
        TupleDefinition td=s.getDefinition();
        
        for (int i=0;i<n;i++) {
            for(int j=0;j<m;j++) {
                Tuple t=new Tuple(td, 
                        new Object[]{1000L*i+j, j, "histotest"+j});
                s.emitTuple(t);
            }
        }
        
        for (int i=2*n;i<3*n;i++) {
            for(int j=0;j<m;j++) {
                Tuple t=new Tuple(td, 
                        new Object[]{1000L*i+j, j, "histotest"+j});
                s.emitTuple(t);
            }
        }
        
        execute("close stream "+tblName+"_in");
    }

    @Test
    public void test1() throws Exception {
        populate("test1");
        String query="create stream test1_out as select * from test1 histogram(name) where last>"+(n*1000)+" and first<100000000";
        ydb.execute(query);
        final List<Tuple> tuples=suckAll("test1_out");
        assertEquals(m, tuples.size());
        Tuple t=tuples.get(0);
        //tuples should contain (name String, start TIMESTAMP, stop TIMESTAMP,  num int)
        assertEquals(4, t.size());
        assertEquals("histotest0", (String)t.getColumn(0));
        assertEquals(2*n*1000L, (long)(Long)t.getColumn(1));
        assertEquals((3*n-1)*1000L, (long)(Long)t.getColumn(2));
        assertEquals(n, (int)(Integer)t.getColumn(3));
        
        ydb.execute("drop table test1");
    }
    
    @Test
    public void test2() throws Exception {
        populate("test2");
        String query="create stream test2_out as select * from test2 histogram(name)";
        ydb.execute(query);
        Stream s=ydb.getStream("test2_out");
        final AtomicInteger count=new AtomicInteger();
        final Semaphore semaphore=new Semaphore(0);
        s.addSubscriber(new StreamSubscriber() {
            
            @Override
            public void streamClosed(Stream stream) {
                semaphore.release();
            }
            
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                if(count.incrementAndGet()==2) {
                    stream.close();
                    return;
                }
                if(count.get()>=2)                throw new RuntimeException();
                assertTrue(count.get()<2);
            }
        });
        s.start();
        assertTrue(semaphore.tryAcquire(5, TimeUnit.SECONDS));
        
        ydb.execute("drop table test2");
    }
    
    @Test
    public void test3() throws Exception {
        populate("test3");
        String query="create stream test3_out as select * from test3 histogram(name, "+((n+1)*1000)+1+")";
        ydb.execute(query);
        final List<Tuple> tuples=suckAll("test3_out");
        assertEquals(m, tuples.size());
        Tuple t=tuples.get(0);
        //tuples should contain (name String, start TIMESTAMP, stop TIMESTAMP,  num int)
        assertEquals(4, t.size());
        assertEquals("histotest0", (String)t.getColumn(0));
        assertEquals(0L, (long)(Long)t.getColumn(1));
        assertEquals((3*n-1)*1000L, (long)(Long)t.getColumn(2));
        assertEquals(2*n, (int)(Integer)t.getColumn(3));
        
        ydb.execute("drop table test3");
    }
  
    @Test
    public void testEmpyStream() throws Exception {
        populate("testEmptyStream");
        String query="create stream testEmptyStream_out as select * from testEmptyStream histogram(name) where last>0 and first<-1";
        ydb.execute(query);
        final List<Tuple> tuples=suckAll("testEmptyStream_out");
        assertEquals(0, tuples.size());
        
        
        String query1 = "create stream testEmptyStream_out1 as select * from testEmptyStream histogram(name) where last>76797379324836000";
        ydb.execute(query1);
        final List<Tuple> tuples1 = suckAll("testEmptyStream_out1");
        assertEquals(0, tuples1.size());
        
        
        String query2 = "create stream testEmptyStream_out2 as select * from testEmptyStream histogram(name) where first>76797379324836000";
        ydb.execute(query2);
        final List<Tuple> tuples2 = suckAll("testEmptyStream_out2");
        assertEquals(0, tuples2.size());
        
        
        ydb.execute("drop table testEmptyStream");
    }
}
