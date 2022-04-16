package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.yamcs.utils.TimeEncoding;

public class WriterTest extends YarchTestCase {
    int n = 10;

    @Test
    public void TestUpsert() throws Exception {
        execute("create table tbl_upsert"
                + "(gentime timestamp, packetName enum, packet binary, primary key(gentime,packetName))");
        execute("create stream tbl_upsert_in(gentime timestamp, packetName enum, packet binary)");
        execute("upsert into tbl_upsert select * from tbl_upsert_in");

        Stream s = ydb.getStream("tbl_upsert_in");
        TupleDefinition td = new TupleDefinition();
        td.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
        td.addColumn(new ColumnDefinition("packetName", DataType.ENUM));
        td.addColumn(new ColumnDefinition("packet", DataType.BINARY));

        Random random = new Random();
        byte[] b1 = new byte[1000];
        random.nextBytes(b1);
        s.emitTuple(new Tuple(td, new Object[] { 1000l, "pn1", b1 }));
        s.emitTuple(new Tuple(td, new Object[] { 1000l, "pn2", b1 }));

        byte[] b2 = new byte[500];
        random.nextBytes(b2);
        s.emitTuple(new Tuple(td, new Object[] { 1000l, "pn1", b2 }));

        execute("close stream tbl_upsert_in");

        execute("create stream tbl_upsert_out as select * from tbl_upsert");
        final List<Tuple> tuples = fetchAll("tbl_upsert_out");
        assertEquals(2, tuples.size());

        Tuple t1 = tuples.get(0);
        assertEquals("pn1", t1.getColumn("packetName"));
        assertTrue(Arrays.equals(b2, (byte[]) t1.getColumn("packet")));

        Tuple t2 = tuples.get(1);
        assertEquals("pn2", t2.getColumn("packetName"));
        assertTrue(Arrays.equals(b1, (byte[]) t2.getColumn("packet")));

        execute("drop table tbl_upsert");
    }

    @Test
    public void TestUpsertAppend() throws Exception {
        long t = TimeEncoding.parse("2017-11-17T13:48:33.323");
        execute("create table tbl_upsert_append "
                + "(gentime timestamp, name string, seq int, primary key(gentime,name, seq))");
        execute("create stream tbl_upsert_append_in(gentime timestamp)");
        execute("upsert_append into tbl_upsert_append select * from tbl_upsert_append_in");

        Stream s = ydb.getStream("tbl_upsert_append_in");
        TupleDefinition td = new TupleDefinition();
        td.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
        td.addColumn(new ColumnDefinition("name", DataType.STRING));
        td.addColumn(new ColumnDefinition("seq", DataType.INT));

        TupleDefinition td1 = td.copy();
        td1.addColumn(new ColumnDefinition("p1", DataType.STRING));
        s.emitTuple(new Tuple(td1, new Object[] { t, "pn1", 1, "v1" }));
        s.emitTuple(new Tuple(td1, new Object[] { t, "pn2", 1, "v2" }));

        TupleDefinition td2 = td.copy();
        td2.addColumn(new ColumnDefinition("p2", DataType.STRING));
        s.emitTuple(new Tuple(td2, new Object[] { t, "pn1", 1, "v3" }));

        s.emitTuple(new Tuple(td1, new Object[] { t + 1000, "pn1", 2, "v4" }));
        execute("close stream tbl_upsert_append_in");

        execute("create stream tbl_upsert_append_out as select * from tbl_upsert_append");
        final List<Tuple> tuples = fetchAll("tbl_upsert_append_out");
        assertEquals(3, tuples.size());

        Tuple t1 = tuples.get(0);
        assertEquals("pn1", t1.getColumn("name"));
        assertEquals("v1", (String) t1.getColumn("p1"));
        assertEquals("v3", (String) t1.getColumn("p2"));

        Tuple t2 = tuples.get(1);
        assertEquals("pn2", t2.getColumn("name"));
        assertEquals("v2", (String) t2.getColumn("p1"));

        execute("drop table tbl_upsert_append");
    }

    @Test
    public void TestUpsertAppendParralel() throws Exception {
        long t = TimeEncoding.parse("2019-11-17T13:48:33.323");
        execute("create table tbl_upsert_append "
                + "(gentime timestamp, name string, seq int, primary key(gentime,name, seq))");
        execute("create stream tbl_upsert_append_in(gentime timestamp)");
        execute("upsert_append into tbl_upsert_append select * from tbl_upsert_append_in");

        Stream s = ydb.getStream("tbl_upsert_append_in");
        TupleDefinition td = new TupleDefinition();
        td.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
        td.addColumn(new ColumnDefinition("name", DataType.STRING));
        td.addColumn(new ColumnDefinition("seq", DataType.INT));

        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            int x = i;
            threads[i] = new Thread() {
                @Override
                public void run() {
                    TupleDefinition td1 = td.copy();
                    td1.addColumn(new ColumnDefinition("p" + x, DataType.STRING));
                    s.emitTuple(new Tuple(td1, new Object[] { t, "pn1", 1, "v" + x }));
                }
            };
            threads[i].start();
        }
        for (int i = 0; i < 10; i++) {
            threads[i].join();
        }
        execute("close stream tbl_upsert_append_in");

        execute("create stream tbl_upsert_append_out as select * from tbl_upsert_append");
        final List<Tuple> tuples = fetchAll("tbl_upsert_append_out");
        assertEquals(1, tuples.size());

        Tuple t1 = tuples.get(0);
        assertEquals("pn1", t1.getColumn("name"));
        for (int i = 0; i < 10; i++) {
            assertEquals("v" + i, (String) t1.getColumn("p" + i));
        }

        execute("drop table tbl_upsert_append");
    }
}
