package org.yamcs.yarch.tokyocabinet;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchTestCase;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.tokyocabinet.TcPartitionManager;
import org.yamcs.yarch.tokyocabinet.TcStorageEngine;

import org.yamcs.utils.TimeEncoding;

public class PartitioningTest extends YarchTestCase {

    @Test
    public void testIndexPartitioning() throws ParseException, StreamSqlException, IOException, InterruptedException {
        ydb.execute("create table test1(gentime timestamp, apidSeqCount int, primary key(gentime,apidSeqCount)) partition by time(gentime)");
        ydb.execute("create stream tm_in(gentime timestamp, apidSeqCount int)");
        ydb.execute("insert into test1 select * from tm_in");
        Stream tm_in=ydb.getStream("tm_in");
        
        long instant1=TimeEncoding.parse("1999-06-21T07:03:00");
        tm_in.emitTuple(new Tuple(tm_in.getDefinition(), new Object[]{instant1, 20000}));

        TableDefinition tdef=ydb.getTable("test1");
        TcStorageEngine storageEngine = (TcStorageEngine) ydb.getStorageEngine(tdef);
        
        assertTrue(tdef.hasPartitioning());
        TcPartitionManager pmgr= storageEngine.getPartitionManager(tdef);
        Collection<String> partitions=pmgr.getPartitions();
        assertEquals(1,partitions.size());
        assertEquals("1999/172/test1",partitions.iterator().next());
        File f=new File(YarchDatabase.getHome()+"/"+context.getDbName()+"/1999/172/test1.tcb");
        System.out.println("f="+f);
        assertTrue(f.exists());

        long instant2=TimeEncoding.parse("2001-01-01T00:00:00");
        tm_in.emitTuple(new Tuple(tm_in.getDefinition(), new Object[]{instant2, 2000}));
        partitions=pmgr.getPartitions();
        assertEquals(2,partitions.size());
        Iterator<String>it=partitions.iterator();
        assertEquals("1999/172/test1",it.next());
        assertEquals("2001/001/test1",it.next());

        long instant3=TimeEncoding.parse("2001-01-01T00:00:01");
        tm_in.emitTuple(new Tuple(tm_in.getDefinition(), new Object[]{instant3, 2000}));
        partitions=pmgr.getPartitions();
        assertEquals(2,partitions.size());
        it=partitions.iterator();
        assertEquals("1999/172/test1",it.next());
        assertEquals("2001/001/test1",it.next());

        long instant4=TimeEncoding.parse("2000-12-31T23:59:59");
        tm_in.emitTuple(new Tuple(tm_in.getDefinition(), new Object[]{instant4, 2000}));
        partitions=pmgr.getPartitions();
        assertEquals(3,partitions.size());
        it=partitions.iterator();
        assertEquals("1999/172/test1",it.next());
        assertEquals("2000/366/test1",it.next());
        assertEquals("2001/001/test1",it.next());

        
        long instant5=TimeEncoding.parse("2008-12-31T23:59:60");
        tm_in.emitTuple(new Tuple(tm_in.getDefinition(), new Object[]{instant5, 2000}));
        Thread.sleep(100);//give time to the other thread to finish reading the input
        partitions=pmgr.getPartitions();
        assertEquals(4,partitions.size());
        it=partitions.iterator();
        assertEquals("1999/172/test1",it.next());
        assertEquals("2000/366/test1",it.next());
        assertEquals("2001/001/test1",it.next());
        assertEquals("2008/366/test1",it.next());
        ydb.execute("close stream tm_in");



        ydb.execute("create stream test1_out as select * from test1");
        Stream test1_out=ydb.getStream("test1_out");
        List<Tuple> tuples=suckAll("test1_out");
        assertEquals(5, tuples.size());
        Iterator<Tuple> iter=tuples.iterator();
        
        assertEquals(instant1, (long)(Long)iter.next().getColumn(0));
        assertEquals(instant4, (long)(Long)iter.next().getColumn(0));
        assertEquals(instant2, (long)(Long)iter.next().getColumn(0));
        assertEquals(instant3, (long)(Long)iter.next().getColumn(0));
        assertEquals(instant5, (long)(Long)iter.next().getColumn(0));

        execute("drop table test1");
        assertFalse((new File(YarchDatabase.getHome()+"/1999/172/test1.tcb")).exists());
        assertFalse((new File(YarchDatabase.getHome()+"/2001/001/test1.tcb")).exists());
        assertFalse((new File(YarchDatabase.getHome()+"/2000/365/test1.tcb")).exists());

    }

