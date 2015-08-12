package org.yamcs.web.websocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Websocket.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.protobuf.Yamcs.ColumnValue;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.StreamData;
import org.yamcs.protobuf.Yamcs.StreamSubscribeRequest;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;

import com.google.protobuf.ByteString;

/**
 * Capable of producing and consuming yarch Stream data (Tuples) over web socket
 */
public class StreamResource extends AbstractWebSocketResource {
    public static final String OP_subscribe = "subscribe";
    public static final String OP_publish = "publish";
    private Logger log;
    
    private List<Subscription> subscriptions = new ArrayList<>();
    
    public StreamResource(YProcessor yproc, WebSocketServerHandler wsHandler) {
        super(yproc, wsHandler);
        wsHandler.addResource("stream", this);
        log = LoggerFactory.getLogger(StreamResource.class.getName() + "[" + yproc.getInstance() + "]");
    }

    @Override
    public WebSocketReplyData processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder, AuthenticationToken authenticationToken) throws WebSocketException {
        switch (ctx.getOperation()) {
        case OP_subscribe:
            return processSubscribeRequest(ctx, decoder);
        case OP_publish:
            return processPublishRequest(ctx, decoder);
        default:
            throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '" + ctx.getOperation() + "'");
        }
    }
    
    private WebSocketReplyData processSubscribeRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        YarchDatabase ydb = YarchDatabase.getInstance(processor.getInstance());
        
        // Optionally read body. If it's not provided, suppose the subscription concerns
        // the stream of the current processor (TODO currently doesn't work with JSON).
        Stream stream;
        if (ctx.getData() != null) { // Check doesn't work with JSON, always returns JsonParser
            StreamSubscribeRequest req = decoder.decodeMessageData(ctx, SchemaYamcs.StreamSubscribeRequest.MERGE).build();
            if (req.hasStream()) {
                stream = ydb.getStream(req.getStream());
            } else {
                throw new WebSocketException(ctx.getRequestId(), "No stream was provided");
            }
        } else {
            stream = ydb.getStream(processor.getName());
        }
        
        StreamSubscriber subscriber = new StreamSubscriber() {

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                StreamData.Builder builder = StreamData.newBuilder();
                builder.setStream(stream.getName());
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
                    
                    ColumnValue.Builder columnValue = ColumnValue.newBuilder();
                    columnValue.setColumnName(cdef.getName());
                    columnValue.setValue(v);
                    builder.addColumnValue(columnValue);
                    i++;
                }
                try {
                    wsHandler.sendData(ProtoDataType.STREAM_DATA, builder.build(), SchemaYamcs.StreamData.WRITE);
                } catch (IOException e) {
                    log.debug("Could not send tuple data", e);
                }
            }

            @Override
            public void streamClosed(Stream stream) {
            }
        };
        
        stream.addSubscriber(subscriber);
        subscriptions.add(new Subscription(stream, subscriber));
        return toAckReply(ctx.getRequestId());
    }
    
    private WebSocketReplyData processPublishRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        YarchDatabase ydb = YarchDatabase.getInstance(processor.getInstance());
        
        StreamData req = decoder.decodeMessageData(ctx, SchemaYamcs.StreamData.MERGE).build();
        Stream stream = ydb.getStream(req.getStream());
        
        int expectedColumnCount = stream.getDefinition().getColumnDefinitions().size();
        if (req.getColumnValueCount() != expectedColumnCount) {
            throw new WebSocketException(ctx.getRequestId(),
                    String.format("Expected %d columns, but found %d", req.getColumnValueCount(), expectedColumnCount));
        }
        
        List<Object> tupleColumns = new ArrayList<>();
        for (ColumnDefinition cdef : stream.getDefinition().getColumnDefinitions()) {
            ColumnValue providedValue = findColumnValue(req, cdef.getName());
            if (providedValue == null) {
                throw new WebSocketException(ctx.getRequestId(), "Could not find a column named " + cdef.getName());
            }
            
            if (!providedValue.hasValue()) {
                throw new WebSocketException(ctx.getRequestId(), "No value was provided for column " + cdef.getName());
            }
            
            // Sanity check. We should perhaps find a better way to do all of this
            switch (cdef.getType().val) {
            case SHORT:
                if (providedValue.getValue().getType() != Type.SINT32)
                    throw new WebSocketException(ctx.getRequestId(), String.format(
                            "Value type for column %s should be '%s'", cdef.getName(), Type.SINT32));
                tupleColumns.add(providedValue.getValue().getSint32Value());
                break;
            case DOUBLE:
                if (providedValue.getValue().getType() != Type.DOUBLE)
                    throw new WebSocketException(ctx.getRequestId(), String.format(
                            "Value type for column %s should be '%s'", cdef.getName(), Type.DOUBLE));
                tupleColumns.add(providedValue.getValue().getDoubleValue());
                break;
            case BINARY:
                if (providedValue.getValue().getType() != Type.BINARY)
                    throw new WebSocketException(ctx.getRequestId(), String.format(
                            "Value type for column %s should be '%s'", cdef.getName(), Type.BINARY));
                tupleColumns.add(providedValue.getValue().getBinaryValue().toByteArray());
                break;
            case INT:
                if (providedValue.getValue().getType() != Type.SINT32)
                    throw new WebSocketException(ctx.getRequestId(), String.format(
                            "Value type for column %s should be '%s'", cdef.getName(), Type.SINT32));
                tupleColumns.add(providedValue.getValue().getSint32Value());
                break;
            case TIMESTAMP:
                if (providedValue.getValue().getType() != Type.TIMESTAMP)
                    throw new WebSocketException(ctx.getRequestId(), String.format(
                            "Value type for column %s should be '%s'", cdef.getName(), Type.TIMESTAMP));
                tupleColumns.add(providedValue.getValue().getTimestampValue());
                break;
            case ENUM:
            case STRING:
                if (providedValue.getValue().getType() != Type.STRING)
                    throw new WebSocketException(ctx.getRequestId(), String.format(
                            "Value type for column %s should be '%s'", cdef.getName(), Type.STRING));
                tupleColumns.add(providedValue.getValue().getStringValue());
                break;
            default:
                throw new IllegalArgumentException("Tuple column type " + cdef.getType().val + " is currently not supported");
            }
        }
        
        stream.emitTuple(new Tuple(stream.getDefinition(), tupleColumns));
        return toAckReply(ctx.getRequestId());
    }
    
    private static ColumnValue findColumnValue(StreamData tupleData, String name) {
        for (ColumnValue val : tupleData.getColumnValueList()) {
            if (val.getColumnName().equals(name)) {
                return val;
            }
        }
        return null;
    }
    
    public void switchYProcessor(YProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void quit() {
        for (Subscription subscription : subscriptions) {
            subscription.stream.removeSubscriber(subscription.subscriber);
        }
    }
    
    private static class Subscription {
        Stream stream;
        StreamSubscriber subscriber;
        
        Subscription(Stream stream, StreamSubscriber subscriber) {
            this.stream = stream;
            this.subscriber = subscriber;
        }
    }
}
