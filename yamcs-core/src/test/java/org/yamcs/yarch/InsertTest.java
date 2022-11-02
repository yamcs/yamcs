package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.yamcs.yarch.streamsql.StreamSqlResult;

public class InsertTest extends YarchTestCase {
    @Test
    public void test1() throws Exception {
        ydb.execute("create table tbl1(x int, v string, primary key(x))");
        // ydb.execute("insert into tbl1(x, v) values(1, 'test1')");
        ydb.execute("insert into tbl1(x, v) values(100, 'test1')");

        List<Tuple> tlist = fetchAllFromTable("tbl1");
        assertEquals(1, tlist.size());
        checkEquals(tlist.get(0), 100, "test1");

        ydb.execute("create table tbl2(x int, v string, primary key(x))");

        StreamSqlResult res = ydb.execute("insert into tbl2 select * from tbl1");
        Tuple t = res.next();
        assertEquals(1, t.getLongColumn("inserted"));

        tlist = fetchAllFromTable("tbl2");
        assertEquals(1, tlist.size());
        checkEquals(tlist.get(0), 100, "test1");

    }

    void checkEquals(Tuple t, int x, String v) {
        assertEquals(x, t.getIntColumn("x"));
        assertEquals(v, t.getColumn("v"));
    }
}
