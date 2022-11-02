package org.yamcs.yarch.rocksdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimeInterval;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.ColumnSerializer;
import org.yamcs.yarch.ColumnSerializerFactory;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.HistogramIterator;
import org.yamcs.yarch.HistogramRecord;
import org.yamcs.yarch.PartitionManager;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchTestCase;
import org.yamcs.yarch.streamsql.StreamSqlException;

public class HistogramRebuilderTest extends YarchTestCase {
    String tblName = "HistogramRebuilderTest";
    TableDefinition tblDef;
    RdbStorageEngine rse;
    long t1 = TimeEncoding.parse("2016-12-16T00:00:00");

    void createTable(boolean partitioned) throws StreamSqlException, ParseException {
        String query = "create table " + tblName
                + "(gentime timestamp, seqNum int, name string, primary key(gentime, seqNum)) histogram(name) "
                + (partitioned ? " partition by time(gentime)" : "");
        ydb.execute(query);
    }

    public void populate(boolean partitioned) throws Exception {
        createTable(partitioned);

        tblDef = ydb.getTable(tblName);
        rse = (RdbStorageEngine) ydb.getStorageEngine(tblDef);
        TableWriter tw = rse.newTableWriter(ydb, tblDef, InsertMode.INSERT);
        tw.onTuple(null, new Tuple(tblDef.getTupleDefinition(), new Object[] { 1000L, 10, "p1" }));
        tw.onTuple(null, new Tuple(tblDef.getTupleDefinition(), new Object[] { 2000L, 20, "p1" }));
        tw.onTuple(null, new Tuple(tblDef.getTupleDefinition(), new Object[] { 3000L, 30, "p2" }));

        tw.onTuple(null, new Tuple(tblDef.getTupleDefinition(), new Object[] { t1, 30, "p2" }));
        tw.close();
    }

    @AfterEach
    public void dropTable() throws Exception {
        ydb.execute("drop table " + tblName);
    }

    @Test
    public void testDeleteValues() throws Exception {
        populate(true);
        Tablespace tablespace = rse.getTablespace(ydb.getName());
        TimeInterval interval = new TimeInterval();
        HistogramIterator iter = rse.getHistogramIterator(ydb, tblDef, "name", interval);
        assertNumElementsEqual(iter, 3);
        iter.close();

        HistogramRebuilder rebuilder = new HistogramRebuilder(tablespace, ydb, tblName);
        PartitionManager.Interval pminterval = ydb.getPartitionManager(tblDef)
                .intervalIterator(new TimeInterval(1000L, 1000L)).next();
        rebuilder.deleteHistograms(pminterval, new CompletableFuture<Void>());

        iter = rse.getHistogramIterator(ydb, tblDef, "name", interval);
        assertNumElementsEqual(iter, 1);
        iter.close();

        rebuilder.rebuild(new TimeInterval(0, 2000)).get();
        iter = rse.getHistogramIterator(ydb, tblDef, "name", interval);
        assertNumElementsEqual(iter, 3);
        iter.close();
    }

    @Test
    public void testRebuildAll() throws Exception {
        populate(true);
        Tablespace tablespace = rse.getTablespace(ydb.getName());
        HistogramIterator iter = rse.getHistogramIterator(ydb, tblDef, "name", new TimeInterval());
        assertNumElementsEqual(iter, 3);
        iter.close();

        HistogramRebuilder rebuilder = new HistogramRebuilder(tablespace, ydb, tblName);
        ydb.getPartitionManager(tblDef).intervalIterator(new TimeInterval()).forEachRemaining(interval -> {
            rebuilder.deleteHistograms(interval, new CompletableFuture<Void>());
        });

        iter = rse.getHistogramIterator(ydb, tblDef, "name", new TimeInterval());
        assertNumElementsEqual(iter, 0);
        iter.close();

        rebuilder.rebuild().get();
        iter = rse.getHistogramIterator(ydb, tblDef, "name", new TimeInterval());

        assertNumElementsEqual(iter, 3);
        iter.close();
    }

    Thread startWriter(int n, int m, int seqStart, String p, Semaphore semaphore) {
        Thread thread = new Thread(() -> {
            int seq = seqStart;
            for (int j = 0; j < n; j++) {
                long start = 500_000 * j;
                for (int i = 0; i < m; i++) {
                    TableWriter tw = rse.newTableWriter(ydb, tblDef, InsertMode.INSERT);
                    tw.onTuple(null, new Tuple(tblDef.getTupleDefinition(), new Object[] { start + i, seq++, p }));
                }
                if (j == 2 && semaphore != null) {
                    semaphore.release();
                }
            }
        });
        thread.start();

        return thread;
    }

