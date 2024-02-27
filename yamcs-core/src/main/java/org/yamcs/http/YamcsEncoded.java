package org.yamcs.http;

import java.io.IOException;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;

public interface YamcsEncoded {
    int getSerializedSize();

    void writeTo(CodedOutputStream output) throws IOException;

    public static int computeMessageSize(int fieldNumber, YamcsEncoded value) {
        return CodedOutputStream.computeTagSize(fieldNumber) + computeMessageSizeNoTag(value);
    }

    public static int computeMessageSizeNoTag(YamcsEncoded value) {
        return computeLengthDelimitedFieldSize(value.getSerializedSize());
    }

    public static int computeLengthDelimitedFieldSize(int fieldLength) {
        return CodedOutputStream.computeUInt32SizeNoTag(fieldLength) + fieldLength;
    }

    static void writeMessage(CodedOutputStream output, int fieldNumber, YamcsEncoded value) throws IOException {
        output.writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        output.writeUInt32NoTag(value.getSerializedSize());
        value.writeTo(output);
    }
}
