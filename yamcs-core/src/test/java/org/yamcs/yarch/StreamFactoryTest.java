package org.yamcs.yarch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.api.StreamFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.utils.ValueUtility;
import org.yamcs.utils.parser.ParseException;

public class StreamFactoryTest extends YarchTestCase {
    private static final String PARAMETER_NAME = "/yamcs/test-host/processor/name";
    private static final String SEQCOUNT_NAME = "ccsds-seqcount";

    private TupleDefinition definition() {
        TupleDefinition td = new TupleDefinition();
        td.addColumn("id", DataType.INT);
        td.addColumn(PARAMETER_NAME, DataType.PARAMETER_VALUE);
        td.addColumn(SEQCOUNT_NAME, DataType.INT);
        return td;
    }

    private Tuple tuple(int id) {
        ParameterValue pv = new ParameterValue(PARAMETER_NAME);
        pv.setEngValue(ValueUtility.getStringValue("processor" + id));
        return new Tuple(definition(), new Object[] { id, pv, id + 100 });
    }

    private void writeAndClose(Stream stream, int id) {
        assertEquals(DataType.INT, stream.getColumnDefinition("id").getType());
        stream.emitTuple(tuple(id));
        stream.close();
        assertTrue(stream.isClosed());
        assertNull(ydb.getStream(stream.getName()));
    }

    private void verifyRows(int count) throws Exception {
        TableDefinition table = ydb.getTable("special_columns");
        assertEquals(DataType.PARAMETER_VALUE, table.getColumnDefinition(PARAMETER_NAME).getType());
        assertEquals(DataType.INT, table.getColumnDefinition(SEQCOUNT_NAME).getType());
        List<Tuple> rows = fetchAllFromTable("special_columns");
        assertEquals(count, rows.size());
        for (int i = 0; i < count; i++) {
            Tuple row = rows.get(i);
            assertEquals(i, (Integer) row.getColumn("id"));
            assertEquals(i + 100, (Integer) row.getColumn(SEQCOUNT_NAME));
            ParameterValue pv = (ParameterValue) row.getColumn(PARAMETER_NAME);
            assertEquals(PARAMETER_NAME, pv.getParameterQualifiedName());
            assertEquals("processor" + i, pv.getEngValue().getStringValue());
        }
    }

    @Test
    public void testLoadExistingSpecialColumns() throws Exception {
        ydb.createTable(new TableDefinition("special_columns", definition(), List.of("id")));
        reloadDb();
        writeAndClose(StreamFactory.loadStream(instance, ydb.getTable("special_columns")), 0);
        verifyRows(1);
    }

    @Test
    public void testLoadDynamicColumnsAgainAndAfterReload() throws Exception {
        execute("create table special_columns(id int, primary key(id))");
        writeAndClose(StreamFactory.loadStream(instance, ydb.getTable("special_columns")), 0);
        writeAndClose(StreamFactory.loadStream(instance, ydb.getTable("special_columns")), 1);
        reloadDb();
        writeAndClose(StreamFactory.loadStream(instance, ydb.getTable("special_columns")), 2);
        verifyRows(3);
    }

    @Test
    public void testInsertExistingSpecialColumns() throws Exception {
        ydb.createTable(new TableDefinition("special_columns", definition(), List.of("id")));
        reloadDb();
        writeAndClose(StreamFactory.insertStream(instance, ydb.getTable("special_columns")), 0);
        verifyRows(1);
    }

    @Test
    public void testWriterSetupFailureClosesStream() throws Exception {
        ydb.createTable(new TableDefinition("special_columns", definition(), List.of("id")));
        TableDefinition table = ydb.getTable("special_columns");
        ydb.dropTable(table.getName());
        int streamsBefore = ydb.getStreams().size();
        assertThrows(InternalServerErrorException.class, () -> StreamFactory.loadStream(instance, table));
        assertEquals(streamsBefore, ydb.getStreams().size());
        assertThrows(InternalServerErrorException.class, () -> StreamFactory.insertStream(instance, table));
        assertEquals(streamsBefore, ydb.getStreams().size());
    }

    @Test
    public void testSqlIdentifierRestrictionsUnchanged() {
        for (String name : List.of(PARAMETER_NAME, SEQCOUNT_NAME)) {
            assertThrows(ParseException.class,
                    () -> ydb.createStatement("create stream rejected(\"" + name + "\" int)"));
        }
    }
}
