package org.yamcs.replication;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

/**
 * Defines all the message types that are exchanged between master and slave.
 * 
 * <p>
 * Each message has a 4 bytes header: <br>
 * 1 byte type <br>
 * 3 bytes message size
 * <p>
 * Same structure is also kept in the replication file to be able to play it directly over the network.
 * <p>
 * The replication file however contains only STREAM_INFO and DATA messages (and we call them transactions)
 */
public class MessageType {
    public final static byte WAKEUP = 1;
    public final static byte REQUEST = 2;
    public final static byte RESPONSE = 3;
    public final static byte STREAM_INFO = 4;
    public final static byte DATA = 5;

    /**
     * Convert protobuf to messages to be sent via netty. It uses Unpooled netty buffers useful for small seldom sent
     * messages.
     */
    static ByteBuf protoToNetty(byte msgType, MessageLite msg) {
        byte[] b = msg.toByteArray();
        ByteBuf buf = Unpooled.buffer(4 + b.length);
        buf.writeInt((msgType << 24) | b.length);
        buf.writeBytes(b);
        return buf;
    }

    public static <T extends MessageLite.Builder> T nettyToProto(ByteBuf buf, T builder) throws InvalidProtocolBufferException {
        final byte[] data;
        final int offset;
        int length = buf.readableBytes();
      
        if (buf.hasArray()) {
            data = buf.array();
            offset = buf.arrayOffset() + buf.readerIndex();
        } else {
            data = ByteBufUtil.getBytes(buf, buf.readerIndex(), length, false);
            offset = 0;
        }
        builder.mergeFrom(data, offset, length);
        return builder;
    }
}
