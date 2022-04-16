package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

public class StreamSelect2Test extends YarchTestCase {
    final int n = 51;

    public void createFeeder1() throws YarchException {
        Stream s;
        YarchDatabaseInstance ydb = context.getDb();
        final TupleDefinition tpdef = new TupleDefinition();
        tpdef.addColumn("x", DataType.INT);
        tpdef.addColumn("y", DataType.INT);

        s = (new Stream(ydb, "stream_in", tpdef) {
            @Override
            public void doStart() {
                for (int i = 0; i < n; i++) {
                    Integer x = i;
                    Integer y = i * 2;

                    Tuple t = new Tuple(tpdef, new Object[] { x, y });
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
    public void testAdd() throws Exception {
        createFeeder1();

        execute("create stream stream_out1 as select x+y from stream_in");
        List<Tuple> tlist = fetchAll("stream_out1");

        assertEquals(n, tlist.size());
        int k = 0;
        for (Tuple tuple : tlist) {
            int xpy = (Integer) tuple.getColumn(0);
            assertEquals(3 * k, xpy);
            k++;
        }
    }

    @Test
    public void testParanthesis() throws Exception {
        createFeeder1();

        execute("create stream stream_out1 as select (x+y) from stream_in");
        List<Tuple> tlist = fetchAll("stream_out1");

        assertEquals(n, tlist.size());
        int k = 0;
        for (Tuple tuple : tlist) {
            int xpy = (Integer) tuple.getColumn(0);
            assertEquals(3 * k, xpy);
            k++;
        }
    }

    @Test
    public void testHexNumbers() throws Exception {
        createFeeder1();

        execute("create stream stream_out1 as select * from stream_in where x>40 and x<0x2A");
        List<Tuple> tlist = fetchAll("stream_out1");

        assertEquals(1, tlist.size());
        Tuple t = tlist.get(0);
        assertEquals(41, t.getIntColumn("x"));
    }

    @Test
    public void testBitwiseAnd() throws Exception {
        createFeeder1();

        execute("create stream stream_out1 as select x, y<<1, y>>1, y^x, y|x from stream_in where x & 0x1 = 0");
        List<Tuple> tlist = fetchAll("stream_out1");
        assertEquals((n + 1) / 2, tlist.size());
        int k = 0;
        for (Tuple tuple : tlist) {
            int x = (Integer) tuple.getColumn(0);
            int y = 2 * x;
            int yshiftLeft = (Integer) tuple.getColumn(1);
            int yshiftRight = (Integer) tuple.getColumn(2);
            int yxorx = (Integer) tuple.getColumn(3);
            int yorx = (Integer) tuple.getColumn(4);
            assertEquals(0, x & 1);
            assertEquals(2 * k, x);
            assertEquals(y << 1, yshiftLeft);
            assertEquals(y >> 1, yshiftRight);
            assertEquals(y ^ x, yxorx);
            assertEquals(y | x, yorx);
            k++;
        }
    }

    @Test
    public void testAnd() throws Exception {
        createFeeder1();

        execute("create stream stream_out1 as select * from stream_in where (x+3) >= y and x>2");
        List<Tuple> tlist = fetchAll("stream_out1");
        assertEquals(1, tlist.size());
        Tuple t = tlist.get(0);
        assertEquals(3, t.getIntColumn("x"));
    }

    @Test
    public void testWindow1() throws Exception {
        createFeeder1();
        execute("CREATE STREAM stream_out1 AS SELECT SUM(y) from stream_in[SIZE 5 ADVANCE 5 ON x]");

        List<Tuple> tlist = fetchAll("stream_out1");

        assertEquals((n - 1) / 5, tlist.size());
        int k = 0;
        for (Tuple tuple : tlist) {
            int sumy = (Integer) tuple.getColumn(0);
            assertEquals(2 * (5 * k + 10), sumy);
            k += 5;
        }
    }

    @Test
    public void testWindow2() throws Exception {
        createFeeder1();
        execute("CREATE STREAM stream_out1 AS SELECT SUM(y+3) from stream_in[SIZE 5 ADVANCE 5 ON x]");

        List<Tuple> tlist = fetchAll("stream_out1");

        assertEquals((n - 1) / 5, tlist.size());
        int k = 0;
        for (Tuple tuple : tlist) {
            int sumy = (Integer) tuple.getColumn(0);
            assertEquals(2 * (5 * k + 10) + 15, sumy);
            k += 5;
        }
    }

    @Test
    public void testWindow3() throws Exception {
        createFeeder1();
        execute("CREATE STREAM stream_out1 AS SELECT 2+SUM(x+y+1) from stream_in[SIZE 5 ADVANCE 5 ON x]");

        List<Tuple> tlist = fetchAll("stream_out1");

        assertEquals((n - 1) / 5, tlist.size());
        int k = 0;
        for (Tuple tuple : tlist) {
            int sumy = (Integer) tuple.getColumn(0);
            assertEquals(3 * (5 * k + 10) + 5 + 2, sumy);
            k += 5;
        }
    }

    @Test
    public void testWindow4() throws Exception {
        createFeeder1();
        execute("CREATE STREAM stream_out1 AS SELECT aggregatelist(*) from stream_in[SIZE 5 ADVANCE 5 ON x]");

        List<Tuple> tlist = fetchAll("stream_out1");

        assertEquals((n - 1) / 5, tlist.size());
        int k = 0;
        for (Tuple tuple : tlist) {
            // System.out.println("tuple: "+tuple);
            List<Tuple> ret = (List<Tuple>) tuple.getColumn(0);
            for (Tuple t : ret) {
                assertEquals(k, ((Integer) t.getColumn(0)).intValue());
                assertEquals(2 * k, ((Integer) t.getColumn(1)).intValue());
                k++;
            }
        }
    }

    @Test
    public void testWindow5() throws Exception {
        createFeeder1();
        execute("CREATE STREAM stream_out1 AS SELECT firstval(x) AS fvx from stream_in[SIZE 5 ADVANCE 5 ON x]");

        List<Tuple> tlist = fetchAll("stream_out1");

        assertEquals((n - 1) / 5, tlist.size());
        int k = 0;
        for (Tuple tuple : tlist) {
            int firstvalx = (Integer) tuple.getColumn(0);
            assertEquals(k, firstvalx);
            k += 5;
        }
    }

    @Test
    public void testWindow6() throws Exception {
        createFeeder1();
        execute("CREATE STREAM stream_out1 AS SELECT firstval(x+y) from stream_in[SIZE 5 ADVANCE 5 ON x]");

        List<Tuple> tlist = fetchAll("stream_out1");

        assertEquals((n - 1) / 5, tlist.size());
        int k = 0;
        for (Tuple tuple : tlist) {
            // System.out.println("tuple: "+tuple);
            int fvxpy = (Integer) tuple.getColumn(0);
            assertEquals(3 * k, fvxpy);
            k += 5;
        }
    }

    @Test
    public void testWindow7() throws Exception {
        createFeeder1();
        execute(
                "CREATE STREAM stream_out1 AS SELECT firstval(x),firstval(y),aggregatelist(*) from stream_in[SIZE 5 ADVANCE 5 ON x]");

        List<Tuple> tlist = fetchAll("stream_out1");

        assertEquals((n - 1) / 5, tlist.size());
        int k = 0;
        for (Tuple tuple : tlist) {
            // System.out.println("tuple: "+tuple);
            int fvx = (Integer) tuple.getColumn(0);
            int fvy = (Integer) tuple.getColumn(1);

            assertEquals(k, fvx);
            assertEquals(2 * k, fvy);
            List<Tuple> ret = (List<Tuple>) tuple.getColumn(2);
            for (Tuple t : ret) {
                assertEquals(k, ((Integer) t.getColumn(0)).intValue());
                assertEquals(2 * k, ((Integer) t.getColumn(1)).intValue());
                k++;
            }
        }
    }

    @Test
    public void testCount1() throws Exception {
        createFeeder1();
        execute("CREATE STREAM stream_out1 AS SELECT count(*) from stream_in");

        List<Tuple> tlist = fetchAll("stream_out1");
        assertEquals(1, tlist.size());
        long count = (Long) tlist.get(0).getColumn(0);
        assertEquals(n, count);
    }

    @Test
    public void testCount2() throws Exception {
        createFeeder1();
        execute("CREATE STREAM stream_out1 AS SELECT count(*) from stream_in where x < 3");

        List<Tuple> tlist = fetchAll("stream_out1");
        assertEquals(1, tlist.size());
        long count = (Long) tlist.get(0).getColumn(0);
        assertEquals(3, count);
    }

    @Test
    public void testWindowCount() throws Exception {
        createFeeder1();
        execute("CREATE STREAM stream_out1 AS SELECT count(*) from stream_in[SIZE 5 ADVANCE 5 ON x]");

        List<Tuple> tlist = fetchAll("stream_out1");

        assertEquals((n - 1) / 5, tlist.size());
        for (Tuple tuple : tlist) {
            long count = (Long) tuple.getColumn(0);
            assertEquals(5, count);
        }
    }
}
