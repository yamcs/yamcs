package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.yamcs.yarch.streamsql.StreamSqlResult;

public class SecondaryIndex2Test extends YarchTestCase {

    void populate(String tblName) throws Exception {
        int n = 10;
        ydb.execute("create table " + tblName
                + "(a long auto_increment, b string, "
                + "primary key(a), index(b))");

        ydb.execute("create stream abcd_in(b string)");
        ydb.execute("insert into " + tblName + " select * from abcd_in");
        Stream s = ydb.getStream("abcd_in");

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < 3; j++) {
                Tuple t = new Tuple(s.getDefinition(), Arrays.asList("row " + i));
                s.emitTuple(t);
            }
        }
        execute("close stream abcd_in");
    }

    @Test
    public void test1() throws Exception {
        populate("test1");
        StreamSqlResult res = ydb.execute("select * from test1 where b=?", "row 0");

        for (int i = 0; i < 3; i++) {
            assertTrue(res.hasNext());
            Tuple t = res.next();
            long a = (Long) t.getColumn("a");
            assertEquals(i, a);
            assertEquals("row 0", (String) t.getColumn("b"));
        }
        assertFalse(res.hasNext());
    }
}
