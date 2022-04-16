package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.yamcs.yarch.streamsql.StreamSqlException;

public class AutoincrementTest extends YarchTestCase {

    @Test
    public void testInvalid1() {
        assertThrows(StreamSqlException.class, () -> {
            ydb.execute("create table test1 (a int, b int, c int, d string auto_increment, primary key(a,b,c))");
        });
    }

    @Test
    public void testInvalid2() {
        assertThrows(StreamSqlException.class, () -> {
            ydb.execute("create table test1 (a int, b int, c int auto_increment, d string, primary key(a,b,c))");
        });
    }

    void populate(String tblName, int start, int n) throws Exception {
        ydb.execute("create table if not exists " + tblName
                + "  (a int, b long auto_increment, c long auto_increment , primary key(a,b))");
        ydb.execute("create stream abcd_in(a int)");
        ydb.execute("insert into " + tblName + " select * from abcd_in");
        Stream s = ydb.getStream("abcd_in");
        for (int i = start; i < start + n; i++) {
            s.emitTuple(new Tuple(s.getDefinition(), Arrays.asList(i)));
        }
        ydb.execute("close stream abcd_in");
    }

    @Test
    public void test1() throws Exception {
        populate("test1", 5, 5);

        ydb.execute("create stream abcd_out as select * from test1");
        List<Tuple> tlist = fetchAll("abcd_out");
        assertEquals(5, tlist.size());

        for (int i = 0; i < 5; i++) {
            Tuple tuple = tlist.get(i);
            int a = (Integer) tuple.getColumn("a");
            long b = (Long) tuple.getColumn("b");
            long c = (Long) tuple.getColumn("c");

            assertEquals(i, a - 5);
            assertEquals((long) i, b);
            assertEquals((long) i, c);
        }
    }

    @Test
    public void testPersistence() throws Exception {
        populate("test1", 5, 5);
        YarchDatabase.removeInstance(instance);
        ydb = YarchDatabase.getInstance(instance);

        populate("test1", 50, 5);

        ydb.execute("create stream abcd_out as select * from test1");
        List<Tuple> tlist = fetchAll("abcd_out");
        assertEquals(10, tlist.size());

        for (int i = 0; i < 10; i++) {
            Tuple tuple = tlist.get(i);
            long b = (Long) tuple.getColumn("b");
            long c = (Long) tuple.getColumn("c");

            assertEquals((long) i, b);
            assertEquals((long) i, c);
        }
    }
}
