package org.yamcs.yarch.query;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlParser;
import org.yamcs.yarch.streamsql.StreamSqlStatement;
import org.yamcs.yarch.streamsql.TokenMgrError;

public class UpdateTableQueryBuilder implements QueryBuilder {

    private String table;
    private List<String> setClauses = new ArrayList<>();
    private List<Object> setParameters = new ArrayList<>();
    private String whereClause;
    private List<Object> whereParameters = new ArrayList<>();

    public UpdateTableQueryBuilder(String table) {
        this.table = table;
    }

    public UpdateTableQueryBuilder set(String column, Object value) {
        setClauses.add("\"" + column + "\" = ?");
        setParameters.add(sanitizeValue(value));
        return this;
    }

    public UpdateTableQueryBuilder set(Tuple tuple) {
        for (int i = 0; i < tuple.size(); i++) {
            var cdef = tuple.getColumnDefinition(i);
            set(cdef.getName(), tuple.getColumn(i));
        }
        return this;
    }

    public UpdateTableQueryBuilder where(String column, Object value) {
        value = sanitizeValue(value);

        if (value == null) {
            whereClause = "\"" + column + "\" IS NULL";
        } else {
            whereClause = "\"" + column + "\" = ?";
            whereParameters.add(value);
        }

        return this;
    }

    @Override
    public String toSQL() {
        var buf = new StringBuilder("UPDATE ").append(table);

        if (!setClauses.isEmpty()) {
            buf.append(" SET ").append(String.join(", ", setClauses));
        }

        if (whereClause != null) {
            buf.append(" WHERE ").append(whereClause);
        }

        return buf.toString();
    }

    @Override
    public StreamSqlStatement toStatement() throws ParseException, StreamSqlException {
        var query = toSQL();
        var args = Stream.concat(setParameters.stream(), whereParameters.stream()).toArray();

        var parser = new StreamSqlParser(new StringReader(query));
        parser.setArgs(args);
        try {
            return parser.OneStatement();
        } catch (TokenMgrError e) {
            throw new ParseException(e.getMessage());
        }
    }
}
