package org.yamcs.yarch.rocksdb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
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
import org.yamcs.yarch.YarchTestCase;

@Disabled // note that this one does not cleanup the test directory resulting in error if run multiple times
public class RdbPerformanceTest extends YarchTestCase {
    private TupleDefinition tdef;
    private TableWriter tw;
    int numPacketType = 20;
    int[] freq = new int[] {
            1, 1, 1, 1,
            10, 10, 10, 10,
            300, 300, 300, 300,
            3600, 3600, 3600, 3600,
            86400, 86400, 86400, 86400
    };
    String dir = "/storage/ptest";

    void populate(TableDefinition tblDef, int n, boolean timeFirst) throws Exception {
        RdbStorageEngine rse = (RdbStorageEngine) ydb.getStorageEngine(tblDef);
        tw = rse.newTableWriter(ydb, tblDef, InsertMode.INSERT);

        long baseTime = TimeEncoding.parse("2015-01-01T00:00:00");

        ThreadLocalRandom r = ThreadLocalRandom.current();
        byte[] b = new byte[256];
        int numPackets = 0;

        long t0 = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < freq.length; j++) {
                if (i % freq[j] == 0) {
                    r.nextBytes(b);
                    numPackets++;
                    Tuple t;
                    if (timeFirst) {
                        t = new Tuple(tdef, new Object[] { baseTime + i * 1000L + j, "packet" + j, b });
                    } else {
                        t = new Tuple(tdef, new Object[] { "packet" + j, baseTime + i * 1000L + j, b });
                    }
                    tw.onTuple(null, t);
                }
            }
        }
        System.out.println("total numPackets: " + numPackets);
        long t1 = System.currentTimeMillis();
        System.out.println("time to populate " + ((t1 - t0) / 1000) + " seconds");
    }

    void read(String tblName, String packetName) throws Exception {
        long t0 = System.currentTimeMillis();
        String q = "create stream s as select * from " + tblName;
        if (packetName != null) {
            q = q + " where pname='" + packetName + "'";
        }
        execute(q);
        Stream s = ydb.getStream("s");

        Semaphore semaphore = new Semaphore(0);
        AtomicInteger r = new AtomicInteger();
        s.addSubscriber(new StreamSubscriber() {
            int c = 0;

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                if (packetName != null) {
                    assertEquals(packetName, tuple.getColumn("pname"));
                }
                c++;
            }

            @Override
            public void streamClosed(Stream stream) {
                r.set(c);
                semaphore.release();
            }
        });
        s.start();
        semaphore.acquire();

        long t1 = System.currentTimeMillis();
        System.out
                .println("time to read " + r.get() + " tuples with " + packetName + ": " + (t1 - t0) + " miliseconds");
    }

    void populateAndRead(TableDefinition tbldef, boolean timeFirst) throws Exception {
        String tblname = tbldef.getName();
        System.out.println("********************** " + tblname + " timeFirst:" + timeFirst + " **********************");

        // populate(tblDef, 365*24*60*60);
        populate(tbldef, 90 * 24 * 60 * 60, timeFirst);
        // Thread.sleep(1000);

        // populate(tblDef, 100);
        read(tblname, null);
        read(tblname, "packet1");
        read(tblname, "packet5");
        read(tblname, "packet9");
        read(tblname, "packet14");
        read(tblname, "packet19");
        System.out.println("sleeping 60 seconds to allow rocksdb consolidation");
        Thread.currentThread().sleep(60000);
        read(tblname, null);

    }

    @Test
    public void testPname() throws Exception {
        String tblname = "Pname";
        tdef = new TupleDefinition();
        tdef.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
        tdef.addColumn(new ColumnDefinition("pname", DataType.ENUM));
        tdef.addColumn(new ColumnDefinition("packet", DataType.BINARY));
        TableDefinition tblDef = new TableDefinition(tblname, tdef, Arrays.asList("gentime"));

        PartitioningSpec pspec = PartitioningSpec.valueSpec("pname");
        pspec.setValueColumnType(DataType.ENUM);
        tblDef.setPartitioningSpec(pspec);

        tblDef.setStorageEngineName(YarchDatabase.RDB_ENGINE_NAME);

        ydb.createTable(tblDef);
        populateAndRead(tblDef, true);
    }

    @Test
    public void testNoPnameYYYY() throws Exception {
        String tblname = "NoPname_YYYY";
        tdef = new TupleDefinition();
        tdef.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
        tdef.addColumn(new ColumnDefinition("pname", DataType.ENUM));
        tdef.addColumn(new ColumnDefinition("packet", DataType.BINARY));
        TableDefinition tblDef = new TableDefinition(tblname, tdef, Arrays.asList("gentime"));

        PartitioningSpec pspec = PartitioningSpec.timeSpec("gentime", "YYYY");
        tblDef.setPartitioningSpec(pspec);

        tblDef.setStorageEngineName(YarchDatabase.RDB_ENGINE_NAME);

        ydb.createTable(tblDef);

        populateAndRead(tblDef, true);
    }

    @Test
    public void testPnameYYYY() throws Exception {
        String tblname = "Pname_YYYY";
        tdef = new TupleDefinition();

        tdef.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
        tdef.addColumn(new ColumnDefinition("pname", DataType.ENUM));
        tdef.addColumn(new ColumnDefinition("packet", DataType.BINARY));
        TableDefinition tblDef = new TableDefinition(tblname, tdef, Arrays.asList("gentime", "pname"));

        PartitioningSpec pspec = PartitioningSpec.timeAndValueSpec("gentime", "pname", "YYYY");
        tblDef.setPartitioningSpec(pspec);

        tblDef.setStorageEngineName(YarchDatabase.RDB_ENGINE_NAME);

        ydb.createTable(tblDef);

        populateAndRead(tblDef, true);
    }
}
/*
 * Results
 Intel(R) Core(TM) i7-4610M CPU @ 3.00GHz
 Samsung SSD 850 EVO 500GB
 
 
 * 
 ********************** NoPname_YYYY timeFirst:true **********************
total numPackets: 3814120
time to populate 26 seconds
time to read 3814120 tuples with null: 8196 miliseconds
time to read 864000 tuples with packet1: 8440 miliseconds
time to read 86400 tuples with packet5: 8500 miliseconds
time to read 2880 tuples with packet9: 8072 miliseconds
time to read 240 tuples with packet14: 8146 miliseconds
time to read 10 tuples with packet19: 8110 miliseconds
time to read 3814120 tuples with null: 7840 miliseconds
********************** Pname timeFirst:true **********************
total numPackets: 3814120
time to populate 79 seconds
time to read 3814120 tuples with null: 17420 miliseconds
time to read 864000 tuples with packet1: 1855 miliseconds
time to read 86400 tuples with packet5: 183 miliseconds
time to read 2880 tuples with packet9: 6 miliseconds
time to read 240 tuples with packet14: 1 miliseconds
time to read 10 tuples with packet19: 2 miliseconds
time to read 3814120 tuples with null: 8740 miliseconds
********************** Pname_YYYY timeFirst:true **********************
total numPackets: 3814120
time to populate 90 seconds
time to read 3814120 tuples with null: 17903 miliseconds
time to read 864000 tuples with packet1: 3624 miliseconds
time to read 86400 tuples with packet5: 340 miliseconds
time to read 2880 tuples with packet9: 118 miliseconds
time to read 240 tuples with packet14: 2 miliseconds
time to read 10 tuples with packet19: 1 miliseconds
time to read 3814120 tuples with null: 16988 miliseconds
 
 */
