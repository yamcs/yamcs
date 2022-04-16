package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.yamcs.yarch.streamsql.StreamSqlResult;

public class TableRowDeleteTest2 extends YarchTestCase {
    private void populate(String tblName) throws Exception {
        ydb.execute("create table " + tblName
                + "(a int, b int, c int, d string, primary key(a,b,c))");
        ydb.execute("create stream abcd_in(a int, b int, c int, d int)");
        ydb.execute("insert into " + tblName + " select * from abcd_in");
        Stream s = ydb.getStream("abcd_in");

        for (int a = 0; a < 10; a++) {
            for (int b = 9; b >= 0; b--) {
                for (int c = 0; c < 10; c++) {
                    s.emitTuple(new Tuple(s.getDefinition(), Arrays.asList(a, b, c, "r" + a + b + c)));
                }
            }
        }
        execute("close stream abcd_in");
    }

    private void verify(String streamQuery, final Checker checker, int numRows) throws Exception {
        ydb.execute("create stream abcd_out as " + streamQuery);

        final AtomicInteger ai = new AtomicInteger(0);
        final Semaphore semaphore = new Semaphore(0);

        Stream s = ydb.getStream("abcd_out");
        s.addSubscriber(new StreamSubscriber() {
            @Override
            public void streamClosed(Stream stream) {
                semaphore.release();
            }

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                int a = (Integer) tuple.getColumn(0);
                int b = (Integer) tuple.getColumn(1);
                int c = (Integer) tuple.getColumn(2);
                String d = (String) tuple.getColumn(3);

                checker.check(a, b, c, d);
                ai.getAndIncrement();
            }
        });
        s.start();
        semaphore.tryAcquire(30, TimeUnit.SECONDS);
        assertEquals(numRows, ai.get());
    }

    @Test
    public void testDeleteAll() throws Exception {
        populate("abcd1");
        execute("delete from abcd1");

        verify("select * from abcd1",
                (a, b, c, d) -> {
                    fail();
                }, 0);
        execute("drop table abcd1");
    }

    @Test
    public void testDeleteWithIndexCondition1() throws Exception {
        populate("abcd2");
        execute("delete from abcd2 where a < 10");

        verify("select * from abcd2",
                (a, b, c, d) -> {
                    fail();
                }, 0);
        execute("drop table abcd2");
    }

    @Test
    public void testDeleteWithInCondition() throws Exception {
        populate("abcd3");
        StreamSqlResult res = ydb.execute("delete from abcd3 where a in (2,3)");

        assertTrue(res.hasNext());
        Tuple t = res.next();
        assertEquals(200l, t.getLongColumn("deleted"));

        verify("select * from abcd3",
                (a, b, c, d) -> {
                    assertTrue(a != 2 && a != 3);
                }, 800);
        execute("drop table abcd3");
    }

    @Test
    public void testDeleteWithIndexAndFilter() throws Exception {
        populate("tdf4");

        // this query could be entirely executed based on ranges on combined a,b keys but
        // currently the RdbTableWalker is not able to perform ranges on combined primary key
        // that is why it limits the search using "a=3" condition but then it filters the results using "b<2" condition
        StreamSqlResult res = ydb.execute("delete from tdf4 where a = 3 and b < 2");

        Tuple t = res.next();
        assertEquals(100l, t.getLongColumn("inspected"));
        assertEquals(20l, t.getLongColumn("deleted"));

        verify("select * from tdf4",
                (a, b, c, d) -> {
                    assertTrue(a != 3 || b >= 2);
                }, 980);
        execute("drop table tdf4");
    }

    @Test
    public void testDeleteWithFilter1() throws Exception {
        populate("f1");

        execute("delete from f1 where b < 10");

        verify("select * from f1",
                (a, b, c, d) -> {
                    fail();
                }, 0);
        execute("drop table f1");
    }

    interface Checker {
        public void check(int a, int b, int c, String d);
    }
}
