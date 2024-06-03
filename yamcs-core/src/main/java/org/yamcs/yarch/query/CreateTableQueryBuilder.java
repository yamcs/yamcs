package org.yamcs.yarch.query;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.ArrayDataType;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.ProtobufDataType;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlParser;
import org.yamcs.yarch.streamsql.StreamSqlStatement;
import org.yamcs.yarch.streamsql.TokenMgrError;

public class CreateTableQueryBuilder implements QueryBuilder {

    private String table;
    private List<ColumnInfo> columns = new ArrayList<>();
    private String[] primaryKey;
    private String[] secondaryKey;

    public CreateTableQueryBuilder(String table) {
        this.table = table;
    }

    public CreateTableQueryBuilder withColumn(String name, DataType dataType) {
        columns.add(new ColumnInfo(name, dataType));
        return this;
    }

    public CreateTableQueryBuilder autoIncrement(String column) {
        var columnInfo = requireColumn(column);
        columnInfo.autoIncrement = true;
        return this;
    }

    public CreateTableQueryBuilder primaryKey(String... columns) {
        primaryKey = columns;
        return this;
    }

    public CreateTableQueryBuilder index(String... columns) {
        secondaryKey = columns;
        return this;
    }

    @Override
    public String toSQL() {
        var buf = new StringBuilder("CREATE TABLE ").append(table).append("(");

        var first = true;
        for (var columnInfo : columns) {
            if (!first) {
                buf.append(", ");
            }
            buf.append("\"").append(columnInfo.name).append("\" ");
            if (columnInfo.dataType instanceof ProtobufDataType) {
                var dataType = (ProtobufDataType) columnInfo.dataType;
                buf.append(String.format("PROTOBUF('%s')", dataType.getClassName()));
            } else if (columnInfo.dataType instanceof ArrayDataType) {
                var dataType = (ArrayDataType) columnInfo.dataType;
                buf.append(String.format("%s[]", dataType.getElementType()));
            } else {
                buf.append(columnInfo.dataType);
            }
            if (columnInfo.autoIncrement) {
                buf.append(" AUTO_INCREMENT");
            }
            first = false;
        }

        if (primaryKey != null) {
            buf.append(", PRIMARY KEY(\"");
            buf.append(String.join("\", \"", primaryKey));
            buf.append("\")");
        }
        if (secondaryKey != null) {
            buf.append(", INDEX(\"");
            buf.append(String.join("\", \"", secondaryKey));
            buf.append("\")");
        }

        buf.append(")");

        return buf.toString();
    }

    @Override
    public StreamSqlStatement toStatement() throws ParseException, StreamSqlException {
        var query = toSQL();

        var parser = new StreamSqlParser(new StringReader(query));
        try {
            return parser.OneStatement();
        } catch (TokenMgrError e) {
            throw new ParseException(e.getMessage());
        }
    }

    private ColumnInfo requireColumn(String column) {
        var match = columns.stream().filter(c -> c.name.equals(column)).findFirst();
        if (match.isPresent()) {
            return match.get();
        } else {
            throw new IllegalArgumentException("No column '" + column + "'");
        }
    }

    static class ColumnInfo {
        String name;
        DataType dataType;
        boolean autoIncrement;

        ColumnInfo(String name, DataType dataType) {
            this.name = name;
            this.dataType = dataType;
            this.autoIncrement = false;
        }
    }
}
