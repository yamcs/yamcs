package org.yamcs.yarch.query;

import java.io.StringReader;

import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlParser;
import org.yamcs.yarch.streamsql.StreamSqlStatement;
import org.yamcs.yarch.streamsql.TokenMgrError;

public class DeleteFromTableQueryBuilder implements QueryBuilder {

    private String table;
    private String whereClause;
    private Object whereParameter;

    public DeleteFromTableQueryBuilder(String table) {
        this.table = table;
    }

    public DeleteFromTableQueryBuilder where(String column, Object value) {
        whereClause = "\"" + column + "\" = ?";
        whereParameter = sanitizeValue(value);
        return this;
    }

    @Override
    public StreamSqlStatement toStatement() throws ParseException, StreamSqlException {
        var buf = new StringBuilder("DELETE FROM ").append(table);

        if (whereClause != null) {
            buf.append(" WHERE ").append(whereClause);
        }

        var query = buf.toString();
        var args = new Object[] { whereParameter };

        var parser = new StreamSqlParser(new StringReader(query));
        parser.setArgs(args);
        try {
            return parser.OneStatement();
        } catch (TokenMgrError e) {
            throw new ParseException(e.getMessage());
        }
    }
}
