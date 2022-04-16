package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.yamcs.yarch.streamsql.StreamSqlResult;

public class SecondaryIndex3Test extends YarchTestCase {
    int n = 2;

    void populate(String tblName) throws Exception {
        ydb.execute("create table " + tblName
                + "(a long, b int, c string, "
                + "primary key(a, b), index(b))");

        ydb.execute("create stream test_in(a long, b int, c string)");
        ydb.execute("insert into " + tblName + " select * from test_in");
        Stream s = ydb.getStream("test_in");

        for (int i = 0; i < n; i++) {
            s.emitTuple(new Tuple(s.getDefinition(), Arrays.asList(1l, i, "test")));
        }
        execute("close stream test_in");
    }

    @Test
    public void testUpdatePk() throws Exception {
        populate("test1");

        ydb.execute("update test1 set b=b+2 where b = 0");
        StreamSqlResult r = ydb.execute("select * from test1");
        Tuple t = r.next();
        assertEquals(1, t.getIntColumn("b"));
        t = r.next();
        assertEquals(2, t.getIntColumn("b"));
        assertFalse(r.hasNext());
    }

    @Test
    public void testUpdatePkDuplicate() throws Exception {
        populate("test2");
        Exception e = null;
        try {
            ydb.execute("update test2 set b=b+1 where b = 0");
        } catch (Exception e1) {
            e = e1;
        }
        assertNotNull(e);
        assertTrue(e.getMessage().contains("duplicate key"));
    }
}
