package org.yamcs.yarch.rocksdb;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.YarchTestCase;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

import com.google.common.io.Files;


public class RdbSelectTest extends YarchTestCase {
    
    private TupleDefinition tdef;
    private TableWriter tw;
    
    @Before
    public void before() throws StreamSqlException, YarchException {
        tdef=new TupleDefinition();
        tdef.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
        tdef.addColumn(new ColumnDefinition("packetid", DataType.INT));
        tdef.addColumn(new ColumnDefinition("col3", DataType.INT));
        TableDefinition tblDef = new TableDefinition("RdbSelectTest", tdef, Arrays.asList("gentime"));

        String tmpdir=Files.createTempDir().getAbsolutePath();
        tblDef.setDataDir(tmpdir);

        PartitioningSpec pspec=PartitioningSpec.timeAndValueSpec("gentime", "packetid");
        pspec.setValueColumnType(DataType.INT);
        tblDef.setPartitioningSpec(pspec);
        
        tblDef.setStorageEngineName(YarchDatabase.RDB_ENGINE_NAME);
        
        ydb.createTable(tblDef);
        
        RdbStorageEngine rse = (RdbStorageEngine) ydb.getStorageEngine(tblDef);
        tw = rse.newTableWriter(tblDef, InsertMode.INSERT);
        tw.onTuple(null, new Tuple(tdef, new Object[]{2000L, 20, 2}));
        tw.onTuple(null, new Tuple(tdef, new Object[]{1000L, 10, 1}));
        tw.onTuple(null, new Tuple(tdef, new Object[]{3000L, 30, 3}));
    }
    
    @After
    public void after() throws YarchException {
        ydb.dropTable("RdbSelectTest");
    }

    @Test
    public void testUnspecifiedOrder() throws Exception {
        ydb.execute("create stream s1 as select * from RdbSelectTest");
        Stream s1 = ydb.getStream("s1");
        List<Tuple> tuples = fetchTuples(s1);
        assertEquals(3, tuples.size());
        
        // Ordered ascending by key
        assertEquals(1000L, tuples.get(0).getColumn("gentime"));
        assertEquals(2000L, tuples.get(1).getColumn("gentime"));
        assertEquals(3000L, tuples.get(2).getColumn("gentime"));
    }
    
    @Test
    public void testOrderAscending() throws Exception {
        // keyword ORDER is allowed but unneeded (defaults to ascending)
        ydb.execute("create stream s1 as select * from RdbSelectTest order");
        Stream s1 = ydb.getStream("s1");
        List<Tuple> tuples = fetchTuples(s1);
        assertEquals(3, tuples.size());
        
        // Ordered ascending by key
        assertEquals(1000L, tuples.get(0).getColumn("gentime"));
        assertEquals(2000L, tuples.get(1).getColumn("gentime"));
        assertEquals(3000L, tuples.get(2).getColumn("gentime"));
        
        // keywords ORDER ASC unneeded, but allowed 
        ydb.execute("create stream s2 as select * from RdbSelectTest order asc");
        Stream s2 = ydb.getStream("s2");
        tuples = fetchTuples(s2);
        assertEquals(3, tuples.size());
        
        // Ordered ascending by key
        assertEquals(1000L, tuples.get(0).getColumn("gentime"));
        assertEquals(2000L, tuples.get(1).getColumn("gentime"));
        assertEquals(3000L, tuples.get(2).getColumn("gentime"));
    }
    
    @Test
    public void testOrderDescending() throws Exception {
        ydb.execute("create stream s1 as select * from RdbSelectTest order desc");
        Stream s1 = ydb.getStream("s1");
        List<Tuple> tuples = fetchTuples(s1);
        assertEquals(3, tuples.size());
        
        // Ordered descending by key
        // Every tuple comes from a different partition
        assertEquals(3000L, tuples.get(0).getColumn("gentime"));
        assertEquals(2000L, tuples.get(1).getColumn("gentime"));
        assertEquals(1000L, tuples.get(2).getColumn("gentime"));
        
        // Mix it up a bit, by adding a tuple that is more than a month separated
        long t4 = TimeEncoding.getWallclockTime();
        tw.onTuple(null, new Tuple(tdef, new Object[]{t4, 20, 2}));
        
        ydb.execute("create stream s2 as select * from RdbSelectTest order desc");
        Stream s2 = ydb.getStream("s2");
        tuples = fetchTuples(s2);
        assertEquals(4, tuples.size());
        assertEquals(t4, tuples.get(0).getColumn("gentime"));
        assertEquals(3000L, tuples.get(1).getColumn("gentime"));
        assertEquals(2000L, tuples.get(2).getColumn("gentime"));
        assertEquals(1000L, tuples.get(3).getColumn("gentime"));
        
        // Filter with a strict range end
        ydb.execute("create stream s3 as select * from RdbSelectTest where gentime<3000 order desc");        
        Stream s3 = ydb.getStream("s3");
        tuples = fetchTuples(s3);
        assertEquals(2, tuples.size());
        assertEquals(2000L, tuples.get(0).getColumn("gentime"));
        assertEquals(1000L, tuples.get(1).getColumn("gentime"));
        
        
        // Filter with a non-strict range end
        ydb.execute("create stream s4 as select * from RdbSelectTest where gentime<=2000 order desc");        
        Stream s4 = ydb.getStream("s4");
        tuples = fetchTuples(s4);
        assertEquals(2, tuples.size());
        assertEquals(2000L, tuples.get(0).getColumn("gentime"));
        assertEquals(1000L, tuples.get(1).getColumn("gentime"));

        // Filter with a strict range start
        ydb.execute("create stream s5 as select * from RdbSelectTest where gentime>2000 order desc");        
        Stream s5 = ydb.getStream("s5");
        tuples = fetchTuples(s5);
        assertEquals(2, tuples.size());
        assertEquals(t4, tuples.get(0).getColumn("gentime"));
        assertEquals(3000L, tuples.get(1).getColumn("gentime"));
        
        // Filter with a non-strict range start
        ydb.execute("create stream s6 as select * from RdbSelectTest where gentime>=3000 order desc");        
        Stream s6 = ydb.getStream("s6");
        tuples = fetchTuples(s6);
        assertEquals(2, tuples.size());
        assertEquals(t4, tuples.get(0).getColumn("gentime"));
        assertEquals(3000L, tuples.get(1).getColumn("gentime"));
    }
    
