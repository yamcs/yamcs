package org.yamcs.yarch.rocksdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchTestCase;
import org.yamcs.yarch.streamsql.StreamSqlException;

public class PartitioningTest extends YarchTestCase {

    @Test
    public void testIndexPartitioning() throws Exception {
        execute("create table test1(gentime timestamp, apidSeqCount int, primary key(gentime,apidSeqCount)) partition by time(gentime('YYYY/DOY'))");
        execute("create stream tm_in(gentime timestamp, apidSeqCount int)");
        execute("insert into test1 select * from tm_in");
        Stream tm_in = ydb.getStream("tm_in");

        long instant1 = TimeEncoding.parse("1999-06-21T07:03:00");
        tm_in.emitTuple(new Tuple(tm_in.getDefinition(), new Object[] { instant1, 20000 }));

        TableDefinition tdef = ydb.getTable("test1");
        RdbStorageEngine storageEngine = (RdbStorageEngine) ydb.getStorageEngine(tdef);
        Tablespace tablespace = storageEngine.getTablespace(instance);

        assertTrue(tdef.hasPartitioning());
        RdbPartitionManager pmgr = storageEngine.getPartitionManager(ydb, tdef);
        List<Partition> partitions = pmgr.getPartitions();
        assertEquals(1, partitions.size());
        RdbPartition p1 = (RdbPartition) partitions.iterator().next();
        assertEquals("1999/172", p1.dir);
        File f = new File(tablespace.getDataDir() + "/" + p1.dir);
        assertTrue(f.exists());

        long instant2 = TimeEncoding.parse("2001-01-01T00:00:00");
        tm_in.emitTuple(new Tuple(tm_in.getDefinition(), new Object[] { instant2, 2000 }));
        partitions = pmgr.getPartitions();
        assertEquals(2, partitions.size());
        Iterator<Partition> it = partitions.iterator();
        p1 = (RdbPartition) it.next();
        assertEquals("1999/172", p1.dir);
        RdbPartition p2 = (RdbPartition) it.next();
        assertEquals("2001/001", p2.dir);

        long instant3 = TimeEncoding.parse("2001-01-01T00:00:01");
        tm_in.emitTuple(new Tuple(tm_in.getDefinition(), new Object[] { instant3, 2000 }));
        partitions = pmgr.getPartitions();
        assertEquals(2, partitions.size());
        it = partitions.iterator();
        p1 = (RdbPartition) it.next();
        assertEquals("1999/172", p1.dir);
        p2 = (RdbPartition) it.next();
        assertEquals("2001/001", p2.dir);

        long instant4 = TimeEncoding.parse("2000-12-31T23:59:59");
        tm_in.emitTuple(new Tuple(tm_in.getDefinition(), new Object[] { instant4, 2000 }));
        partitions = pmgr.getPartitions();
        assertEquals(3, partitions.size());
        it = partitions.iterator();
        p1 = (RdbPartition) it.next();
        assertEquals("1999/172", p1.dir);
        p2 = (RdbPartition) it.next();
        assertEquals("2000/366", p2.dir);
        RdbPartition p3 = (RdbPartition) it.next();
        assertEquals("2001/001", p3.dir);

        long instant5 = TimeEncoding.parse("2008-12-31T23:59:60");
        tm_in.emitTuple(new Tuple(tm_in.getDefinition(), new Object[] { instant5, 2000 }));
        Thread.sleep(100);// give time to the other thread to finish reading the input
        partitions = pmgr.getPartitions();
        assertEquals(4, partitions.size());
        it = partitions.iterator();
        p1 = (RdbPartition) it.next();
        assertEquals("1999/172", p1.dir);
        p2 = (RdbPartition) it.next();
        assertEquals("2000/366", p2.dir);
        p3 = (RdbPartition) it.next();
        assertEquals("2001/001", p3.dir);
        RdbPartition p4 = (RdbPartition) it.next();
        assertEquals("2008/366", p4.dir);
        execute("close stream tm_in");

        execute("create stream test1_out as select * from test1");

        List<Tuple> tuples = fetchAll("test1_out");
        assertEquals(5, tuples.size());
        Iterator<Tuple> iter = tuples.iterator();

        assertEquals(instant1, (long) (Long) iter.next().getColumn(0));
        assertEquals(instant4, (long) (Long) iter.next().getColumn(0));
        assertEquals(instant2, (long) (Long) iter.next().getColumn(0));
        assertEquals(instant3, (long) (Long) iter.next().getColumn(0));
        assertEquals(instant5, (long) (Long) iter.next().getColumn(0));

        assertTrue((new File(tablespace.getDataDir() + "/1999/172/")).exists());
        assertTrue((new File(tablespace.getDataDir() + "/2001/001/")).exists());
        assertTrue((new File(tablespace.getDataDir() + "/2000/366/")).exists());

        execute("drop table test1");
        // for the new rocksdb storage engine we don't want the partitions to be removed because they may be shared by
        // other tables
        // we should check that no data is inside though
        assertTrue((new File(tablespace.getDataDir() + "/1999/172/")).exists());
        assertTrue((new File(tablespace.getDataDir() + "/2001/001/")).exists());
        assertTrue((new File(tablespace.getDataDir() + "/2000/366/")).exists());
        RDBFactory rdbFactory = tablespace.rdbFactory;
        checkEmpty(rdbFactory.getRdb("1999/172", true));
        checkEmpty(rdbFactory.getRdb("2001/001", true));
        checkEmpty(rdbFactory.getRdb("2000/366", true));
    }

