package org.yamcs.yarch.query;

import java.io.StringReader;

import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.TableWriter.InsertMode;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlParser;
import org.yamcs.yarch.streamsql.StreamSqlStatement;
import org.yamcs.yarch.streamsql.TokenMgrError;

public class InsertIntoTableQueryBuilder implements QueryBuilder {

    private InsertMode insertMode = InsertMode.INSERT;
    private String table;
    private String[] columns;

    private String query;
    private Object[] values;

    public InsertIntoTableQueryBuilder(String table, String... columns) {
        this.table = table;
        this.columns = columns;
    }

    public InsertIntoTableQueryBuilder(String table, Tuple tuple) {
        this.table = table;
        columns = new String[tuple.size()];
        values = new Object[tuple.size()];
        for (int i = 0; i < tuple.size(); i++) {
            var cdef = tuple.getColumnDefinition(i);
            columns[i] = cdef.getName();
            values[i] = sanitizeValue(tuple.getColumn(i));
        }
    }

    public InsertIntoTableQueryBuilder insertMode(InsertMode insertMode) {
        this.insertMode = insertMode;
        return this;
    }

    public InsertIntoTableQueryBuilder values(Object... values) {
        if (query != null) {
            throw new IllegalArgumentException("Cannot insert values when a query is already specified");
        }
        this.values = new Object[columns.length];
        for (var i = 0; i < columns.length; i++) {
            this.values[i] = sanitizeValue(values[i]);
        }
        return this;
    }

    public InsertIntoTableQueryBuilder query(String query) {
        if (values != null) {
            throw new IllegalArgumentException("Cannot insert query when values are already specified");
        }
        this.query = query;
        return this;
    }

    @Override
    public String toSQL() {
        var buf = new StringBuilder(insertMode.name())
                .append(" INTO ")
                .append(table);

        if (values != null) {
            buf.append("(");

            if (columns.length > 0) {
                buf.append("\"").append(String.join("\", \"", columns)).append("\"");
            }

            buf.append(") VALUES (");

            for (var i = 0; i < values.length; i++) {
                buf.append(i == 0 ? "?" : ", ?");
            }

            buf.append(")");
        } else if (query != null) {
            buf.append(" ").append(query);
        } else {
            throw new IllegalStateException("Nothing to insert");
        }

        return buf.toString();
    }

    @Override
    public StreamSqlStatement toStatement() throws ParseException, StreamSqlException {
        var query = toSQL();
        var parser = new StreamSqlParser(new StringReader(query));
        parser.setArgs(values);
        try {
            return parser.OneStatement();
        } catch (TokenMgrError e) {
            throw new ParseException(e.getMessage());
        }
    }
}
