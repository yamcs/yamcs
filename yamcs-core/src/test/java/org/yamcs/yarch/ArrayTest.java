package org.yamcs.yarch;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

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
}
