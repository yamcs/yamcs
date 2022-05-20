package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.Test;

/**
 * test for insert modes in a table with a dynamic schema
 * 
 * @author nm
 *
 */
public class DynamicSchemaTableTest extends YarchTestCase {

    private void emit(Stream s, long key, String colName, int colValue) {
        TupleDefinition tdef = new TupleDefinition();
        tdef.addColumn("t", DataType.TIMESTAMP);
        tdef.addColumn(colName, DataType.INT);
        Tuple t = new Tuple(tdef, new Object[] { key, colValue });
        s.emitTuple(t);
    }

    private void emit(Stream s, long key, String colName, boolean colValue) {
        TupleDefinition tdef = new TupleDefinition();
        tdef.addColumn("t", DataType.TIMESTAMP);
        tdef.addColumn(colName, DataType.BOOLEAN);
        Tuple t = new Tuple(tdef, new Object[] { key, colValue });
        s.emitTuple(t);
    }

    private void emit(Stream s, long key, String colName, byte colValue) {
        TupleDefinition tdef = new TupleDefinition();
        tdef.addColumn("t", DataType.TIMESTAMP);
        tdef.addColumn(colName, DataType.BYTE);
        Tuple t = new Tuple(tdef, new Object[] { key, colValue });
        s.emitTuple(t);
    }

    private void emit(Stream s, long key, String colName, short colValue) {
        TupleDefinition tdef = new TupleDefinition();
        tdef.addColumn("t", DataType.TIMESTAMP);
        tdef.addColumn(colName, DataType.SHORT);
        Tuple t = new Tuple(tdef, new Object[] { key, colValue });
        s.emitTuple(t);
    }

    @Test
    public void testInsert() throws Exception {
        ydb.execute("create table test_insert (t timestamp, v1 int, v2 int, primary key(t))");
        ydb.execute("create stream test_insert_in (t timestamp)");
        ydb.execute("insert_append into test_insert select * from test_insert_in");

        TableDefinition tblDef = ydb.getTable("test_insert");

        List<TableColumnDefinition> keyCols = tblDef.getKeyDefinition();
        assertEquals(1, keyCols.size());

        List<TableColumnDefinition> valueCols = tblDef.getValueDefinition();
        assertEquals(2, valueCols.size());

        Stream s = ydb.getStream("test_insert_in");
        emit(s, 1, "v1", 1);
        emit(s, 1, "v2", 2);
        emit(s, 1, "v3", 3);
        emit(s, 2, "v3", 3);

        emit(s, 2, "v4", true);
        emit(s, 2, "v5", (byte) 5);
        emit(s, 2, "v6", (short) 6);

        valueCols = tblDef.getValueDefinition();
        assertEquals(6, valueCols.size());
        assertEquals("v3", valueCols.get(2).getName());
        assertEquals("v6", valueCols.get(5).getName());

        ydb.execute("create stream test_insert_out as select * from test_insert");
        Stream sout = ydb.getStream("test_insert_out");
        final Semaphore semaphore = new Semaphore(0);
        final ArrayList<Tuple> tuples = new ArrayList<>();
        sout.addSubscriber(new StreamSubscriber() {
            @Override
            public void streamClosed(Stream stream) {
                semaphore.release();
            }

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                tuples.add(tuple);
            }
        });
        sout.start();
        semaphore.acquire();
        assertEquals(2, tuples.size());

        Tuple t = tuples.get(0);
        assertEquals(4, t.getColumns().size());
        assertEquals(1L, ((Long) t.getColumn("t")).longValue());
        assertEquals(1, ((Integer) t.getColumn("v1")).intValue());

        t = tuples.get(1);
        assertEquals(5, t.getColumns().size());
        assertEquals(2L, ((Long) t.getColumn("t")).longValue());
        assertEquals(3, ((Integer) t.getColumn("v3")).intValue());

        assertEquals(true, ((Boolean) t.getColumn("v4")).booleanValue());
        assertEquals(5, ((Byte) t.getColumn("v5")).byteValue());
        assertEquals(6, ((Short) t.getColumn("v6")).shortValue());

