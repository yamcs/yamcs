package org.yamcs.web.websocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.protobuf.Archive.ColumnData;
import org.yamcs.protobuf.Archive.StreamData;
import org.yamcs.protobuf.Rest.StreamSubscribeRequest;
import org.yamcs.protobuf.SchemaArchive;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.web.rest.archive.ArchiveHelper;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;

/**
 * Capable of producing and consuming yarch Stream data (Tuples) over web socket
 */
public class StreamResource extends AbstractWebSocketResource {

    private static final Logger log = LoggerFactory.getLogger(StreamResource.class);

    public static final String OP_subscribe = "subscribe";
    public static final String OP_publish = "publish";

    private List<Subscription> subscriptions = new ArrayList<>();

    public StreamResource(YProcessor yproc, WebSocketFrameHandler wsHandler) {
        super(yproc, wsHandler);
        wsHandler.addResource("stream", this);
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
            StreamSubscribeRequest req = decoder.decodeMessageData(ctx, SchemaRest.StreamSubscribeRequest.MERGE).build();
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
                StreamData data = ArchiveHelper.toStreamData(stream, tuple);
                try {
                    wsHandler.sendData(ProtoDataType.STREAM_DATA, data, SchemaArchive.StreamData.WRITE);
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

    private static DataType dataTypeFromValue(Value value) {
        switch (value.getType()) {
        case SINT32:
            return DataType.INT;
        case DOUBLE:
            return DataType.DOUBLE;
        case BINARY:
            return DataType.BINARY;
        case TIMESTAMP:
            return DataType.TIMESTAMP;
        case STRING:
            return DataType.STRING;
        default:
            throw new IllegalArgumentException("Unexpected value type " + value.getType());
        }
    }

    private Object makeTupleColumn(WebSocketDecodeContext ctx, String name, Value value, DataType columnType) throws WebSocketException {
        // Sanity check. We should perhaps find a better way to do all of this
        switch (columnType.val) {
        case SHORT:
            if (value.getType() != Type.SINT32)
                throw new WebSocketException(ctx.getRequestId(), String.format(
                        "Value type for column %s should be '%s'", name, Type.SINT32));
            return value.getSint32Value();
        case DOUBLE:
            if (value.getType() != Type.DOUBLE)
                throw new WebSocketException(ctx.getRequestId(), String.format(
                        "Value type for column %s should be '%s'", name, Type.DOUBLE));
            return value.getDoubleValue();
        case BINARY:
            if (value.getType() != Type.BINARY)
                throw new WebSocketException(ctx.getRequestId(), String.format(
                        "Value type for column %s should be '%s'", name, Type.BINARY));
            return value.getBinaryValue().toByteArray();
        case INT:
            if (value.getType() != Type.SINT32)
                throw new WebSocketException(ctx.getRequestId(), String.format(
                        "Value type for column %s should be '%s'", name, Type.SINT32));
            return value.getSint32Value();
        case TIMESTAMP:
            if (value.getType() != Type.TIMESTAMP)
                throw new WebSocketException(ctx.getRequestId(), String.format(
                        "Value type for column %s should be '%s'", name, Type.TIMESTAMP));
            return value.getTimestampValue();
        case ENUM:
        case STRING:
            if (value.getType() != Type.STRING)
                throw new WebSocketException(ctx.getRequestId(), String.format(
                        "Value type for column %s should be '%s'", name, Type.STRING));
            return value.getStringValue();
        default:
            throw new IllegalArgumentException("Tuple column type " + columnType.val + " is currently not supported");
        }
    }

    private WebSocketReplyData processPublishRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        YarchDatabase ydb = YarchDatabase.getInstance(processor.getInstance());

        StreamData req = decoder.decodeMessageData(ctx, SchemaArchive.StreamData.MERGE).build();
        Stream stream = ydb.getStream(req.getStream());
        if (stream == null) {
            throw new WebSocketException(ctx.getRequestId(), "Cannot find stream '" + req.getStream() + "'");
        }

        TupleDefinition tdef = stream.getDefinition();
        List<Object> tupleColumns = new ArrayList<>();

        // 'fixed' colums
        for (ColumnDefinition cdef : stream.getDefinition().getColumnDefinitions()) {
            ColumnData providedField = findColumnValue(req, cdef.getName());
            if (providedField == null) continue;
            if (!providedField.hasValue()) {
                throw new WebSocketException(ctx.getRequestId(), "No value was provided for column " + cdef.getName());
            }
            Object column = makeTupleColumn(ctx, cdef.getName(), providedField.getValue(), cdef.getType());
            tupleColumns.add(column);
        }

        // 'dynamic' columns
        for (ColumnData val : req.getColumnList()) {
            if (stream.getDefinition().getColumn(val.getName()) == null) {
                DataType type = dataTypeFromValue(val.getValue());
                tdef.addColumn(val.getName(), type);
                Object column = makeTupleColumn(ctx, val.getName(), val.getValue(), type);
                tupleColumns.add(column);
            }
        }

        Tuple t = new Tuple(tdef, tupleColumns);
        log.info("Emitting tuple {} to {}", t, stream.getName());
        stream.emitTuple(t);
        return toAckReply(ctx.getRequestId());
    }

    private static ColumnData findColumnValue(StreamData tupleData, String name) {
        for (ColumnData val : tupleData.getColumnList()) {
            if (val.getName().equals(name)) {
                return val;
            }
        }
        return null;
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
