package org.yamcs.web.websocket;

import java.io.IOException;

import org.yamcs.protobuf.Alarms.AlarmData;
import org.yamcs.protobuf.Archive.StreamData;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandQueueEvent;
import org.yamcs.protobuf.Commanding.CommandQueueInfo;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Web.WebSocketServerMessage;
import org.yamcs.protobuf.Web.WebSocketServerMessage.MessageType;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketSubscriptionData;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.TimeInfo;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.LinkEvent;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.Statistics;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.protostuff.Schema;

public class ProtobufEncoder implements WebSocketEncoder {
    
    private ChannelHandlerContext ctx;
    
    public ProtobufEncoder(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public WebSocketFrame encodeException(WebSocketException e) throws IOException {
        WebSocketServerMessage serverMessage = WebSocketServerMessage.newBuilder()
                .setType(MessageType.EXCEPTION)
                .setException(e.toWebSocketExceptionData())
                .build();
        return toFrame(serverMessage);
    }

    @Override
    public WebSocketFrame encodeReply(WebSocketReplyData reply) throws IOException {
        WebSocketServerMessage serverMessage = WebSocketServerMessage.newBuilder()
                .setType(MessageType.REPLY)
                .setReply(reply)
                .build();
        return toFrame(serverMessage);
    }

    @Override
    public <T> WebSocketFrame encodeData(int sequenceNumber, ProtoDataType dataType, T message, Schema<T> schema) throws IOException {
        WebSocketSubscriptionData.Builder responseb = WebSocketSubscriptionData.newBuilder();
        responseb.setSequenceNumber(sequenceNumber);
        responseb.setType(dataType);
        if (dataType == ProtoDataType.CMD_HISTORY) {
            responseb.setCommand((CommandHistoryEntry) message);
        } else if (dataType == ProtoDataType.PARAMETER) {
            responseb.setParameterData((ParameterData) message);
        } else if (dataType == ProtoDataType.PROCESSOR_INFO) {
            responseb.setProcessorInfo((ProcessorInfo) message);
        } else if (dataType == ProtoDataType.CLIENT_INFO) {
            responseb.setClientInfo((ClientInfo) message);
        } else if (dataType == ProtoDataType.PROCESSING_STATISTICS) {
            responseb.setStatistics((Statistics) message);
        } else if (dataType == ProtoDataType.EVENT) {
            responseb.setEvent((Event) message);
        } else if (dataType == ProtoDataType.ALARM_DATA) {
            responseb.setAlarmData((AlarmData) message);
        } else if (dataType == ProtoDataType.STREAM_DATA) {
            responseb.setStreamData((StreamData) message);
        } else if (dataType == ProtoDataType.LINK_EVENT) {
            responseb.setLinkEvent((LinkEvent) message);
        } else if (dataType == ProtoDataType.TIME_INFO) {
            responseb.setTimeInfo((TimeInfo) message);
        } else if (dataType == ProtoDataType.EVENT) {
            responseb.setEvent((Event) message);
        } else if (dataType == ProtoDataType.COMMAND_QUEUE_INFO) {
            responseb.setCommandQueueInfo((CommandQueueInfo) message);
        } else if (dataType == ProtoDataType.COMMAND_QUEUE_EVENT) {
            responseb.setCommandQueueEvent((CommandQueueEvent) message);
        } else if (dataType == ProtoDataType.TM_PACKET) {
            responseb.setTmPacket((TmPacketData) message);
        } else {
            throw new IllegalArgumentException("Unsupported data type " + dataType);
        }

        WebSocketServerMessage serverMessage = WebSocketServerMessage.newBuilder()
                .setType(MessageType.DATA)
                .setData(responseb)
                .build();
        return toFrame(serverMessage);
    }

    private BinaryWebSocketFrame toFrame(WebSocketServerMessage message) throws IOException {
        // TODO This assumes that the frame is quite small (which it should be, but)
        ByteBuf buf = ctx.alloc().buffer();
        try (ByteBufOutputStream cout = new ByteBufOutputStream(buf)) {
            message.writeTo(cout);
        }
        return new BinaryWebSocketFrame(buf);
    }
}
