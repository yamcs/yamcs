package org.yamcs.yarch.query;

import org.yamcs.yarch.Tuple;

/**
 * Helper class to programmatically build SQL queries.
 */
public class Query {

    public static CreateTableQueryBuilder createTable(String table) {
        return new CreateTableQueryBuilder(table);
    }

    public static SelectTableQueryBuilder selectTable(String table) {
        return new SelectTableQueryBuilder(table);
    }

    public static InsertIntoTableQueryBuilder insertIntoTable(String table, String... columns) {
        return new InsertIntoTableQueryBuilder(table, columns);
    }

    public static InsertIntoTableQueryBuilder insertIntoTable(String table, Tuple tuple) {
        return new InsertIntoTableQueryBuilder(table, tuple);
    }

    public static UpdateTableQueryBuilder updateTable(String table) {
        return new UpdateTableQueryBuilder(table);
    }

    public static DeleteFromTableQueryBuilder deleteFromTable(String table) {
        return new DeleteFromTableQueryBuilder(table);
    }
}
