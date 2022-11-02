package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.yamcs.time.Instant;

public class NegativeValuesPKTest extends YarchTestCase {
    int idFirst = -5;
    int idLast = 10;

    private void populate(String tblname, String type) throws Exception {
        ydb.execute("create table " + tblname + "(id " + type + ", name string, primary key(id))");
        ydb.execute("create stream " + tblname + "_in(id int, name string)");
        ydb.execute("insert into " + tblname + " select * from " + tblname + "_in");

        Stream s = ydb.getStream(tblname + "_in");
        TupleDefinition td = new TupleDefinition();
        td.addColumn(new ColumnDefinition("id", DataType.INT));
        td.addColumn(new ColumnDefinition("name", DataType.STRING));

        for (int i = idFirst; i <= idLast; i++) {
            Tuple t = new Tuple(td, new Object[] {
                    i, "id" + i
            });
            s.emitTuple(t);
        }
        execute("close stream " + tblname + "_in");
    }

    @Test
    public void testInt() throws Exception {
        populate("testint", "int");
        ydb.execute("create stream test_out as select * from testint");
        final List<Tuple> tuples = fetchAll("test_out");

        for (int i = idFirst; i <= idLast; i++) {
            Tuple t = tuples.get(i - idFirst);
            assertEquals(i, (int) t.getColumn(0));
            assertEquals("id" + i, (String) t.getColumn(1));
        }
    }

    @Test
    public void testShort() throws Exception {
        populate("testshort", "short");
        ydb.execute("create stream test_out as select * from testshort");
        final List<Tuple> tuples = fetchAll("test_out");

        for (int i = idFirst; i <= idLast; i++) {
            Tuple t = tuples.get(i - idFirst);
            assertEquals(i, (short) t.getColumn(0));
            assertEquals("id" + i, (String) t.getColumn(1));
        }
    }

    @Test
    public void testLong() throws Exception {
        populate("testlong", "long");
        ydb.execute("create stream test_out as select * from testlong");
        final List<Tuple> tuples = fetchAll("test_out");

        for (int i = idFirst; i <= idLast; i++) {
            Tuple t = tuples.get(i - idFirst);
            assertEquals(i, (long) t.getColumn(0));
            assertEquals("id" + i, (String) t.getColumn(1));
        }
    }

    @Test
    public void testDouble() throws Exception {
        populate("testdouble", "double");
        ydb.execute("create stream test_out as select * from testdouble");
        final List<Tuple> tuples = fetchAll("test_out");

        for (int i = idFirst; i <= idLast; i++) {
            Tuple t = tuples.get(i - idFirst);
            assertEquals(i, (double) t.getColumn(0), 1e-10);
            assertEquals("id" + i, (String) t.getColumn(1));
        }
    }

    @Test
    public void testTimestamp() throws Exception {
        populate("testtimestamp", "timestamp");
        ydb.execute("create stream test_out as select * from testtimestamp");
        final List<Tuple> tuples = fetchAll("test_out");

        for (int i = idFirst; i <= idLast; i++) {
            Tuple t = tuples.get(i - idFirst);
            assertEquals(i, (long) t.getColumn(0));
            assertEquals("id" + i, (String) t.getColumn(1));
        }
    }

    @Test
    public void testHrTimestamp() throws Exception {
        populate("testhrtimestamp", "hres_timestamp");
        ydb.execute("create stream test_out as select * from testhrtimestamp");
        final List<Tuple> tuples = fetchAll("test_out");

        for (int i = idFirst; i <= idLast; i++) {
            Tuple t = tuples.get(i - idFirst);
            assertEquals(i, ((Instant) t.getColumn(0)).getMillis());
            assertEquals("id" + i, (String) t.getColumn(1));
        }
    }
}