        execute("drop table test_insert");
    }

    @Test
    public void testInsertAppend() throws Exception {
        ydb.execute("create table test_inserta (t timestamp, v1 int, v2 int, primary key(t))");
        ydb.execute("create stream test_inserta_in (t timestamp)");
        ydb.execute("insert_append into test_inserta select * from test_inserta_in");

        TableDefinition tblDef = ydb.getTable("test_inserta");

        List<TableColumnDefinition> keyCols = tblDef.getKeyDefinition();
        assertEquals(1, keyCols.size());

        List<TableColumnDefinition> valueCols = tblDef.getValueDefinition();
        assertEquals(2, valueCols.size());

        Stream s = ydb.getStream("test_inserta_in");
        emit(s, 1, "v1", 1);
        emit(s, 1, "v2", 2);
        emit(s, 1, "v3", 3);
        emit(s, 1, "v3", 4);
        emit(s, 2, "v3", 30);

        valueCols = tblDef.getValueDefinition();
        assertEquals(3, valueCols.size());
        assertEquals("v3", valueCols.get(2).getName());

        execute("create stream test_inserta_out as select * from test_inserta");
        Stream sout = ydb.getStream("test_inserta_out");
        final Semaphore semaphore = new Semaphore(0);
        final ArrayList<Tuple> tuples = new ArrayList<>();
        sout.addSubscriber(new StreamSubscriber() {
            @Override
            public void streamClosed(Stream stream) {
                semaphore.release();
            }

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                tuples.add(tuple);
            }
        });
        sout.start();
        semaphore.acquire();
        assertEquals(2, tuples.size());
        Tuple t = tuples.get(0);
        assertEquals(4, t.getColumns().size());
        assertEquals(1L, ((Long) t.getColumn("t")).longValue());
        assertEquals(1, ((Integer) t.getColumn("v1")).intValue());
        assertEquals(2, ((Integer) t.getColumn("v2")).intValue());
        assertEquals(3, ((Integer) t.getColumn("v3")).intValue());

        t = tuples.get(1);
        assertEquals(2, t.getColumns().size());
        assertEquals(2L, ((Long) t.getColumn("t")).longValue());
        assertEquals(30, ((Integer) t.getColumn("v3")).intValue());
        execute("drop table test_inserta");
    }

    @Test
    public void testUpsert() throws Exception {

        ydb.execute("create table test_upsert (t timestamp, v1 int, v2 int, primary key(t))");

        ydb.execute("create stream test_upsert_in (t timestamp)");
        ydb.execute("upsert into test_upsert select * from test_upsert_in");

        TableDefinition tblDef = ydb.getTable("test_upsert");

        List<TableColumnDefinition> keyCols = tblDef.getKeyDefinition();
        assertEquals(1, keyCols.size());

        List<TableColumnDefinition> valueCols = tblDef.getValueDefinition();
        assertEquals(2, valueCols.size());

        Stream s = ydb.getStream("test_upsert_in");
        emit(s, 1, "v1", 1);
        emit(s, 1, "v2", 2);
        emit(s, 1, "v3", 3);
        emit(s, 2, "v3", 3);

        valueCols = tblDef.getValueDefinition();
        assertEquals(3, valueCols.size());
        assertEquals("v3", valueCols.get(2).getName());

        ydb.execute("create stream test_upsert_out as select * from test_upsert");
        Stream sout = ydb.getStream("test_upsert_out");
        final Semaphore semaphore = new Semaphore(0);
        final ArrayList<Tuple> tuples = new ArrayList<>();
        sout.addSubscriber(new StreamSubscriber() {
            @Override
            public void streamClosed(Stream stream) {
                semaphore.release();
            }

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                tuples.add(tuple);
            }
        });
        sout.start();
        semaphore.acquire();
        assertEquals(2, tuples.size());
        Tuple t = tuples.get(0);
        assertEquals(2, t.getColumns().size());
        assertEquals(1L, ((Long) t.getColumn("t")).longValue());
        assertEquals(3, ((Integer) t.getColumn("v3")).intValue());

        t = tuples.get(1);
        assertEquals(2, t.getColumns().size());
        assertEquals(2L, ((Long) t.getColumn("t")).longValue());
        assertEquals(3, ((Integer) t.getColumn("v3")).intValue());

        execute("drop table test_upsert");
    }

    @Test
    public void testUpsertAppend() throws Exception {

        ydb.execute("create table test_upserta (t timestamp, v1 int, v2 int, primary key(t))");

        ydb.execute("create stream test_upserta_in (t timestamp)");
        ydb.execute("upsert_append into test_upserta select * from test_upserta_in");

        TableDefinition tblDef = ydb.getTable("test_upserta");

        List<TableColumnDefinition> keyCols = tblDef.getKeyDefinition();
        assertEquals(1, keyCols.size());

        List<TableColumnDefinition> valueCols = tblDef.getValueDefinition();
        assertEquals(2, valueCols.size());

        Stream s = ydb.getStream("test_upserta_in");
        emit(s, 1, "v1", 1);
        emit(s, 1, "v2", 2);
        emit(s, 1, "v3", 3);
        emit(s, 1, "v3", 4);
        emit(s, 2, "v3", 3);

        valueCols = tblDef.getValueDefinition();
        assertEquals(3, valueCols.size());
        assertEquals("v3", valueCols.get(2).getName());

        ydb.execute("create stream test_upserta_out as select * from test_upserta");
        Stream sout = ydb.getStream("test_upserta_out");
        final Semaphore semaphore = new Semaphore(0);
        final ArrayList<Tuple> tuples = new ArrayList<>();
        sout.addSubscriber(new StreamSubscriber() {
            @Override
            public void streamClosed(Stream stream) {
                semaphore.release();
            }

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                tuples.add(tuple);
            }
        });
        sout.start();
        semaphore.acquire();
        assertEquals(2, tuples.size());
        Tuple t = tuples.get(0);
        assertEquals(4, t.getColumns().size());
        assertEquals(1L, ((Long) t.getColumn("t")).longValue());
        assertEquals(1, ((Integer) t.getColumn("v1")).intValue());
        assertEquals(2, ((Integer) t.getColumn("v2")).intValue());
        assertEquals(4, ((Integer) t.getColumn("v3")).intValue());

        t = tuples.get(1);
        assertEquals(2, t.getColumns().size());
        assertEquals(2L, ((Long) t.getColumn("t")).longValue());
        assertEquals(3, ((Integer) t.getColumn("v3")).intValue());

        ydb.execute("drop table test_upserta");
    }
}