    private void doublePartitioningSelect(String whereCnd, final long[] expectedInstant) throws InterruptedException, StreamSqlException, ParseException{

        String query="create stream testdp_out as select * from testdp"+(whereCnd==null?"":" where "+whereCnd);
        ydb.execute(query);
        Stream test1_out=ydb.getStream("testdp_out");
        final Semaphore semaphore=new Semaphore(0);
        final AtomicInteger c=new AtomicInteger(0);
        test1_out.addSubscriber(new StreamSubscriber() {

            @Override
            public void streamClosed(Stream stream) {
                semaphore.release();
            }
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                long inst=(Long)tuple.getColumn("gentime");
                assertEquals(expectedInstant[c.getAndIncrement()], inst);
            }
        });
        test1_out.start();
        assertTrue(semaphore.tryAcquire(10, TimeUnit.SECONDS));
        assertEquals(expectedInstant.length, c.get());
    }

    /**
     * Tests partitioning by time and string value
     * @throws ParseException
     * @throws StreamSqlException
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void testDoublePartitioning() throws ParseException, StreamSqlException, IOException, InterruptedException {
        final long[] instant=new long[4];
        ydb.execute("create table testdp(gentime timestamp, seqNumber int, part String, packet binary, primary key(gentime,seqNumber)) partition by time_and_value(gentime,part)");
        ydb.execute("create stream tm_in(gentime timestamp, seqNumber int, part String, packet binary)");
        ydb.execute("insert into testdp select * from tm_in");
        Stream tm_in=ydb.getStream("tm_in");
        TupleDefinition tpdef=tm_in.getDefinition();

        instant[0]=TimeEncoding.parse("1999-06-21T07:03:00");
        Tuple t11=new Tuple(tpdef, new Object[] {instant[0], 1, "part1", new byte[1000]});
        tm_in.emitTuple(t11);

        instant[1]=instant[0];
        Tuple t12=new Tuple(tpdef, new Object[] {instant[1], 2, "partition2", new byte[1000]});
        tm_in.emitTuple(t12);

        instant[2]=TimeEncoding.parse("1999-06-21T07:03:01");;
        Tuple t2=new Tuple(tpdef, new Object[] {instant[2], 3, "partition2", new byte[1000]});
        tm_in.emitTuple(t2);

        TableDefinition tdef=ydb.getTable("testdp");
        assertTrue(tdef.hasPartitioning());
        
        TcStorageEngine storageEngine = (TcStorageEngine) ydb.getStorageEngine(tdef);
       
        assertTrue(tdef.hasPartitioning());
        TcPartitionManager pmgr= storageEngine.getPartitionManager(tdef);
        
        Collection<String> partitions=pmgr.getPartitions();
        Iterator<String>it=partitions.iterator();
        assertEquals(2,partitions.size());
        assertEquals("1999/172/testdp#part1",it.next());
        assertEquals("1999/172/testdp#partition2",it.next());
        File f=new File(YarchDatabase.getHome()+"/"+context.getDbName()+"/1999/172/testdp#part1.tcb");
        assertTrue(f.exists());
        f=new File(YarchDatabase.getHome()+"/"+context.getDbName()+"/1999/172/testdp#partition2.tcb");
        assertTrue(f.exists());

        instant[3]=TimeEncoding.parse("2001-01-01T00:00:00");
        Tuple t3=new Tuple(tpdef, new Object[] {instant[3], 4, "part3", new byte[1000]});
        tm_in.emitTuple(t3);
        partitions=pmgr.getPartitions();
        assertEquals(3,partitions.size());
        it=partitions.iterator();
        assertEquals("1999/172/testdp#part1",it.next());
        assertEquals("1999/172/testdp#partition2",it.next());
        assertEquals("2001/001/testdp#part3",it.next());

        ydb.execute("close stream tm_in");


        doublePartitioningSelect(null, instant);

        doublePartitioningSelect("part='partition2'", new long[]{instant[1], instant[2]});

        doublePartitioningSelect("part='partition2' and gentime>"+instant[1], new long[]{instant[2]});

        doublePartitioningSelect("part in ('part1','part3') and gentime<="+instant[3], new long[]{instant[0], instant[3]});

        doublePartitioningSelect("part='partition2' and part='part3' and gentime>"+instant[1], new long[]{});
        execute("drop table testdp");
        assertFalse((new File(YarchDatabase.getHome()+"1999/172/testdp#part1.tcb")).exists());
        assertFalse((new File(YarchDatabase.getHome()+"1999/172/testdp#partition2.tcb")).exists());
        assertFalse((new File(YarchDatabase.getHome()+"2001/001/testdp#part3.tcb")).exists());
    }	   









    /**
     * Tests partitioning by enum value
     * @throws ParseException
     * @throws StreamSqlException
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void testEnumPartitioning() throws ParseException, StreamSqlException, IOException, InterruptedException {
        final long[] instant=new long[4];
        ydb.execute("create table testdp(gentime timestamp, seqNumber int, part enum, packet binary, primary key(gentime,seqNumber)) partition by value(part)");
        ydb.execute("create stream tm_in(gentime timestamp, seqNumber int, part enum, packet binary)");
        ydb.execute("insert into testdp select * from tm_in");
        Stream tm_in=ydb.getStream("tm_in");
        TupleDefinition tpdef=tm_in.getDefinition();

        instant[0]=TimeEncoding.parse("1999-06-21T07:03:00");
        Tuple t11=new Tuple(tpdef, new Object[] {instant[0], 1, "part0", new byte[1000]});
        tm_in.emitTuple(t11);

        instant[1]=instant[0];
        Tuple t12=new Tuple(tpdef, new Object[] {instant[1], 2, "partition1", new byte[1000]});
        tm_in.emitTuple(t12);

        instant[2]=TimeEncoding.parse("1999-06-21T07:03:01");;
        Tuple t2=new Tuple(tpdef, new Object[] {instant[2], 3, "partition1", new byte[1000]});
        tm_in.emitTuple(t2);

        TableDefinition tdef=ydb.getTable("testdp");
        assertTrue(tdef.hasPartitioning());
        TcStorageEngine storageEngine = (TcStorageEngine) ydb.getStorageEngine(tdef);
        
        TcPartitionManager pmgr= storageEngine.getPartitionManager(tdef);

        Collection<String> partitions=pmgr.getPartitions();
        Iterator<String>it=partitions.iterator();
        assertEquals(2, partitions.size());
        assertEquals("testdp#0", it.next());
        assertEquals("testdp#1", it.next());
        File f=new File(YarchDatabase.getHome()+"/"+ydb.getName()+"/testdp#0.tcb");
        assertTrue(f.exists());
        f=new File(YarchDatabase.getHome()+"/"+ydb.getName()+"/testdp#1.tcb");
        assertTrue(f.exists());

        instant[3]=TimeEncoding.parse("2001-01-01T00:00:00");
        Tuple t3=new Tuple(tpdef, new Object[] {instant[3], 4, "part2", new byte[1000]});
        tm_in.emitTuple(t3);
        partitions=pmgr.getPartitions();
        assertEquals(3, partitions.size());
        System.out.println("partitions: "+partitions);
        it=partitions.iterator();

        assertTrue(partitions.contains("testdp#0"));
        assertTrue(partitions.contains("testdp#1"));
        assertTrue(partitions.contains("testdp#2"));

        ydb.execute("close stream tm_in");


        doublePartitioningSelect(null, instant);

        doublePartitioningSelect("part='partition1'", new long[]{instant[1], instant[2]});

        doublePartitioningSelect("part='partition1' and gentime>"+instant[1], new long[]{instant[2]});

        doublePartitioningSelect("part in ('part0','part2') and gentime<="+instant[3], new long[]{instant[0], instant[3]});

        doublePartitioningSelect("part='partition2' and part='part3' and gentime>"+instant[1], new long[]{});
        execute("drop table testdp");
        assertFalse((new File(YarchDatabase.getHome()+"/"+ydb.getName()+"/testdp#0.tcb")).exists());
        assertFalse((new File(YarchDatabase.getHome()+"/"+ydb.getName()+"/testdp#1.tcb")).exists());
        assertFalse((new File(YarchDatabase.getHome()+"/"+ydb.getName()+"/testdp#2.tcb")).exists());
    }      
}