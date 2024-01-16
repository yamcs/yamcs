package org.yamcs.yarch.query;

import java.io.StringReader;

import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlParser;
import org.yamcs.yarch.streamsql.StreamSqlStatement;
import org.yamcs.yarch.streamsql.TokenMgrError;

public class SelectStreamQueryBuilder implements QueryBuilder {

    private String stream;
    private String whereClause;
    private Object whereParameter;

    public SelectStreamQueryBuilder(String stream) {
        this.stream = stream;
    }

    public SelectStreamQueryBuilder where(String column, Object value) {
        whereClause = "\"" + column + "\" = ?";
        whereParameter = sanitizeValue(value);
        return this;
    }

    @Override
    public String toSQL() {
        var buf = new StringBuilder("SELECT * FROM ").append(stream);

        if (whereClause != null) {
            buf.append(" WHERE ").append(whereClause);
        }

        return buf.toString();
    }

    @Override
    public StreamSqlStatement toStatement() throws ParseException, StreamSqlException {
        var query = toSQL();
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
