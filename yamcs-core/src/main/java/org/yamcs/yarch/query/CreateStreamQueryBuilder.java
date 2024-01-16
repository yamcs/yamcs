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

public class CreateStreamQueryBuilder implements QueryBuilder {

    private String stream;
    private List<ColumnInfo> columns = new ArrayList<>();

    public CreateStreamQueryBuilder(String stream) {
        this.stream = stream;
    }

    public CreateStreamQueryBuilder withColumn(String name, DataType dataType) {
        columns.add(new ColumnInfo(name, dataType));
        return this;
    }

    @Override
    public String toSQL() {
        var buf = new StringBuilder("CREATE STREAM ").append(stream).append("(");

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
            first = false;
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

    private static class ColumnInfo {
        String name;
        DataType dataType;

        ColumnInfo(String name, DataType dataType) {
            this.name = name;
            this.dataType = dataType;
        }
    }
}
