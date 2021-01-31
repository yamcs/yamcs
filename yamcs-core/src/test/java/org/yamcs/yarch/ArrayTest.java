package org.yamcs.yarch;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.yamcs.yarch.streamsql.StreamSqlResult;

/**
 * Tests columns of type array
 */
public class ArrayTest extends YarchTestCase {
    int n = 10;

    private void populate(String tblName) throws Exception {
        execute("create table " + tblName
                + "(id int, tag string[], primary key(id))");
        execute("create stream " + tblName + "_in(id long, tag string[])");
        execute("upsert into " + tblName + " select * from " + tblName + "_in");
        Stream s = ydb.getStream(tblName + "_in");
        for (int i = 0; i < n; i++) {
            s.emitTuple(new Tuple(s.getDefinition(), Arrays.asList(i, Arrays.asList("tag" + i, "tag" + (i + 1)))));
        }

    }

    @Test
    public void test1() throws Exception {
        populate("test1");
        List<Tuple> tlist = fetchAllFromTable("test1");
        assertEquals(n, tlist.size());
        for (int i = 0; i < n; i++) {
            Tuple t = tlist.get(i);
            assertEquals(i, t.getIntColumn("id"));

            List<String> tags = t.getColumn("tag");
            assertEquals(Arrays.asList("tag" + i, "tag" + (i + 1)), tags);
        }
    }

    @Test
    public void testArrayIntersect() throws Exception {
        populate("test2");
        StreamSqlResult r = ydb.execute("select * from test2 where tag && array['tag1', 'tag10']");

        Tuple t = r.next();
        assertEquals(Arrays.asList("tag0", "tag1"), t.getColumn("tag"));

        t = r.next();
        assertEquals(Arrays.asList("tag1", "tag2"), t.getColumn("tag"));

        t = r.next();
        assertEquals(Arrays.asList("tag9", "tag10"), t.getColumn("tag"));

        assertFalse(r.hasNext());
    }

    @Test
    public void testArrayIntersect2() throws Exception {
        populate("test3");
        StreamSqlResult r = ydb.execute("select * from test3 where tag && array['tag'+id, 'tag10']");

        for (int i = 0; i < n; i++) {
            assertTrue(r.hasNext());
            r.next();
        }
        assertFalse(r.hasNext());
    }

    @Test
    public void testArrayIntersect3() throws Exception {
        populate("test4");
        StreamSqlResult r = ydb.execute("select * from test4 where tag && ?", Arrays.asList("tag1", "tag10"));

        Tuple t = r.next();
        assertEquals(Arrays.asList("tag0", "tag1"), t.getColumn("tag"));

        t = r.next();
        assertEquals(Arrays.asList("tag1", "tag2"), t.getColumn("tag"));

        t = r.next();
        assertEquals(Arrays.asList("tag9", "tag10"), t.getColumn("tag"));

        assertFalse(r.hasNext());
    }
}
