package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.util.List;

import org.junit.jupiter.api.Test;

public class Enum2Test extends YarchTestCase {
    int n = 20;

    private void populate(String tblname) throws Exception {
        execute("create table " + tblname
                + "(packetName enum, gentime timestamp, packet binary, primary key(packetName, gentime))");
        execute("create stream " + tblname + "_in(gentime timestamp, packetName enum, packet binary)");
        execute("insert into " + tblname + " select * from " + tblname + "_in");

        Stream s = ydb.getStream(tblname + "_in");
        TupleDefinition td = new TupleDefinition();
        td.addColumn(new ColumnDefinition("gentime", DataType.TIMESTAMP));
        td.addColumn(new ColumnDefinition("packetName", DataType.ENUM));
        td.addColumn(new ColumnDefinition("packet", DataType.BINARY));

        for (int i = 0; i < n; i++) {
            ByteBuffer bb = ByteBuffer.allocate(2000);
            while (bb.remaining() > 0) {
                bb.putInt(i);
            }
            Tuple t = new Tuple(td, new Object[] {
                    1000L * i, "pn" + (i % 10), bb.array()
            });
            s.emitTuple(t);
        }
        execute("close stream " + tblname + "_in");
    }

    @Test
    public void test1() throws Exception {
        populate("testenum1");
        execute("create stream testenum_out as select * from testenum1");
        final List<Tuple> tuples = fetchAll("testenum_out");
        int k = 0;
        for (int j = 0; j < 10; j++) {
            for (int i = 0; i < n / 10; i++) {
                Tuple t = tuples.get(k);
                assertEquals((j + 10 * i) * 1000l, (long) (Long) t.getColumn(1));
                assertEquals("pn" + j, (String) t.getColumn(0));
                k++;
            }
        }
    }

    @Test
    public void test2() throws Exception {
        populate("testenum2");
        execute("create stream testenum2_out as select * from testenum2 where packetName in ('pn1', 'invalid')");

        final List<Tuple> tuples = fetchAll("testenum2_out");
        assertEquals((n + 9) / 10, tuples.size());

        int i = 1;
        for (Tuple t : tuples) {
            assertEquals(i * 1000l, (long) (Long) t.getColumn(1));
            assertEquals("pn" + (i % 10), (String) t.getColumn(0));
            i += 10;
        }
    }

    @Test
    public void test2Desc() throws Exception {
        populate("testenum2");
        execute("create stream testenum2_out as select * from testenum2 where  packetName in ('pn1', 'invalid')");

        final List<Tuple> tuples = fetchAll("testenum2_out");
        assertEquals((n + 9) / 10, tuples.size());

        int i = 1;
        for (Tuple t : tuples) {
            assertEquals(i * 1000l, (long) (Long) t.getColumn(1));
            assertEquals("pn" + (i % 10), (String) t.getColumn(0));
            i += 10;
        }
    }

    @Test
    public void test3() throws Exception {
        populate("testenum3");
        execute("create stream testenum3_out as select * from testenum3 where packetName in ('invalid')");
        final List<Tuple> tuples = fetchAll("testenum3_out");
        assertEquals(0, tuples.size());
    }

    @Test
    public void test3Desc() throws Exception {
        populate("testenum3");
        execute(
                "create stream testenum3_out as select * from testenum3 where packetName in ('invalid') order desc");
        final List<Tuple> tuples = fetchAll("testenum3_out");
        assertEquals(0, tuples.size());
    }

    @Test
    public void test4() throws Exception {
        populate("testenum4");
        execute("create stream testenum2_out as select * from testenum4 where packetName='pn1'");
        final List<Tuple> tuples = fetchAll("testenum2_out");
        assertEquals((n + 9) / 10, tuples.size());

        for (Tuple t : tuples) {
            assertEquals("pn1", (String) t.getColumn(0));
        }
    }

    @Test
    public void test4Desc() throws Exception {
        populate("testenum4");
        execute("create stream testenum2_out as select * from testenum4 where packetName='pn1' order desc");
        final List<Tuple> tuples = fetchAll("testenum2_out");
        assertEquals((n + 9) / 10, tuples.size());

        for (Tuple t : tuples) {
            assertEquals("pn1", (String) t.getColumn(0));
        }
    }
}
