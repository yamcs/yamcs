package org.yamcs.yarch.query;

import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

/**
 * Helper class to programmatically build SQL queries.
 */
public class Query {

    public static CreateTableQueryBuilder createTable(String table) {
        return new CreateTableQueryBuilder(table);
    }

    public static CreateTableQueryBuilder createTable(String table, TupleDefinition tdef) {
        var b = new CreateTableQueryBuilder(table);
        for (var cdef : tdef.getColumnDefinitions()) {
            b.withColumn(cdef.getName(), cdef.getType());
        }
        return b;
    }

    public static CreateStreamQueryBuilder createStream(String stream) {
        return new CreateStreamQueryBuilder(stream);
    }

    public static CreateStreamQueryBuilder createStream(String stream, TupleDefinition tdef) {
        var b = new CreateStreamQueryBuilder(stream);
        for (var cdef : tdef.getColumnDefinitions()) {
            b.withColumn(cdef.getName(), cdef.getType());
        }
        return b;
    }

    public static SelectTableQueryBuilder selectTable(String table) {
        return new SelectTableQueryBuilder(table);
    }

    public static SelectStreamQueryBuilder selectStream(String stream) {
        return new SelectStreamQueryBuilder(stream);
    }

    public static InsertIntoTableQueryBuilder insertIntoTable(String table, String... columns) {
        return new InsertIntoTableQueryBuilder(table, columns);
    }

    public static InsertIntoTableQueryBuilder insertIntoTable(String table, Tuple tuple) {
        return new InsertIntoTableQueryBuilder(table, tuple);
    }

    public static InsertIntoTableQueryBuilder insertAppendIntoTable(String table, String... columns) {
        return new InsertIntoTableQueryBuilder(table, columns)
                .insertMode(InsertMode.INSERT_APPEND);
    }

    public static InsertIntoTableQueryBuilder insertAppendIntoTable(String table, Tuple tuple) {
        return new InsertIntoTableQueryBuilder(table, tuple)
                .insertMode(InsertMode.INSERT_APPEND);
    }

    public static InsertIntoTableQueryBuilder upsertIntoTable(String table, String... columns) {
        return new InsertIntoTableQueryBuilder(table, columns)
                .insertMode(InsertMode.UPSERT);
    }

    public static InsertIntoTableQueryBuilder upsertIntoTable(String table, Tuple tuple) {
        return new InsertIntoTableQueryBuilder(table, tuple)
                .insertMode(InsertMode.UPSERT);
    }

    public static InsertIntoTableQueryBuilder upsertAppendIntoTable(String table, String... columns) {
        return new InsertIntoTableQueryBuilder(table, columns)
                .insertMode(InsertMode.UPSERT_APPEND);
    }

    public static InsertIntoTableQueryBuilder upsertAppendIntoTable(String table, Tuple tuple) {
        return new InsertIntoTableQueryBuilder(table, tuple)
                .insertMode(InsertMode.UPSERT_APPEND);
    }

    public static UpdateTableQueryBuilder updateTable(String table) {
        return new UpdateTableQueryBuilder(table);
    }

    public static DeleteFromTableQueryBuilder deleteFromTable(String table) {
        return new DeleteFromTableQueryBuilder(table);
    }
}