    private void checkEmpty(YRDB db) throws RocksDBException {
        try (RocksIterator it1 = db.newIterator()) {
            it1.seekToFirst();
            assertFalse(it1.isValid());
        }
    }

    private void doublePartitioningSelect(String whereCnd, final long[] expectedInstant)
            throws InterruptedException, StreamSqlException, ParseException {
        String query = "create stream testdp_out as select * from testdp"
                + (whereCnd == null ? "" : " where " + whereCnd);
        execute(query);
        Stream test1_out = ydb.getStream("testdp_out");
        final Semaphore semaphore = new Semaphore(0);
        final AtomicInteger c = new AtomicInteger(0);
        test1_out.addSubscriber(new StreamSubscriber() {

            @Override
            public void streamClosed(Stream stream) {
                semaphore.release();
            }

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                long inst = (Long) tuple.getColumn("gentime");
                assertEquals(expectedInstant[c.getAndIncrement()], inst);
            }
        });
        test1_out.start();
        assertTrue(semaphore.tryAcquire(10, TimeUnit.SECONDS));
        assertEquals(expectedInstant.length, c.get());
    }

    /**
     * Tests partitioning by time and string value
     *
     * @throws ParseException
     * @throws StreamSqlException
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void testDoublePartitioning() throws Exception {
        final long[] instant = new long[4];
        ydb.execute(
                "create table testdp(gentime timestamp, seqNumber int, part enum, packet binary, primary key(gentime,seqNumber)) partition by time_and_value(gentime('YYYY/DOY'), part)");
        ydb.execute("create stream tm_in(gentime timestamp, seqNumber int, part enum, packet binary)");
        ydb.execute("insert into testdp select * from tm_in");
        Stream tm_in = ydb.getStream("tm_in");
        TupleDefinition tpdef = tm_in.getDefinition();
        Tablespace tablespace = RdbStorageEngine.getInstance().getTablespace(instance);

        instant[0] = TimeEncoding.parse("1999-06-21T07:03:00");
        Tuple t11 = new Tuple(tpdef, new Object[] { instant[0], 1, "part1", new byte[1000] });
        tm_in.emitTuple(t11);

        instant[1] = instant[0];
        Tuple t12 = new Tuple(tpdef, new Object[] { instant[1], 2, "partition2", new byte[1000] });
        tm_in.emitTuple(t12);

        instant[2] = TimeEncoding.parse("1999-06-21T07:03:01");
        ;
        Tuple t2 = new Tuple(tpdef, new Object[] { instant[2], 3, "partition2", new byte[1000] });
        tm_in.emitTuple(t2);

        TableDefinition tdef = ydb.getTable("testdp");
        assertTrue(tdef.hasPartitioning());

        RdbStorageEngine storageEngine = (RdbStorageEngine) ydb.getStorageEngine(tdef);

        assertTrue(tdef.hasPartitioning());
        RdbPartitionManager pmgr = storageEngine.getPartitionManager(ydb, tdef);

        List<Partition> partitions = pmgr.getPartitions();
        Iterator<Partition> it = partitions.iterator();
        assertEquals(2, partitions.size());
        RdbPartition p1 = (RdbPartition) it.next();
        assertEquals("1999/172", p1.dir);

        RdbPartition p2 = (RdbPartition) it.next();
        assertEquals("1999/172", p2.dir);

        Object[] pvalues = new Object[] { p1.getValue(), p2.getValue() };
        Arrays.sort(pvalues);

        assertEquals((short) 0, pvalues[0]);
        assertEquals((short) 1, pvalues[1]);

        File f = new File(tablespace.getDataDir() + "/1999/172");
        assertTrue(f.exists());

        instant[3] = TimeEncoding.parse("2001-01-01T00:00:00");
        Tuple t3 = new Tuple(tpdef, new Object[] { instant[3], 4, "part3", new byte[1000] });
        tm_in.emitTuple(t3);
        partitions = pmgr.getPartitions();
        assertEquals(3, partitions.size());
        it = partitions.iterator();
        it.next();
        it.next();
        RdbPartition p3 = (RdbPartition) it.next();

        assertEquals("2001/001", p3.dir);
        assertEquals((short) 2, p3.getValue());

        execute("close stream tm_in");

        doublePartitioningSelect(null, instant);

        doublePartitioningSelect("part='partition2'", new long[] { instant[1], instant[2] });

        doublePartitioningSelect("part='partition2' and gentime>" + instant[1], new long[] { instant[2] });

        doublePartitioningSelect("part in ('part1','part3') and gentime<=" + instant[3],
                new long[] { instant[0], instant[3] });

        doublePartitioningSelect("part='partition2' and part='part3' and gentime>" + instant[1], new long[] {});

        assertTrue((new File(tablespace.getDataDir() + "/1999/172")).exists());
        assertTrue((new File(tablespace.getDataDir() + "/2001/001")).exists());

        execute("drop table testdp");
        assertTrue((new File(tablespace.getDataDir() + "/1999/172")).exists());
        assertTrue((new File(tablespace.getDataDir() + "/2001/001")).exists());

        checkEmpty(tablespace.rdbFactory.getRdb("1999/172", true));
        checkEmpty(tablespace.rdbFactory.getRdb("2001/001", true));
    }

    /**
     * Tests partitioning by enum value
     *
     * @throws ParseException
     * @throws StreamSqlException
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void testEnumPartitioning() throws ParseException, StreamSqlException, IOException, InterruptedException {
        final long[] instant = new long[4];
        execute("create table testdp(gentime timestamp, seqNumber int, part enum, packet binary, primary key(gentime,seqNumber)) engine rocksdb2 partition by value(part)");
        execute("create stream tm_in(gentime timestamp, seqNumber int, part enum, packet binary)");
        execute("insert into testdp select * from tm_in");
        Stream tm_in = ydb.getStream("tm_in");
        TupleDefinition tpdef = tm_in.getDefinition();

        instant[0] = TimeEncoding.parse("1999-06-21T07:03:00");
        Tuple t11 = new Tuple(tpdef, new Object[] { instant[0], 1, "part0", new byte[1000] });
        tm_in.emitTuple(t11);

        instant[1] = instant[0];
        Tuple t12 = new Tuple(tpdef, new Object[] { instant[1], 2, "partition1", new byte[1000] });
        tm_in.emitTuple(t12);

        instant[2] = TimeEncoding.parse("1999-06-21T07:03:01");
        ;
        Tuple t2 = new Tuple(tpdef, new Object[] { instant[2], 3, "partition1", new byte[1000] });
        tm_in.emitTuple(t2);

        TableDefinition tdef = ydb.getTable("testdp");
        assertTrue(tdef.hasPartitioning());
        RdbStorageEngine storageEngine = (RdbStorageEngine) ydb.getStorageEngine(tdef);

        RdbPartitionManager pmgr = storageEngine.getPartitionManager(ydb, tdef);

        Collection<Partition> partitions = pmgr.getPartitions();
        Iterator<Partition> it = partitions.iterator();
        assertEquals(2, partitions.size());
        RdbPartition p1 = (RdbPartition) it.next();
        assertNull(p1.dir);
        assertEquals((short) 0, p1.getValue());

        RdbPartition p2 = (RdbPartition) it.next();
        assertNull(p2.dir);
        assertEquals((short) 1, p2.getValue());

        instant[3] = TimeEncoding.parse("2001-01-01T00:00:00");
        Tuple t3 = new Tuple(tpdef, new Object[] { instant[3], 4, "part2", new byte[1000] });
        tm_in.emitTuple(t3);
        partitions = pmgr.getPartitions();
        assertEquals(3, partitions.size());
        it = partitions.iterator();
        p1 = (RdbPartition) it.next();
        assertNull(p1.dir);
        assertEquals((short) 0, p1.getValue());

        p2 = (RdbPartition) it.next();
        RdbPartition p3 = (RdbPartition) it.next();
        assertNull(p3.dir);

        short[] pvalues = new short[] { (Short) p2.getValue(), (Short) p3.getValue() };
        Arrays.sort(pvalues);
        assertEquals((short) 1, pvalues[0]);

        assertEquals((short) 2, pvalues[1]);

        execute("close stream tm_in");

        // doublePartitioningSelect(null, instant);

        // doublePartitioningSelect("part='partition1'", new long[]{instant[1], instant[2]});

        doublePartitioningSelect("part='partition1' and gentime>" + instant[1], new long[] { instant[2] });

        doublePartitioningSelect("part in ('part0','part2') and gentime<=" + instant[3],
                new long[] { instant[0], instant[3] });

        doublePartitioningSelect("part='partition2' and part='part3' and gentime>" + instant[1], new long[] {});
        execute("drop table testdp");
    }
}
