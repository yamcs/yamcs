package org.yamcs.web.websocket;

import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.management.ManagementService;
import org.yamcs.management.TableStreamListener;
import org.yamcs.protobuf.Archive.StreamInfo;
import org.yamcs.protobuf.Web.StreamsSubscriptionRequest;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.YamcsManagement.StreamEvent;
import org.yamcs.protobuf.YamcsManagement.StreamEvent.Type;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

/**
 * Emits info updates on stream stats
 */
public class StreamsResource implements WebSocketResource, TableStreamListener {

    private ConnectedWebSocketClient client;

    // Instance requested by the user. This should not update when the processor changes.
    private String instance;

    public StreamsResource(ConnectedWebSocketClient client) {
        this.client = client;
    }

    @Override
    public String getName() {
        return "streams";
    }

    @Override
    public WebSocketReply subscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        if (ctx.getData() == null) {
            throw new WebSocketException(ctx.getRequestId(), "Instance must be specified");
        } else {
            StreamsSubscriptionRequest req = decoder.decodeMessageData(ctx, StreamsSubscriptionRequest.newBuilder())
                    .build();
            if (req.hasInstance()) {
                instance = req.getInstance();
            } else {
                throw new WebSocketException(ctx.getRequestId(), "Instance must be specified");
            }
        }

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        ManagementService mservice = ManagementService.getInstance();

        client.sendReply(WebSocketReply.ack(ctx.getRequestId()));

        for (Stream stream : ydb.getStreams()) {
            client.sendData(ProtoDataType.STREAM_EVENT, StreamEvent.newBuilder()
                    .setType(Type.CREATED)
                    .setName(stream.getName())
                    .setDataCount(stream.getDataCount())
                    .build());
        }
        mservice.addTableStreamListener(this);
        return null;
    }

    @Override
    public void streamRegistered(String instance, Stream stream) {
        if (instance.equals(this.instance)) {
            client.sendData(ProtoDataType.STREAM_EVENT, StreamEvent.newBuilder()
                    .setType(Type.CREATED)
                    .setName(stream.getName())
                    .setDataCount(stream.getDataCount())
                    .build());
        }
    }

    @Override
    public void streamUpdated(String instance, StreamInfo stream) {
        if (instance.equals(this.instance)) {
            client.sendData(ProtoDataType.STREAM_EVENT, StreamEvent.newBuilder()
                    .setType(Type.UPDATED)
                    .setName(stream.getName())
                    .setDataCount(stream.getDataCount())
                    .build());
        }
    }

    @Override
    public void streamUnregistered(String instance, String name) {
        if (instance.equals(this.instance)) {
            client.sendData(ProtoDataType.STREAM_EVENT, StreamEvent.newBuilder()
                    .setType(Type.DELETED)
                    .setName(name)
                    .build());
        }
    }

    @Override
    public WebSocketReply unsubscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        ManagementService mservice = ManagementService.getInstance();
        mservice.removeTableStreamListener(this);
        return WebSocketReply.ack(ctx.getRequestId());
    }

    @Override
    public void selectProcessor(Processor processor) throws ProcessorException {
        // Ignore
    }

    @Override
    public void unselectProcessor() {
        // Ignore
    }

    @Override
    public void socketClosed() {
        ManagementService mservice = ManagementService.getInstance();
        mservice.removeTableStreamListener(this);
    }
}
