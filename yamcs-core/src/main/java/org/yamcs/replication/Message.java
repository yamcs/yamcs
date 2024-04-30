package org.yamcs.replication;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

import org.yamcs.replication.protobuf.Request;
import org.yamcs.replication.protobuf.Response;
import org.yamcs.replication.protobuf.StreamInfo;
import org.yamcs.replication.protobuf.TimeMessage;
import org.yamcs.replication.protobuf.Wakeup;
import org.yamcs.utils.DecodingException;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.MessageLite;

/**
 * Defines all the message types that are exchanged between master and slave.
 * 
 * <p>
 * Message format:
 * 
 * <pre>
 * 1 byte type
 * 3 bytes message size (size of data to follow) = n+4
 * n bytes data
 * 4 bytes CRC.
 * </pre>
 * <p>
 * This is the same structure used in the replication file to be able to play it directly over the network.
 * <p>
 * The replication file contains only STREAM_INFO and DATA messages (and we call them transactions)
 */
public class Message {
    public final static byte WAKEUP = 1;
    public final static byte REQUEST = 2;
    public final static byte RESPONSE = 3;
    public final static byte STREAM_INFO = 4;
    public final static byte DATA = 5;
    public final static byte TIME = 6;

    final byte type;
    MessageLite protoMsg;

    public static Message decode(ByteBuffer buf) throws DecodingException {
        verifyCrc(buf);

        int lengthtype = buf.getInt();
        int length = lengthtype & 0xFFFFFF;
        byte type = (byte) (lengthtype >> 24);

        if (length != buf.remaining()) {// netty will split the messages based on this length so if this
            // message has been received via netty, this error cannot really happen
            throw new DecodingException(
                    "Message length does not match. header length: " + length + " buffer length:" + buf.remaining());
        }

        Message msg;

        buf.limit(buf.limit() - 4); // get rid of CRC

        switch (type) {
        case DATA:
            msg = new TransactionMessage(type, buf.getInt(), buf.getLong());
            ((TransactionMessage) msg).buf = buf;
            break;
        case WAKEUP:
            msg = new Message(type);
            msg.protoMsg = decodeProto(buf, Wakeup.newBuilder()).build();
            break;
        case REQUEST:
            msg = new Message(type);
            msg.protoMsg = decodeProto(buf, Request.newBuilder()).build();
            break;
        case RESPONSE:
            msg = new Message(type);
            msg.protoMsg = decodeProto(buf, Response.newBuilder()).build();
            break;
        case STREAM_INFO:
            msg = new TransactionMessage(type, buf.getInt(), buf.getLong());
            buf.getInt();// pointer to next metadata
            msg.protoMsg = decodeProto(buf, StreamInfo.newBuilder()).build();
            break;
        case TIME:
            msg = new Message(type);
            msg.protoMsg = decodeProto(buf, TimeMessage.newBuilder()).build();
            break;
        default:
            throw new DecodingException("unknown message type " + type);

        }

        return msg;
    }

    Message(byte type) {
        this.type = type;
    }

    public byte type() {
        return type;
    }

    public MessageLite protoMsg() {
        return protoMsg;
    }

    private static void verifyCrc(ByteBuffer buf) throws DecodingException {
        int pos = buf.position();
        buf.limit(buf.limit() - 4);
        CRC32 crc = new CRC32();
        crc.update(buf);
        int ccrc = (int) crc.getValue();

        buf.limit(buf.limit() + 4);
        int rcrc = buf.getInt();

        if (ccrc != rcrc) {
            throw new DecodingException("CRC verification failed");
        }
        buf.position(pos);
    }

    /**
     * decodes the proto message. buf has to be positioned before the size.
     * 
     * <p>
     * the CRC is the last 4 bytes and is not checked.
     */
    private static <T extends MessageLite.Builder> T decodeProto(ByteBuffer buf, T builder)
            throws DecodingException {
        try {
            builder.mergeFrom(CodedInputStream.newInstance(buf));
            return builder;
        } catch (IOException e) {
            throw new DecodingException(e);
        }
    }

    public static Message get(Wakeup wp) {
        Message msg = new Message(WAKEUP);
        msg.protoMsg = wp;
        return msg;
    }

    public static Message get(TimeMessage tm) {
        Message msg = new Message(TIME);
        msg.protoMsg = tm;
        return msg;
    }

    public static Message get(Response resp) {
        Message msg = new Message(RESPONSE);
        msg.protoMsg = resp;
        return msg;
    }

    public static Message get(Request req) {
        Message msg = new Message(REQUEST);
        msg.protoMsg = req;
        return msg;
    }

    public ByteBuffer encode() {
        byte[] b = protoMsg.toByteArray();
        ByteBuffer buf = ByteBuffer.allocate(b.length + 8);

        buf.putInt((type << 24) | (b.length + 4));
        buf.put(b);
        CRC32 crc = new CRC32();
        buf.position(0);
        buf.limit(b.length + 4);
        crc.update(buf);

        buf.limit(b.length + 8);
        buf.putInt((int) crc.getValue());
        buf.position(0);
        return buf;
    }

    // this is a message that comes from a replication file
    public static class TransactionMessage extends Message {
        long txId;
        int instanceId;
        ByteBuffer buf;

        TransactionMessage(byte type, int instanceId, long txId) {
            super(type);
            this.instanceId = instanceId;
            this.txId = txId;
        }

        public ByteBuffer encode() {
            throw new UnsupportedOperationException();
        }

        public long txId() {
            return txId;
        }

        public ByteBuffer buf() {
            return buf;
        }

    }

}
