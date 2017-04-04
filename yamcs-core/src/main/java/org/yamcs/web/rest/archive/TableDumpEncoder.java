package org.yamcs.web.rest.archive;

import java.io.IOException;

import org.yamcs.api.MediaType;
import org.yamcs.protobuf.Table.ColumnInfo;
import org.yamcs.protobuf.Table.Row;
import org.yamcs.web.HttpException;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.StreamToChunkedTransferEncoder;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

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
                rowb.addColumns(ColumnInfo.newBuilder().setId(colId).setName(cd.getName()).setType(cd.getType().toString()).build());
            }
            
            
        }
    }
    
    
}
