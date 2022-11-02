package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestRenameTable extends YarchTestCase {

    TupleDefinition tdef;

    @BeforeEach
    public void before() {
        tdef = new TupleDefinition();
        tdef.addColumn("t", DataType.TIMESTAMP);
        tdef.addColumn("v", DataType.INT);
    }

    @Test
    public void test1() throws Exception {
        ydb.execute("create table test1 (t timestamp, v int, primary key(t))");

        ydb.execute("create stream in_stream (t timestamp)");
        ydb.execute("insert_append into test1 select * from in_stream");

        Stream s = ydb.getStream("in_stream");

        emit(s, 1, 100);
        Tuple t = ydb.execute("alter table test1 rename to test2").next();
        assertEquals("test1", t.getColumn("oldName"));
        assertEquals("test2", t.getColumn("newName"));

        emit(s, 2, 200);

        List<Tuple> tlist = fetchAllFromTable("test2");
        assertEquals(2, tlist.size());
        assertEquals(100, tlist.get(0).getIntColumn("v"));
        assertEquals(200, tlist.get(1).getIntColumn("v"));
    }

    private void emit(Stream s, long t, int v) {
        s.emitTuple(new Tuple(tdef, Arrays.asList(t, v)));
    }
}
