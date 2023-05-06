package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class NullTest extends YarchTestCase {

    @Test
    public void testInsert() throws Exception {
        execute("create table tbl1(k int, u string, v string, w string, primary key(k))");

        // Do not specify 'v'
        execute("insert into tbl1(k, u, w) values(100, 'uval', 'wval')");

        List<Tuple> tuples = fetchAllFromTable("tbl1");
        assertEquals(1, tuples.size());

        Tuple tuple = tuples.get(0);
        assertEquals(100, tuple.getIntColumn("k"));
        assertEquals("uval", tuple.getColumn("u"));
        assertNull(tuple.getColumn("v"));
        assertEquals("wval", tuple.getColumn("w"));

        // Explicit null-insert of 'v'
        execute("delete from tbl1");
        execute("insert into tbl1(k, u, v, w) values(100, 'uval2', null, 'wval2')");

        tuples = fetchAllFromTable("tbl1");
        assertEquals(1, tuples.size());

        tuple = tuples.get(0);
        assertEquals(100, tuple.getIntColumn("k"));
        assertEquals("uval2", tuple.getColumn("u"));
        assertNull(tuple.getColumn("v"));
        assertEquals("wval2", tuple.getColumn("w"));

        // Update 'w' to null
        execute("update tbl1 set w = null");

        tuples = fetchAllFromTable("tbl1");
        assertEquals(1, tuples.size());

        tuple = tuples.get(0);
        assertEquals(100, tuple.getIntColumn("k"));
        assertEquals("uval2", tuple.getColumn("u"));
        assertNull(tuple.getColumn("v"));
        assertNull(tuple.getColumn("w"));

        // No change
        execute("delete from tbl1 where w is not null");
        tuples = fetchAllFromTable("tbl1");
        assertEquals(1, tuples.size());

        // Remove the only record
        execute("delete from tbl1 where w is null");
        tuples = fetchAllFromTable("tbl1");
        assertEquals(0, tuples.size());
    }

    @Test
    public void testSelect() throws Exception {
        execute("create table tbl2(k int, u string, v string, primary key(k))");
        execute("insert into tbl2(k, u, w) values(100, 'uval', 'wval')");
        execute("insert into tbl2(k, u, w) values(200, 'uval2', 'wval2')");

        var result = ydb.execute("select k, null from tbl2");

        List<Tuple> tuples = new ArrayList<>();
        result.forEachRemaining(tuples::add);
        result.close();

        assertEquals(2, tuples.size());

        Tuple tuple = tuples.get(0);
        assertEquals(2, tuple.size());
        assertEquals(100, tuple.getColumn(0));
        assertNull(tuple.getColumn(1));

        tuple = tuples.get(1);
        assertEquals(2, tuple.size());
        assertEquals(200, tuple.getColumn(0));
        assertNull(tuple.getColumn(1));
    }
}
