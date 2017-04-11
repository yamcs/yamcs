package org.yamcs.web.rest.archive;

import java.io.IOException;

import org.yamcs.api.MediaType;
import org.yamcs.protobuf.Table.Cell;
import org.yamcs.protobuf.Table.ColumnInfo;
import org.yamcs.protobuf.Table.Row;
import org.yamcs.web.HttpException;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.StreamToChunkedTransferEncoder;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.ColumnSerializer;
import org.yamcs.yarch.ColumnSerializerFactory;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

import com.google.protobuf.ByteString;

import org.yamcs.yarch.DataType._type;

import io.netty.buffer.ByteBufOutputStream;

/**
 * Encodes table dumps
 * @author nm
 *
 */
public class TableDumpEncoder extends StreamToChunkedTransferEncoder {
    TupleDefinition completeTuple = new TupleDefinition();
    
    public TableDumpEncoder(RestRequest req) throws HttpException {
        super(req, MediaType.PROTOBUF);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void processTuple(Tuple tuple, ByteBufOutputStream bufOut) throws IOException {
        Row.Builder rowb = Row.newBuilder();
        for(int i=0; i<tuple.size(); i++) {
            ColumnDefinition cd = tuple.getColumnDefinition(i);
            Object v = tuple.getColumn(i);
            int colId = completeTuple.getColumnIndex(cd.getName());
            if(colId==-1) {
                completeTuple.addColumn(cd);
                colId = completeTuple.getColumnIndex(cd.getName());
                rowb.addColumn(ColumnInfo.newBuilder().setId(colId).setName(cd.getName()).setType(cd.getType().toString()).build());
            }
            DataType type = cd.getType();
            ColumnSerializer cs;
            if(type.val==_type.ENUM) {
                cs = ColumnSerializerFactory.getBasicColumnSerializer(DataType.STRING);
            } else if(type.val==_type.PROTOBUF) {
                cs = ColumnSerializerFactory.getProtobufSerializer(cd);
            } else {
                cs = ColumnSerializerFactory.getBasicColumnSerializer(cd.getType());
            }
            rowb.addCell(Cell.newBuilder().setColumnId(colId).setData(ByteString.copyFrom(cs.toByteArray(v))).build());
        }
        rowb.build().writeDelimitedTo(bufOut);
    }
}
