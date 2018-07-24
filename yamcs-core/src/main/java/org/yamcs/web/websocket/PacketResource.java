package org.yamcs.web.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.tctm.TmDataLinkInitialiser;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.protobuf.ByteString;

/**
 * Provides realtime event subscription via web.
 */
public class PacketResource extends AbstractWebSocketResource {

    private static final Logger log = LoggerFactory.getLogger(PacketResource.class);
    public static final String RESOURCE_NAME = "packets";

    public static final String OP_subscribe = "subscribe";
    public static final String OP_unsubscribe = "unsubscribe";
    private String streamName;
    private Stream stream;
    private StreamSubscriber streamSubscriber;

    private String yamcsInstance;

    public PacketResource(ConnectedWebSocketClient client) {
        super(client);
        Processor processor = client.getProcessor();
        if (processor != null) {
            yamcsInstance = processor.getInstance();
        }
    }

    @Override
    public WebSocketReply processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {
        String op = ctx.getOperation();
        if (OP_unsubscribe.equals(op)) {
            return unsubscribe(ctx.getRequestId());
        }

        if (op.startsWith(OP_subscribe)) {
            if (streamSubscriber != null) {
                throw new WebSocketException(ctx.getRequestId(), "Already subscribed to a stream");
            }

            String[] a = op.split("\\s+");
            if (a.length != 2) {
                throw new WebSocketException(ctx.getRequestId(), "Invalid request. Use 'subscribe <stream_name>'");
            }
            this.streamName = a[1];
            if (yamcsInstance != null) {
                YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
                stream = ydb.getStream(streamName);
                if (stream == null) {
                    throw new WebSocketException(ctx.getRequestId(),
                            "Invalid request. No stream named '" + streamName + "'");
                }
            } else {
                throw new WebSocketException(ctx.getRequestId(), "Invalid request. Instance unspecified");
            }
            return subscribe(ctx.getRequestId());
        }

        throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '" + ctx.getOperation() + "'");
    }

    private WebSocketReply subscribe(int requestId) throws WebSocketException {
        doUnsubscribe(); // Only one subscription at a time
        doSubscribe();
        return WebSocketReply.ack(requestId);
    }

    @Override
    public void unselectProcessor() {
        doUnsubscribe();
        yamcsInstance = null;
    }

    @Override
    public void selectProcessor(Processor processor) throws ProcessorException {
        yamcsInstance = processor.getInstance();
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        stream = ydb.getStream(streamName);
        doSubscribe();
    }

    private WebSocketReply unsubscribe(int requestId) throws WebSocketException {
        doUnsubscribe();
        return WebSocketReply.ack(requestId);
    }

    @Override
    public void socketClosed() {
        doUnsubscribe();
    }

    private void doSubscribe() {
        if (stream != null) {
            streamSubscriber = new StreamSubscriber() {
                @Override
                public void onTuple(Stream stream, Tuple tuple) {
                    try {
                        byte[] pktData = (byte[]) tuple.getColumn(TmDataLinkInitialiser.PACKET_COLUMN);
                        long genTime = (Long) tuple.getColumn(TmDataLinkInitialiser.GENTIME_COLUMN);
                        long receptionTime = (Long) tuple.getColumn(TmDataLinkInitialiser.RECTIME_COLUMN);
                        int seqNumber = (Integer) tuple.getColumn(TmDataLinkInitialiser.SEQNUM_COLUMN);
                        TmPacketData tm = TmPacketData.newBuilder().setPacket(ByteString.copyFrom(pktData))
                                .setGenerationTime(genTime)
                                .setReceptionTime(receptionTime)
                                .setSequenceNumber(seqNumber)
                                .build();
                        wsHandler.sendData(ProtoDataType.TM_PACKET, tm);
                    } catch (Exception e) {
                        log.warn("got error when sending event, quitting", e);
                        socketClosed();
                    }
                }

                @Override
                public void streamClosed(Stream stream) {
                }
            };
            stream.addSubscriber(streamSubscriber);
        }
    }

    private void doUnsubscribe() {
        if (streamSubscriber != null) {
            stream.removeSubscriber(streamSubscriber);
        }
        streamSubscriber = null;
    }
}
