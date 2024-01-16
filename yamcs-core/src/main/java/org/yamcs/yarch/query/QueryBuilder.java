package org.yamcs.yarch.query;

import java.util.List;

import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlStatement;

public interface QueryBuilder {

    String toSQL();

    StreamSqlStatement toStatement() throws ParseException, StreamSqlException;

    default Object sanitizeValue(Object value) {
        // Yamcs DB does not like empty arrays
        if (value instanceof List && ((List<?>) value).isEmpty()) {
            return null;
        } else {
            return value;
        }
    }
}
