package org.yamcs.http.websocket;

import java.util.HashSet;
import java.util.Set;

import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.http.api.ArchiveHelper;
import org.yamcs.protobuf.StreamSubscriptionRequest;
import org.yamcs.protobuf.Table.StreamData;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

/**
 * Capable of producing and consuming yarch Stream data (Tuples) over web socket
 */
public class StreamResource implements StreamSubscriber, WebSocketResource {

    private ConnectedWebSocketClient client;

    private Set<Stream> subscribedStreams = new HashSet<>();

    private String yamcsInstance;

    public StreamResource(ConnectedWebSocketClient client) {
        this.client = client;
        Processor processor = client.getProcessor();
        if (processor != null) {
            yamcsInstance = processor.getInstance();
        }
    }

    @Override
    public String getName() {
        return "stream";
    }

    @Override
    public WebSocketReply subscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);

        // Optionally read body. If it's not provided, suppose the subscription concerns
        // the stream of the current processor (TODO currently doesn't work with JSON).
        Stream stream;
        StreamSubscriptionRequest req = decoder.decodeMessageData(ctx, StreamSubscriptionRequest.newBuilder()).build();
        if (req.hasStream()) {
            stream = ydb.getStream(req.getStream());
        } else {
            throw new WebSocketException(ctx.getRequestId(), "No stream was provided");
        }

        if (!subscribedStreams.contains(stream)) {
            stream.addSubscriber(this);
            subscribedStreams.add(stream);
        }

        return WebSocketReply.ack(ctx.getRequestId());
    }

    @Override
    public void onTuple(Stream stream, Tuple tuple) {
        StreamData data = ArchiveHelper.toStreamData(stream, tuple);
        client.sendData(ProtoDataType.STREAM_DATA, data);
    }

    @Override
    public void streamClosed(Stream stream) {
    }

    @Override
    public WebSocketReply unsubscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        unsubscribeAll();
        return WebSocketReply.ack(ctx.getRequestId());
    }

    @Override
    public void selectProcessor(Processor processor) throws ProcessorException {
        yamcsInstance = processor.getInstance();
    }

    @Override
    public void unselectProcessor() {
        yamcsInstance = null;
    }

    @Override
    public void socketClosed() {
        unsubscribeAll();
    }

    private void unsubscribeAll() {
        for (Stream subscribedStream : subscribedStreams) {
            subscribedStream.removeSubscriber(this);
        }
        subscribedStreams.clear();
    }
}