    @Test
    public void testConcurrency() throws Exception {
        int n = 5;
        int m = 2;

        createTable(false);
        tblDef = ydb.getTable(tblName);
        rse = (RdbStorageEngine) ydb.getStorageEngine(tblDef);
        Semaphore semaphore = new Semaphore(0);
        Thread thread1 = startWriter(n, m, 0, "p1", semaphore);

        // wait for the thread to write 2 cycles
        semaphore.acquire();
        Tablespace tablespace = rse.getTablespace(ydb.getName());
        // rebuild all the histograms
        HistogramRebuilder rebuilder = new HistogramRebuilder(tablespace, ydb, tblName);
        rebuilder.rebuild().get();

        // wait for the thread to finish
        thread1.join(100000);
        // assertFalse(thread1.isAlive());

        ColumnSerializer<String> cs = ColumnSerializerFactory.getBasicColumnSerializerV3(DataType.STRING);
        HistogramIterator iter = rse.getHistogramIterator(ydb, tblDef, "name", new TimeInterval());
        List<HistogramRecord> p1List = new ArrayList<>();
        while (iter.hasNext()) {
            HistogramRecord hr = iter.next();
            String p = cs.deserialize(ByteBuffer.wrap(hr.getColumnv()), null);
            if ("p1".equals(p)) {
                p1List.add(hr);
            }
        }
        assertEquals(n, p1List.size());

        for (int j = 0; j < n; j++) {
            HistogramRecord hr1 = p1List.get(j);

            assertEquals(j * 500_000, hr1.getStart());
            assertEquals(j * 500_000 + m - 1, hr1.getStop());
            assertEquals(m, hr1.getNumTuples());

        }
        iter.close();
    }

    /**
     * This test shows that rebuilding a histogram with two fast concurrent writers may result in inconsistent results.
     * <p>
     * See {@link SingleColumnHistogramWriter#startQueueing(String)}.
     * 
     * @throws Exception
     */
    @Test
    @Disabled
    public void testConcurrencyDoubleThread() throws Exception {
        int n = 5;
        int m = 2;

        createTable(false);
        tblDef = ydb.getTable(tblName);
        rse = (RdbStorageEngine) ydb.getStorageEngine(tblDef);
        Semaphore semaphore = new Semaphore(0);
        Thread thread1 = startWriter(n, m, 0, "p1", semaphore);
        Thread thread2 = startWriter(n, m, 1000_000, "p2", null);

        // wait for the thread1 to write 2 cycles
        semaphore.acquire();

        // rebuild all the histograms
        Tablespace tablespace = rse.getTablespace(ydb.getName());
        HistogramRebuilder rebuilder = new HistogramRebuilder(tablespace, ydb, tblName);
        rebuilder.rebuild().get();

        // wait for the threads to finish
        thread1.join(100000);
        thread2.join(100000);
        assertFalse(thread1.isAlive());
        assertFalse(thread2.isAlive());

        // now verify the results
        ColumnSerializer<String> cs = ColumnSerializerFactory.getBasicColumnSerializerV3(DataType.STRING);
        HistogramIterator iter = rse.getHistogramIterator(ydb, tblDef, "name", new TimeInterval());
        List<HistogramRecord> p1List = new ArrayList<>();
        List<HistogramRecord> p2List = new ArrayList<>();
        while (iter.hasNext()) {
            HistogramRecord hr = iter.next();
            String p = cs.deserialize(ByteBuffer.wrap(hr.getColumnv()), null);
            if ("p1".equals(p)) {
                p1List.add(hr);
            } else {
                p2List.add(hr);
            }
        }
        assertEquals(n, p1List.size());
        assertEquals(n, p2List.size());

        for (int j = 0; j < n; j++) {
            HistogramRecord hr1 = p1List.get(j);
            HistogramRecord hr2 = p2List.get(j);

            assertEquals(j * 500_000, hr1.getStart());
            assertEquals(j * 500_000 + m - 1, hr1.getStop());
            assertEquals(m, hr1.getNumTuples());

            assertEquals(j * 500_000, hr2.getStart());
            assertEquals(j * 500_000 + m - 1, hr2.getStop());
            assertEquals(m, hr2.getNumTuples());
        }
        iter.close();
    }
}
