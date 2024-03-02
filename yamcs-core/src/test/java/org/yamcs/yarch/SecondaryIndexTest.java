package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;

public class SecondaryIndexTest extends YarchTestCase {

    void populate(String tblName) throws Exception {
        int n = 10;
        ydb.execute("create table " + tblName
                + "(a int, b int, c int, d string, "
                + "primary key(a,b), index(c))");

        ydb.execute("create stream abcd_in(a int, b int, c int, d int)");
        ydb.execute("insert into " + tblName + " select * from abcd_in");
        Stream s = ydb.getStream("abcd_in");

        for (int a = 0; a < n; a++) {
            for (int b = 0; b < n; b++) {
                Tuple t = new Tuple(s.getDefinition(), Arrays.asList(a, b, a + b, "r" + a + b));
                s.emitTuple(t);
            }
        }
        execute("close stream abcd_in");
    }

    @Test
    public void testInvalid1() {
        assertThrows(StreamSqlException.class, () -> {
            ydb.execute("create table invalid1 (a int, b string, primary key(a), index(b,a))");
        });
    }

    @Test
    public void test1() throws Exception {
        populate("test1");
        StreamSqlResult res = ydb.execute("select * from test1 where c=3");
        for (int i = 0; i < 4; i++) {
            assertTrue(res.hasNext());
            Tuple t = res.next();
            assertEquals(3, (int) t.getColumn("c"));
        }
        assertFalse(res.hasNext());
    }

    @Test
    public void testDbReload() throws Exception {
        ydb.execute("create table test_reload(a int, b int, primary key(a), index(b))");

        TableDefinition tbldef = ydb.getTable("test_reload");
        List<String> idx = tbldef.getSecondaryIndex();
        assertEquals(1, idx.size());
        assertEquals("b", idx.get(0));

        YarchDatabase.removeInstance(instance);

        ydb = YarchDatabase.getInstance(instance);

        TableDefinition tbldef1 = ydb.getTable("test_reload");
        List<String> idx1 = tbldef1.getSecondaryIndex();
        assertEquals(1, idx1.size());
        assertEquals("b", idx1.get(0));
    }

    @Test
    public void testDropTable() throws Exception {
        ydb.execute("create table test_drop(a int, b int, primary key(a), index(b))");
        ydb.execute("drop table test_drop");
        ydb.execute("create table test_drop(a int, b int, primary key(a), index(b))");
    }

}
