package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;
import org.yamcs.yarch.streamsql.StreamSqlStatement;

public class HistogramStreamTest extends YarchTestCase {
    StreamSqlStatement statement;
    String cmd;

    private void populate(String tblName, int numDistinctiveValues, int numSamplesPerValue, boolean partitioned)
            throws Exception {
        int n = numSamplesPerValue;
        int m = numDistinctiveValues;
        String query = "create table " + tblName
                + "(gentime timestamp, seqNum int, name string, primary key(gentime, seqNum)) histogram(name)"
                + (partitioned ? "partition by time(gentime)" : "");
        execute(query);

        execute("create stream " + tblName + "_in(gentime timestamp, seqNum int, name string)");
        execute("insert into " + tblName + " select * from " + tblName + "_in");

        Stream s = ydb.getStream(tblName + "_in");
        TupleDefinition td = s.getDefinition();

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                Tuple t = new Tuple(td, new Object[] { 1000L * i + j, j, "histotest" + j });
                s.emitTuple(t);
            }
        }

        for (int i = 2 * n; i < 3 * n; i++) {
            for (int j = 0; j < m; j++) {
                Tuple t = new Tuple(td, new Object[] { 1000L * i + j, j, "histotest" + j });
                s.emitTuple(t);
            }
        }

        Tuple t = new Tuple(td, new Object[] { 1000 * 100000L, 1, "histotest1m" });
        s.emitTuple(t);
        execute("close stream " + tblName + "_in");
    }

    @Test
    public void test0() throws Exception {
        populate("test0", 1, 20, false);
        String query = "create stream test_out as select * from test0 histogram(name)";
        execute(query);
        final List<Tuple> tuples = fetchAll("test_out");
        assertEquals(3, tuples.size());

        verifyEquals("histotest0", 0, 19000, 20, tuples.get(0));
        verifyEquals("histotest0", 40000, 59000, 20, tuples.get(1));
        verifyEquals("histotest1m", 100000000, 100000000, 1, tuples.get(2));
        execute("drop table test0");
        var tblsp = RdbStorageEngine.getInstance().getTablespace(instance);
        var l = tblsp.getTableHistograms(instance, "test0");
        assertEquals(0, l.size());
    }

    @Test
    public void test0Part() throws Exception {
        populate("test0", 1, 20, true);
        String query = "create stream test_out as select * from test0 histogram(name)";
        execute(query);
        final List<Tuple> tuples = fetchAll("test_out");
        assertEquals(3, tuples.size());

        verifyEquals("histotest0", 0, 19000, 20, tuples.get(0));
        verifyEquals("histotest0", 40000, 59000, 20, tuples.get(1));
        verifyEquals("histotest1m", 100000000, 100000000, 1, tuples.get(2));
        execute("drop table test0");
    }

    @Test
    public void test1() throws Exception {
        int n = 10;
        int m = 2;
        populate("test1", m, n, false);
        String query = "create stream test1_out as select * from test1 histogram(name) where last>" + (n * 1000)
                + " and first<90000000";
        execute(query);
        final List<Tuple> tuples = fetchAll("test1_out");
        assertEquals(m, tuples.size());
        Tuple t = tuples.get(0);
        // tuples should contain
        // (name String, start TIMESTAMP, stop TIMESTAMP, num int)
        assertEquals(4, t.size());
        assertEquals("histotest0", (String) t.getColumn(0));
        assertEquals(2 * n * 1000L, (long) (Long) t.getColumn(1));
        assertEquals((3 * n - 1) * 1000L, (long) (Long) t.getColumn(2));
        assertEquals(n, (int) (Integer) t.getColumn(3));

        execute("drop table test1");
    }

    @Test
    public void testEmpyStream() throws Exception {
        int m = 2;
        int n = 1;
        populate("testEmptyStream", m, n, true);
        String query = "create stream testEmptyStream_out as select * from testEmptyStream histogram(name) where last>0 and first<-1";
        execute(query);
        final List<Tuple> tuples = fetchAll("testEmptyStream_out");
        assertEquals(0, tuples.size());

        String query1 = "create stream testEmptyStream_out1 as select * from testEmptyStream histogram(name) where last>76797379324836000";
        execute(query1);
        final List<Tuple> tuples1 = fetchAll("testEmptyStream_out1");
        assertEquals(0, tuples1.size());

        String query2 = "create stream testEmptyStream_out2 as select * from testEmptyStream histogram(name) where first>76797379324836000";
        execute(query2);
        final List<Tuple> tuples2 = fetchAll("testEmptyStream_out2");
        assertEquals(0, tuples2.size());

        execute("drop table testEmptyStream");
    }

    @Test
    public void test4() throws Exception {
        int m = 2;
        int n = 1;
        populate("test1", m, n, true);
        String query = "create stream test1_out as select * from test1 histogram(name) where first>" + (n * 3000)
                + " and last<=100000000";
        execute(query);
        final List<Tuple> tuples = fetchAll("test1_out");
        assertEquals(1, tuples.size());
        Tuple t = tuples.get(0);

        t = tuples.get(0);
        assertEquals("histotest1m", (String) t.getColumn(0));
        execute("drop table test1");
    }

    void verifyEquals(String value, long start, long stop, int num, Tuple t) {
        assertEquals(4, t.size());
        assertEquals(value, (String) t.getColumn(0));
        assertEquals(start, (long) (Long) t.getColumn(1));
        assertEquals(stop, (long) (Long) t.getColumn(2));
        assertEquals(num, (int) (Integer) t.getColumn(3));
    }
}