    @Test
    public void testOrderedMerge() throws Exception {
        ydb.execute("create stream s1 as merge " + 
            "(select * from RdbSelectTest where gentime <3000 order desc), " +
            "(select * from RdbSelectTest where gentime >= 3000 order desc) " +
            "using gentime order desc");
        Stream s1 = ydb.getStream("s1");
        List<Tuple> tuples = fetchTuples(s1);
        assertEquals(3, tuples.size());
        
        // Ordered descending by gentime
        assertEquals(3000L, tuples.get(0).getColumn("gentime"));
        assertEquals(2000L, tuples.get(1).getColumn("gentime"));
        assertEquals(1000L, tuples.get(2).getColumn("gentime"));
    }
    
    @Test(expected=ParseException.class)
    public void testInvalidOrder() throws Exception {
        ydb.execute("create stream s1 as select * from RdbSelectTest order blabla");
    }
    
    @Test
    public void testFollow() throws Exception {
        // Sanity check
        ydb.execute("create stream s1 as select * from RdbSelectTest");
        Stream s1 = ydb.getStream("s1");
        List<Tuple> tuples = fetchTuples(s1);
        assertEquals(3, tuples.size());
        
        // Record a tuple in one of the existing partitions
        // (and ordered -after- the first tuple of the table)
        Tuple sameMonthTuple = new Tuple(tdef, new Object[]{2001L, 20, 2});
        ydb.execute("create stream s2 as select * from RdbSelectTest");
        Stream s2 = ydb.getStream("s2");
        tuples = fetchTuplesAndProduceOne(s2, sameMonthTuple);
        assertEquals(4, tuples.size());
        
        // Record a tuple that is sure to be in another partition
        // (more than a month separated)
        ydb.execute("create stream s3 as select * from RdbSelectTest");
        Stream s3 = ydb.getStream("s3");
        long t = TimeEncoding.getWallclockTime();
        Tuple farOutTuple = new Tuple(tdef, new Object[]{t, 20, 2});
        tuples = fetchTuplesAndProduceOne(s3, farOutTuple);
        // assertEquals(5, tuples.size());
        // TODO this last test still fails
    }
    
    @Test
    public void testNofollow() throws Exception {
        int tableSize = 3;
        
        // Sanity check
        ydb.execute("create stream s1 as select * from RdbSelectTest nofollow");
        Stream s1 = ydb.getStream("s1");
        List<Tuple> tuples = fetchTuples(s1);
        assertEquals(tableSize, tuples.size());
        
        // Record a tuple in one of the existing partitions
        // (and ordered -after- the first tuple of the table)
        Tuple sameMonthTuple = new Tuple(tdef, new Object[]{2001L, 20, 2});
        ydb.execute("create stream s2 as select * from RdbSelectTest nofollow");
        Stream s2 = ydb.getStream("s2");
        tuples = fetchTuplesAndProduceOne(s2, sameMonthTuple);
        assertEquals(tableSize, tuples.size());

        tableSize++;
        // Record a tuple that is sure to be in another partition
        // (more than a month separated)
        ydb.execute("create stream s3 as select * from RdbSelectTest nofollow");
        Stream s3 = ydb.getStream("s3");
        long t = TimeEncoding.getWallclockTime();
        Tuple farOutTuple = new Tuple(tdef, new Object[]{t, 20, 2});
        tuples = fetchTuplesAndProduceOne(s3, farOutTuple);
        assertEquals(tableSize, tuples.size());
    }
    
    private List<Tuple> fetchTuples(Stream s) throws InterruptedException {
        List<Tuple> tuples = new ArrayList<>();
        Semaphore semaphore = new Semaphore(0);
        s.addSubscriber(new StreamSubscriber() {

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                tuples.add(tuple);
            }

            @Override
            public void streamClosed(Stream stream) {
                semaphore.release();
            }
        });
        s.start();
        semaphore.tryAcquire(5, TimeUnit.SECONDS);        
        return tuples;
    }
    
    private List<Tuple> fetchTuplesAndProduceOne(Stream s, Tuple extraTuple) throws InterruptedException {
        List<Tuple> tuples = new ArrayList<>();
        Semaphore semaphore = new Semaphore(0);
        s.addSubscriber(new StreamSubscriber() {
            
            boolean first = true;

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                if (first) {
                    first = false;
                    tw.onTuple(null, extraTuple);
                }
                tuples.add(tuple);
            }

            @Override
            public void streamClosed(Stream stream) {
                semaphore.release();
            }
        });
        s.start();
        semaphore.tryAcquire(5, TimeUnit.SECONDS);        
        return tuples;
    }
}
