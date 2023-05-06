package org.yamcs.yarch.query;

import java.io.StringReader;

import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlParser;
import org.yamcs.yarch.streamsql.StreamSqlStatement;
import org.yamcs.yarch.streamsql.TokenMgrError;

public class InsertIntoTableQueryBuilder implements QueryBuilder {

    private String table;
    private String[] columns;
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

    public InsertIntoTableQueryBuilder values(Object... values) {
        this.values = new Object[columns.length];
        for (var i = 0; i < columns.length; i++) {
            this.values[i] = sanitizeValue(values[i]);
        }
        return this;
    }

    @Override
    public StreamSqlStatement toStatement() throws ParseException, StreamSqlException {
        var buf = new StringBuilder("INSERT INTO ").append(table).append("(");

        if (columns.length > 0) {
            buf.append("\"").append(String.join("\", \"", columns)).append("\"");
        }

        buf.append(") VALUES (");

        for (var i = 0; i < values.length; i++) {
            buf.append(i == 0 ? "?" : ", ?");
        }

        buf.append(")");

        var query = buf.toString();

        var parser = new StreamSqlParser(new StringReader(query));
        parser.setArgs(values);
        try {
            return parser.OneStatement();
        } catch (TokenMgrError e) {
            throw new ParseException(e.getMessage());
        }
    }
}
