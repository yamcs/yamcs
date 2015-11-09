package org.yamcs.web.rest;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.protobuf.Archive.ColumnData;
import org.yamcs.protobuf.Archive.ColumnInfo;
import org.yamcs.protobuf.Archive.StreamData;
import org.yamcs.protobuf.Archive.StreamInfo;
import org.yamcs.protobuf.Archive.TableInfo;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;

import com.google.protobuf.ByteString;

/**
 * Collects all archive-related conversions performed in the web api
 * (x towards archive.proto)
 */
public final class ArchiveAssembler {

    public static TableInfo toTableInfo(TableDefinition def) {
        TableInfo.Builder infob = TableInfo.newBuilder();
        infob.setName(def.getName());
        for (ColumnDefinition cdef : def.getKeyDefinition().getColumnDefinitions()) {
            infob.addKeyColumn(toColumnInfo(cdef));
        }
        for (ColumnDefinition cdef : def.getValueDefinition().getColumnDefinitions()) {
            infob.addValueColumn(toColumnInfo(cdef));
        }
        return infob.build();
    }
    
    public static StreamInfo toStreamInfo(Stream stream) {
        StreamInfo.Builder infob = StreamInfo.newBuilder();
        infob.setName(stream.getName());
        for (ColumnDefinition cdef : stream.getDefinition().getColumnDefinitions()) {
            infob.addColumn(toColumnInfo(cdef));
        }
        return infob.build();
    }
    
    private static ColumnInfo toColumnInfo(ColumnDefinition cdef) {
        ColumnInfo.Builder infob = ColumnInfo.newBuilder();
        infob.setName(cdef.getName());
        infob.setType(cdef.getType().toString());
        return infob.build();
    }
    
    public static StreamData toStreamData(Stream stream, Tuple tuple) {
        StreamData.Builder builder = StreamData.newBuilder();
        builder.setStream(stream.getName());
        builder.addAllColumn(toColumnDataList(tuple));
        return builder.build();
    }
    
    public static List<ColumnData> toColumnDataList(Tuple tuple) {
        List<ColumnData> result = new ArrayList<>();
        int i = 0;
        for (Object column : tuple.getColumns()) {
            ColumnDefinition cdef = tuple.getColumnDefinition(i);
            
            Value.Builder v = Value.newBuilder();
            switch (cdef.getType().val) {
            case SHORT:
                v.setType(Type.SINT32);
                v.setSint32Value((Short) column);
                break;
            case DOUBLE:
                v.setType(Type.DOUBLE);
                v.setDoubleValue((Double) column);
                break;
            case BINARY:
                v.setType(Type.BINARY);
                v.setBinaryValue(ByteString.copyFrom((byte[]) column));
                break;
            case INT:
                v.setType(Type.SINT32);
                v.setSint32Value((Integer) column);
                break;
            case TIMESTAMP:
                v.setType(Type.TIMESTAMP);
                v.setTimestampValue((Long) column);
                break;
            case ENUM:
            case STRING:
                v.setType(Type.STRING);
                v.setStringValue((String) column);
                break;
            default:
                throw new IllegalArgumentException("Tuple column type " + cdef.getType().val + " is currently not supported");
            }
            
            ColumnData.Builder colData = ColumnData.newBuilder();
            colData.setName(cdef.getName());
            colData.setValue(v);
            result.add(colData.build());
            i++;
        }
        return result;
    }
}
