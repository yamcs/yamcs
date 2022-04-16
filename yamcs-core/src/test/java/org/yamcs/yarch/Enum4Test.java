package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.yamcs.yarch.streamsql.StreamSqlResult;

import com.google.common.collect.BiMap;

public class Enum4Test extends YarchTestCase {
    int n = 10;

    private void populate(String tblname) throws Exception {
        execute("create table " + tblname
                + "(id int, tags enum[], primary key(id))");
        execute("create stream " + tblname + "_in(id int, tags enum[])");
        execute("insert into " + tblname + " select * from " + tblname + "_in");

        Stream s = ydb.getStream(tblname + "_in");
        TupleDefinition td = s.getDefinition();

        for (int i = 0; i < n; i++) {
            List<String> l = new ArrayList<>(i);
            for (int j = 0; j < i; j++) {
                l.add("tag" + j);
            }
            Tuple t = new Tuple(td, new Object[] { i, l });
            s.emitTuple(t);
        }
        execute("close stream " + tblname + "_in");

    }

    @Test
    public void test1() throws Exception {
        populate("test1");

        StreamSqlResult r = ydb.execute("select * from test1");
        for (int i = 0; i < n; i++) {
            assertTrue(r.hasNext());
            Tuple t = r.next();
            assertEquals(i, t.getIntColumn("id"));
            List<String> l = t.getColumn("tags");
            assertEquals(i, l.size());
            for (int j = 0; j < i; j++) {
                assertEquals("tag" + j, l.get(j));
            }
        }

        assertFalse(r.hasNext());

        BiMap<String, Short> x = ydb.getTable("test1").getEnumValues("tags");
        assertEquals(n - 1, x.size());
    }
}
