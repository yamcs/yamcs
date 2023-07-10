package org.yamcs.utils;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

public class ProtoBufUtils {
    public static <T extends MessageLite> T fromByteBuf(ByteBuf buf, T.Builder builder)
            throws InvalidProtocolBufferException {
        final byte[] array;
        final int offset;
        final int length = buf.readableBytes();
        if (buf.hasArray()) {
            array = buf.array();
            offset = buf.arrayOffset() + buf.readerIndex();
        } else {
            array = ByteBufUtil.getBytes(buf, buf.readerIndex(), length, false);
            offset = 0;
        }

        return (T) builder.mergeFrom(array, offset, length).build();
    }
}
