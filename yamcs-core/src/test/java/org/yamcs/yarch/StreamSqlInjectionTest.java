package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.yamcs.yarch.streamsql.StreamSqlResult;

/**
 * Column names and PROTOBUF class names must not be able to inject code in the Java source compiled for StreamSQL
 * expressions.
 */
public class StreamSqlInjectionTest extends YarchTestCase {

    static final String PROPERTY = "4711";

    @AfterEach
    public void clearProperty() {
        System.clearProperty(PROPERTY);
    }

    private void emit(Stream s, long t, String colName, int value) {
        TupleDefinition tdef = new TupleDefinition();
        tdef.addColumn("t", DataType.TIMESTAMP);
        tdef.addColumn("v", DataType.INT);
        tdef.addColumn(colName, DataType.INT);
        s.emitTuple(new Tuple(tdef, new Object[] { t, 1, value }));
    }

    private long sumV(String table, String... colNames) throws Exception {
        ydb.execute("create table " + table + " (t timestamp, v int, primary key(t))");
        ydb.execute("create stream " + table + "_in (t timestamp)");
        ydb.execute("insert_append into " + table + " select * from " + table + "_in");

        Stream s = ydb.getStream(table + "_in");
        for (int i = 0; i < colNames.length; i++) {
            emit(s, i, colNames[i], i);
        }
        StreamSqlResult r = ydb.execute("select sum(v) from " + table);
        long sum = ((Number) r.next().getColumn(0)).longValue();
        r.close();
        return sum;
    }

    @Test
    public void testColumnName() throws Exception {
        assertEquals(2, sumV("inj", "a = 0; System.setProperty(Integer.toString(4711), Integer.toString(1)); Integer b",
                "\\u0022); System.setProperty(Integer.toString(4711), Integer.toString(1)); //"));
        assertNull(System.getProperty(PROPERTY));
    }

    @Test
    public void testSimilarColumnNames() throws Exception {
        assertEquals(2, sumV("similar", "/YSS/x-y", "_YSS_x_y"));
    }

    @Test
    public void testProtobufClassName() {
        assertThrows(Exception.class, () -> ydb.execute(
                "create stream pinj (x PROTOBUF('Object o; } static { System.setProperty(\"4711\", \"1\"); } "
                        + "public void inj(Tuple tuple) { //'), g int)"));
        assertThrows(Exception.class, () -> DataType.byName("PROTOBUF(a b)"));
        assertNull(System.getProperty(PROPERTY));
    }
}
