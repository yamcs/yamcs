package org.yamcs.yarch;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.yarch.ColumnSerializerFactory.AbstractColumnSerializer;

import com.google.common.io.ByteStreams;

public class ParameterValueColumnSerializer extends AbstractColumnSerializer<ParameterValue>{

    public ParameterValueColumnSerializer() {
        super(16);
    }

    @Override
    public ParameterValue deserialize(DataInputStream stream, ColumnDefinition cd) throws IOException {
        int size = stream.readInt();
        if(size>ColumnSerializerFactory.maxBinaryLength) {
            throw new IOException("serialized size too big "+size+">"+ColumnSerializerFactory.maxBinaryLength);
        }
        
        org.yamcs.protobuf.Pvalue.ParameterValue.Builder gpvb = org.yamcs.protobuf.Pvalue.ParameterValue.newBuilder();
        
        final InputStream limitedInput = ByteStreams.limit(stream, size);
        
        gpvb.mergeFrom(limitedInput);
        return ParameterValue.fromGpb(cd.getName(), gpvb.build());
    }

    @Override
    public void serialize(DataOutputStream stream, ParameterValue pv) throws IOException {
        org.yamcs.protobuf.Pvalue.ParameterValue gpv = pv.toProtobufParameterValue(Optional.empty(), false);
        int size = gpv.getSerializedSize();
        stream.writeInt(size);
        gpv.writeTo(stream);
    }
}
