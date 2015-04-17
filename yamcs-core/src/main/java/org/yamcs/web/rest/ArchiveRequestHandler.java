package org.yamcs.web.rest;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpHeaders.Values;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.protostuff.JsonIOUtil;
import io.protostuff.Schema;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsException;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsSession;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Rest.RestDumpArchiveRequest;
import org.yamcs.protobuf.Rest.RestDumpArchiveResponse;
import org.yamcs.protobuf.SchemaCommanding;
import org.yamcs.protobuf.SchemaPvalue;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeedType;
import org.yamcs.protobuf.Yamcs.StringMessage;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.utils.TimeEncoding;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.protobuf.MessageLite;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/** 
 * Serves archived data through a web api. The Archived data is fetched from the
 * ReplayServer using HornetQ.
 *
 * <p>Archive requests use chunked encoding with an unspecified content length, which enables
 * us to send large dumps without needing to determine a content length on the server. At the
 * moment every hornetq message from the archive replay is put in a separate chunk and flushed.
 */
public class ArchiveRequestHandler extends AbstractRestRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(ArchiveRequestHandler.class.getName());

    // This is a guideline, not a hard limit because because calculations don't include wrapping message
    private static final int MAX_BYTE_SIZE = 1048576;

    // Same as ChannelFutureListener.CLOSE_ON_FAILURE, but outputs an additional log message
    private static final ChannelFutureListener CLOSE_ON_FAILURE = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) {
            if (!future.isSuccess()) {
                log.warn("Exception while writing to client", future.cause());
                future.channel().close();
            }
        }
    };

    @Override
    public String[] getSupportedOutboundMediaTypes() {
        return new String[] { JSON_MIME_TYPE, BINARY_MIME_TYPE };
    }

    /**
     * Sends a replay request to the ReplayServer, and from that returns either a normal
     * HttpResponse (when the 'stream'-option is false), or a Chunked-Encoding HttpResponse followed
     * by multiple HttpChunks where messages are delimited by their byte size (when the 'stream'-option
     * is true).
     */
    @Override
    public void handleRequest(ChannelHandlerContext ctx, FullHttpRequest req, String yamcsInstance, String remainingUri) throws RestException {
        RestDumpArchiveRequest request = readMessage(req, SchemaRest.RestDumpArchiveRequest.MERGE).build();
        if (remainingUri == null) remainingUri = "";

        String contentType = getTargetContentType(req);

        ReplayRequest.Builder rrb = ReplayRequest.newBuilder();
        rrb.setSpeed(ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP));
        rrb.setEndAction(EndAction.QUIT);
        if (request.hasParameterRequest())
            rrb.setParameterRequest(request.getParameterRequest());
        if (request.hasPacketRequest())
            rrb.setPacketRequest(request.getPacketRequest());
        if (request.hasEventRequest())
            rrb.setEventRequest(request.getEventRequest());
        if (request.hasCommandHistoryRequest())
            rrb.setCommandHistoryRequest(request.getCommandHistoryRequest());
        if (request.hasPpRequest())
            rrb.setPpRequest(request.getPpRequest());
        ReplayRequest replayRequest = rrb.build();


        QueryStringDecoder qsDecoder = new QueryStringDecoder(remainingUri);
        if (request.hasStream() && request.getStream()) {
            try {
                streamResponse(ctx, req, qsDecoder, yamcsInstance, replayRequest, contentType);
            } catch (Exception e) {
                // Not throwing RestException up, since we are probably a few chunks in already.
                log.error("Could not write entire chunked response", e);
            }
        } else {
            writeAggregatedResponse(ctx, req, qsDecoder, yamcsInstance, replayRequest, contentType);
        }
    }

    private void streamResponse(ChannelHandlerContext ctx, FullHttpRequest req, QueryStringDecoder qsDecoder, String yamcsInstance, ReplayRequest replayRequest, String contentType)
    throws IOException, URISyntaxException, HornetQException, YamcsException, YamcsApiException {
        YamcsSession ys = null;
        YamcsClient msgClient = null;
        try {
            ys = YamcsSession.newBuilder().setConnectionParams("yamcs://localhost:5445/"+yamcsInstance).build();
            msgClient = ys.newClientBuilder().setRpc(true).setDataConsumer(null, null).build();
            SimpleString replayServer = Protocol.getYarchReplayControlAddress(yamcsInstance);
            StringMessage answer = (StringMessage) msgClient.executeRpc(replayServer, "createReplay", replayRequest, StringMessage.newBuilder());

            // Server is good to go, start the replay
            SimpleString replayAddress=new SimpleString(answer.getMessage());
            msgClient.executeRpc(replayAddress, "start", null, null);

            // Return base HTTP response, indicating that we'll used chunked encoding
            HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
            response.headers().set(Names.TRANSFER_ENCODING, Values.CHUNKED);
            response.headers().set(Names.CONTENT_TYPE, contentType);

            ChannelFuture writeFuture=ctx.write(response);
            writeFuture.addListener(CLOSE_ON_FAILURE);

            while(true) {
                ClientMessage msg = msgClient.dataConsumer.receive();

                if (Protocol.endOfStream(msg)) {
                    // Send empty chunk downstream to signal end of response
                    ChannelFuture chunkWriteFuture = ctx.write(new DefaultHttpContent(Unpooled.EMPTY_BUFFER));
                    chunkWriteFuture.addListener(ChannelFutureListener.CLOSE);
                    log.trace("All chunks were written out");
                    break;
                }

                RestDumpArchiveResponse.Builder builder = RestDumpArchiveResponse.newBuilder();
                ProtoDataType dataType = ProtoDataType.valueOf(msg.getIntProperty(Protocol.DATA_TYPE_HEADER_NAME));
                if (dataType == null) {
                    log.trace("Ignoring hornetq message of type null");
                    continue;
                }

                MessageAndSchema restMessage = fromReplayMessage(dataType, msg);
                mergeMessage(dataType, restMessage.message, builder);

                // Write a chunk containing a delimited message
                ByteBuf buf = Unpooled.buffer();
                ByteBufOutputStream channelOut = new ByteBufOutputStream(buf);

                if (BINARY_MIME_TYPE.equals(contentType)) {
                    builder.build().writeDelimitedTo(channelOut);
                } else {
                    JsonGenerator generator = createJsonGenerator(channelOut, qsDecoder);
                    JsonIOUtil.writeTo(generator, restMessage.message, restMessage.schema, false);
                    generator.close();
                }

                Channel ch = ctx.channel();
                writeFuture = ctx.write(new DefaultHttpContent(buf));
                try {
                    while (!ch.isWritable() && ch.isOpen()) {
                        writeFuture.await(5, TimeUnit.SECONDS);
                    }
                } catch (InterruptedException e) {
                    log.warn("Interrupted while waiting for channel to become writable", e);
                    // TODO return? throw up?
                }
            }
        } finally {
            if (msgClient != null) {
                try { msgClient.close(); } catch (HornetQException e) {
                    System.err.println("Caught .... on msgClient close"); e.printStackTrace(); }
            }
            if (ys != null) {
                try { ys.close(); } catch (HornetQException e) { e.printStackTrace(); }
            }
        }
    }

    /**
     * Current implementation seems sub-optimal, because every message is encoded twice.
     * Once for getting a decent byte size estimate, and a second time for writing the aggregate result.
     * However, since we hard-limit to about 1MB and since we expect most clients that fetch from the
     * archive to stream the response instead, I don't consider this a problem at this stage.
     * This method is mostly here for small interactive requests through tools like curl.
     */
    private void writeAggregatedResponse(ChannelHandlerContext ctx, FullHttpRequest req, QueryStringDecoder qsDecoder, String yamcsInstance, ReplayRequest replayRequest, String contentType) throws RestException {
        YamcsSession ys = null;
        YamcsClient msgClient = null;
        int sizeEstimate = 0;
        try {
            ys = YamcsSession.newBuilder().setConnectionParams("yamcs://localhost:5445/"+yamcsInstance).build();
            msgClient = ys.newClientBuilder().setRpc(true).setDataConsumer(null, null).build();
            SimpleString replayServer = Protocol.getYarchReplayControlAddress(yamcsInstance);
            StringMessage answer = (StringMessage) msgClient.executeRpc(replayServer, "createReplay", replayRequest, StringMessage.newBuilder());

            // Server is good to go, start the replay
            SimpleString replayAddress=new SimpleString(answer.getMessage());
            msgClient.executeRpc(replayAddress, "start", null, null);

            HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
            response.headers().set(Names.CONTENT_TYPE, contentType);
            RestDumpArchiveResponse.Builder builder = RestDumpArchiveResponse.newBuilder();
            while(true) {
                ClientMessage msg = msgClient.dataConsumer.receive();

                if (Protocol.endOfStream(msg)) {
                    log.trace("All done. Send to client");
                    writeMessage(ctx, req, qsDecoder, builder.build(), SchemaRest.RestDumpArchiveResponse.WRITE);
                    break;
                }

                ProtoDataType dataType = ProtoDataType.valueOf(msg.getIntProperty(Protocol.DATA_TYPE_HEADER_NAME));
                if (dataType == null) {
                    log.trace("Ignoring hornetq message of type null");
                    continue;
                }

                MessageAndSchema restMessage = fromReplayMessage(dataType, msg);
                try {
                    if (BINARY_MIME_TYPE.equals(contentType)) {
                        sizeEstimate += restMessage.message.getSerializedSize();
                    } else {
                        ByteArrayOutputStream tempOut= new ByteArrayOutputStream();
                        JsonGenerator generator = createJsonGenerator(tempOut, qsDecoder);
                        JsonIOUtil.writeTo(generator, restMessage.message, restMessage.schema, false);
                        generator.close();
                        sizeEstimate += tempOut.toByteArray().length;
                    }
                } catch (IOException e) {
                    throw new InternalServerErrorException(e);
                }

                // Verify whether we still abide by the max byte size (aproximation)
                if (sizeEstimate > MAX_BYTE_SIZE)
                    throw new ForbiddenException("Response too large. Add more precise filters or use 'stream'-option.");

                // If we got this far, append this replay message to our aggregated rest response
                mergeMessage(dataType, restMessage.message, builder);
            }
        } catch (YamcsException | URISyntaxException | YamcsApiException | HornetQException e) {
            throw new InternalServerErrorException(e);
        } finally {
            if (msgClient != null) {
                try { msgClient.close(); } catch (HornetQException e) { e.printStackTrace(); }
            }
            if (ys != null) {
                try { ys.close(); } catch (HornetQException e) { e.printStackTrace(); }
            }
        }
    }

    private static void mergeMessage(ProtoDataType dataType, MessageLite message, RestDumpArchiveResponse.Builder builder) throws YamcsApiException {
        switch (dataType) {
            case PARAMETER:
                builder.addParameterData((ParameterData) message);
                break;
            case TM_PACKET:
                builder.addPacketData((TmPacketData) message);
                break;
            case CMD_HISTORY:
                builder.addCommand((CommandHistoryEntry) message);
                break;
            case PP:
                builder.addPpData((ParameterData) message);
                break;
            case EVENT:
                builder.addEvent((Event) message);
                break;
            default:
                log.warn("Unexpected data type " + dataType);
        }
    }

    private static MessageAndSchema<?> fromReplayMessage(ProtoDataType dataType, ClientMessage msg) throws YamcsApiException {
        switch (dataType) {
            case PARAMETER:
            case PP:
                ParameterData.Builder parameterData = (ParameterData.Builder) Protocol.decodeBuilder(msg, ParameterData.newBuilder());
                List<ParameterValue> pvals = parameterData.getParameterList();
                parameterData.clearParameter();
                for (ParameterValue pval : pvals) {
                    ParameterValue.Builder pvalBuilder = pval.toBuilder();
                    pvalBuilder.setAcquisitionTimeUTC(TimeEncoding.toString(pval.getAcquisitionTime()));
                    pvalBuilder.setGenerationTimeUTC(TimeEncoding.toString(pval.getGenerationTime()));
                    parameterData.addParameter(pvalBuilder);
                }
                return new MessageAndSchema<>(parameterData.build(), SchemaPvalue.ParameterData.WRITE);
            case TM_PACKET:
                return new MessageAndSchema<>((TmPacketData) Protocol.decode(msg, TmPacketData.newBuilder()), SchemaYamcs.TmPacketData.WRITE);
            case CMD_HISTORY:
                return new MessageAndSchema<>((CommandHistoryEntry) Protocol.decode(msg, CommandHistoryEntry.newBuilder()), SchemaCommanding.CommandHistoryEntry.WRITE);
            case EVENT:
                return new MessageAndSchema<>((Event) Protocol.decode(msg, Event.newBuilder()), SchemaYamcs.Event.WRITE);
            default:
                throw new IllegalStateException("Unsupported hornetq message of type " + dataType);
        }
    }

    /**
     * Simple struct to allow generic-safe access to a protobuf message along with its protostuff schema
     */
    private static class MessageAndSchema<T extends MessageLite> {
        T message;
        Schema<T> schema;
        public MessageAndSchema(T message, Schema<T> schema) {
            this.message = message;
            this.schema = schema;
        }
    }
}
