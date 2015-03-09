package org.yamcs.web.rest;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.codehaus.jackson.JsonGenerator;
import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpHeaders.Values;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsException;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsSession;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Rest.RestReplayResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.StringMessage;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.utils.TimeEncoding;

import com.dyuproject.protostuff.JsonIOUtil;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

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

    @Override
    public String[] getSupportedOutboundMediaTypes() {
        return new String[] { JSON_MIME_TYPE, BINARY_MIME_TYPE };
    }

    @Override
    public void handleRequest(ChannelHandlerContext ctx, HttpRequest req, MessageEvent evt, String yamcsInstance, String remainingUri) throws RestException {
        if(remainingUri == null || remainingUri.isEmpty()) {
            sendError(ctx, NOT_FOUND);
            return;
        }
        ReplayRequest incomingRequest = readMessage(req, SchemaYamcs.ReplayRequest.MERGE).build();
        QueryStringDecoder qsDecoder = new QueryStringDecoder(remainingUri);

        /*
         * Modify some of the original request settings
         */
        ReplayRequest.Builder rrb = ReplayRequest.newBuilder(incomingRequest);
        // When using the REST api, start and stop are interpreted as unix time. Convert to internal yamcs time.
        rrb.setStart(TimeEncoding.fromUnixTime(incomingRequest.getStart()));
        rrb.setStop(TimeEncoding.fromUnixTime(incomingRequest.getStop()));
        // Don't support other options through web api
        rrb.setEndAction(EndAction.QUIT);
        ReplayRequest replayRequest = rrb.build();

        // Send the replay request to the ReplayServer.
        YamcsSession ys = null;
        YamcsClient msgClient = null;
        try {
            ys = YamcsSession.newBuilder().setConnectionParams("yamcs://localhost:5445/"+yamcsInstance).build();
            msgClient = ys.newClientBuilder().setRpc(true).setDataConsumer(null, null).build();
            SimpleString replayServer=Protocol.getYarchReplayControlAddress(yamcsInstance);
            StringMessage answer=(StringMessage) msgClient.executeRpc(replayServer, "createReplay", rrb.build(), StringMessage.newBuilder());
            
            // Server is good to go, start the replay
            SimpleString replayAddress=new SimpleString(answer.getMessage());
            msgClient.executeRpc(replayAddress, "start", null, null);
      
            // Return base HTTP response, indicating that we'll used chunked encoding
            HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
            response.setChunked(true);
            response.setHeader(Names.TRANSFER_ENCODING, Values.CHUNKED);
            String contentType = getTargetContentType(req);
            response.setHeader(Names.CONTENT_TYPE, contentType);
            
            Channel ch=evt.getChannel();
            ChannelFuture writeFuture=ch.write(response);
            writeFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if(!future.isSuccess()) {
                        future.getChannel().close();
                        throw new RuntimeException("Exception while writing data to client", future.getCause());
                    }
                }
            });

            // For every separate upstream hornetq message send a chunk back downstream
            while(true) {
                ClientMessage msg = msgClient.dataConsumer.receive();

                if (Protocol.endOfStream(msg)) {
                    // Send empty chunk downstream to signal end of response
                    ChannelFuture chunkWriteFuture=ch.write(new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER));
                    chunkWriteFuture.addListener(ChannelFutureListener.CLOSE);
                    log.trace("All chunks were written out");
                    break;
                }

                RestReplayResponse.Builder responseb = RestReplayResponse.newBuilder();
                int dataType = msg.getIntProperty(Protocol.DATA_TYPE_HEADER_NAME);
                switch (ProtoDataType.valueOf(dataType)) {
                case PARAMETER:
                    // Convert yamcs time to exposed unix time TODO should make this optional
                    ParameterData.Builder parameterData = (ParameterData.Builder) Protocol.decodeBuilder(msg, ParameterData.newBuilder());
                    parameterData.setGenerationTime(TimeEncoding.toUnixTime(parameterData.getGenerationTime()));

                    List<ParameterValue> pvals = parameterData.getParameterList();
                    parameterData.clearParameter();
                    for (ParameterValue pval : pvals) {
                        ParameterValue.Builder pvalBuilder = pval.toBuilder();
                        pvalBuilder.setGenerationTime(TimeEncoding.toUnixTime(pval.getGenerationTime()));
                        pvalBuilder.setAcquisitionTime(TimeEncoding.toUnixTime(pval.getAcquisitionTime()));
                        pvalBuilder.setAcquisitionTimeUTC(TimeEncoding.toString(pval.getAcquisitionTime()));
                        pvalBuilder.setGenerationTimeUTC(TimeEncoding.toString(pval.getGenerationTime()));
                        parameterData.addParameter(pvalBuilder);
                    }
                    responseb.setParameterData(parameterData);
                    break;
                case TM_PACKET:
                    TmPacketData.Builder packetData = (TmPacketData.Builder) Protocol.decodeBuilder(msg, TmPacketData.newBuilder());
                    packetData.setGenerationTime(TimeEncoding.toUnixTime(packetData.getGenerationTime()));
                    packetData.setReceptionTime(TimeEncoding.toUnixTime(packetData.getReceptionTime()));
                    responseb.setPacketData(packetData);
                    break;
                case CMD_HISTORY:
                    CommandHistoryEntry.Builder command = (CommandHistoryEntry.Builder) Protocol.decodeBuilder(msg, CommandHistoryEntry.newBuilder());
                    CommandId.Builder commandId = command.getCmdId().toBuilder().setGenerationTime(TimeEncoding.toUnixTime(command.getCmdId().getGenerationTime()));
                    command.setCmdId(commandId);
                    responseb.setCommand(command);
                    break;
                case PP:
                    ParameterData.Builder ppData = (ParameterData.Builder) Protocol.decodeBuilder(msg, ParameterData.newBuilder());
                    ppData.setGenerationTime(TimeEncoding.toUnixTime(ppData.getGenerationTime()));

                    List<ParameterValue> ppvals = ppData.getParameterList();
                    ppData.clearParameter();
                    for (ParameterValue pval : ppvals) {
                        ParameterValue.Builder pvalBuilder = pval.toBuilder();
                        pvalBuilder.setGenerationTime(TimeEncoding.toUnixTime(pval.getGenerationTime()));
                        pvalBuilder.setAcquisitionTime(TimeEncoding.toUnixTime(pval.getAcquisitionTime()));
                        pvalBuilder.setAcquisitionTimeUTC(TimeEncoding.toString(pval.getAcquisitionTime()));
                        pvalBuilder.setGenerationTimeUTC(TimeEncoding.toString(pval.getGenerationTime()));
                        ppData.addParameter(pvalBuilder);
                    }
                    responseb.setPpData(ppData);
                    break;
                case EVENT:
                    Event.Builder event = (Event.Builder) Protocol.decodeBuilder(msg, Event.newBuilder());
                    event.setGenerationTime(TimeEncoding.toUnixTime(event.getGenerationTime()));
                    event.setReceptionTime(TimeEncoding.toUnixTime(event.getReceptionTime()));
                    responseb.setEvent(event);
                    break;
                default:
                    log.trace("Ignoring unsupported hornetq message of type {}", ProtoDataType.valueOf(dataType));
                    continue;
                }

                // Finally, write the chunk
                ChannelBuffer buf = ChannelBuffers.dynamicBuffer();
                ChannelBufferOutputStream channelOut = new ChannelBufferOutputStream(buf);
                try {
                    if (BINARY_MIME_TYPE.equals(contentType)) {
                        responseb.build().writeTo(channelOut);
                    } else {
                        JsonGenerator generator = createJsonGenerator(channelOut, qsDecoder);
                        JsonIOUtil.writeTo(generator, responseb.build(), SchemaRest.RestReplayResponse.WRITE, false);
                        generator.close();
                    }
                } catch (IOException e) {
                    log.error("Internal server error while writing out message", e);
                    throw new RestException(e);
                }

                writeFuture=ch.write(new DefaultHttpChunk(buf));
                try {
                    while (!ch.isWritable() && ch.isOpen()) {
                        writeFuture.await(5, TimeUnit.SECONDS);
                    }
                } catch (InterruptedException e) {
                    log.warn("Interrupted while waiting for channel to become writable", e);
                }
            }
        } catch (URISyntaxException e) {
            log.error("Could not parse URI to local hornetq URI", e);
            throw new RestException(e);
        } catch (HornetQException e) {
            log.error("" + e, e);
            throw new RestException(e);
        } catch (YamcsApiException e) {
            log.error("" + e, e);
            throw new RestException(e);
        } catch (YamcsException e) {
            log.error("" + e, e);
            throw new RestException(e);
        } finally {
            if (msgClient != null) {
                try { msgClient.close(); } catch (HornetQException e) { e.printStackTrace(); }
            }
            if (ys != null) {
                try { ys.close(); } catch (HornetQException e) { e.printStackTrace(); }
            }
        }
    }
}
