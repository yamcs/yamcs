package org.yamcs.web.rest;

import io.protostuff.JsonIOUtil;

import com.fasterxml.jackson.core.JsonGenerator;
import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpHeaders.Values;

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
import org.yamcs.protobuf.Rest.RestReplayResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.*;
import org.yamcs.utils.TimeEncoding;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    @Override
    public String[] getSupportedOutboundMediaTypes() {
        return new String[] { JSON_MIME_TYPE, BINARY_MIME_TYPE };
    }

    @Override
    public void handleRequest(ChannelHandlerContext ctx, FullHttpRequest req, String yamcsInstance, String remainingUri) throws RestException {
        ReplayRequest incomingRequest = readMessage(req, SchemaYamcs.ReplayRequest.MERGE).build();
        if (remainingUri == null) remainingUri = "";
        QueryStringDecoder qsDecoder = new QueryStringDecoder(remainingUri);

        /*
         * Modify some of the original request settings
         * TODO this uses Yamcs Time. Should perhaps make this configurable. but that seems
         * more like a yamcs problem than a yamcs-web problem
         */
        ReplayRequest.Builder rrb = ReplayRequest.newBuilder(incomingRequest);
        rrb.setEndAction(EndAction.QUIT);
        ReplayRequest replayRequest = rrb.build();

        // Send the replay request to the ReplayServer.
        YamcsSession ys = null;
        YamcsClient msgClient = null;
        try {
            ys = YamcsSession.newBuilder().setConnectionParams("yamcs://localhost:5445/"+yamcsInstance).build();
            msgClient = ys.newClientBuilder().setRpc(true).setDataConsumer(null, null).build();
            SimpleString replayServer=Protocol.getYarchReplayControlAddress(yamcsInstance);
            StringMessage answer=(StringMessage) msgClient.executeRpc(replayServer, "createReplay", replayRequest, StringMessage.newBuilder());
            
            // Server is good to go, start the replay
            SimpleString replayAddress=new SimpleString(answer.getMessage());
            msgClient.executeRpc(replayAddress, "start", null, null);
      
            // Return base HTTP response, indicating that we'll used chunked encoding
            HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
            response.headers().set(Names.TRANSFER_ENCODING, Values.CHUNKED);
            String contentType = getTargetContentType(req);
            response.headers().set(Names.CONTENT_TYPE, contentType);
            
            ChannelFuture writeFuture=ctx.write(response);
            writeFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if(!future.isSuccess()) {
                        future.channel().close();
                        throw new RuntimeException("Exception while writing data to client", future.cause());
                    }
                }
            });

            // For every separate upstream hornetq message send a chunk back downstream
            while(true) {
                ClientMessage msg = msgClient.dataConsumer.receive();

                if (Protocol.endOfStream(msg)) {
                    // Send empty chunk downstream to signal end of response
                    ChannelFuture chunkWriteFuture=ctx.write(new DefaultHttpContent(Unpooled.EMPTY_BUFFER));
                    chunkWriteFuture.addListener(ChannelFutureListener.CLOSE);
                    log.trace("All chunks were written out");
                    break;
                }

                RestReplayResponse.Builder responseb = RestReplayResponse.newBuilder();
                ProtoDataType dataType = ProtoDataType.valueOf(msg.getIntProperty(Protocol.DATA_TYPE_HEADER_NAME));
                switch (dataType) {
                case PARAMETER:
                    ParameterData.Builder parameterData = (ParameterData.Builder) Protocol.decodeBuilder(msg, ParameterData.newBuilder());
                    List<ParameterValue> pvals = parameterData.getParameterList();
                    parameterData.clearParameter();
                    for (ParameterValue pval : pvals) {
                        ParameterValue.Builder pvalBuilder = pval.toBuilder();
                        pvalBuilder.setAcquisitionTimeUTC(TimeEncoding.toString(pval.getAcquisitionTime()));
                        pvalBuilder.setGenerationTimeUTC(TimeEncoding.toString(pval.getGenerationTime()));
                        parameterData.addParameter(pvalBuilder);
                    }
                    responseb.setParameterData(parameterData);
                    break;
                case TM_PACKET:
                    responseb.setPacketData((TmPacketData) Protocol.decode(msg, TmPacketData.newBuilder()));
                    break;
                case CMD_HISTORY:
                    responseb.setCommand((CommandHistoryEntry) Protocol.decode(msg, CommandHistoryEntry.newBuilder()));
                    break;
                case PP:
                    ParameterData.Builder ppData = (ParameterData.Builder) Protocol.decodeBuilder(msg, ParameterData.newBuilder());
                    List<ParameterValue> ppvals = ppData.getParameterList();
                    ppData.clearParameter();
                    for (ParameterValue pval : ppvals) {
                        ParameterValue.Builder pvalBuilder = pval.toBuilder();
                        pvalBuilder.setAcquisitionTimeUTC(TimeEncoding.toString(pval.getAcquisitionTime()));
                        pvalBuilder.setGenerationTimeUTC(TimeEncoding.toString(pval.getGenerationTime()));
                        ppData.addParameter(pvalBuilder);
                    }
                    responseb.setPpData(ppData);
                    break;
                case EVENT:
                    responseb.setEvent((Event) Protocol.decode(msg, Event.newBuilder()));
                    break;
                default:
                    log.trace("Ignoring unsupported hornetq message of type {}", dataType);
                    continue;
                }

                // Finally, write the chunk
                ByteBuf buf = Unpooled.buffer();
                ByteBufOutputStream channelOut = new ByteBufOutputStream(buf);
                try {
                    if (BINARY_MIME_TYPE.equals(contentType)) {
                        responseb.build().writeTo(channelOut);
                    } else {
                        JsonGenerator generator = createJsonGenerator(channelOut, qsDecoder);
                        JsonIOUtil.writeTo(generator, responseb.build(), SchemaRest.RestReplayResponse.WRITE, false);
                        generator.close();
                    }
                } catch (IOException e) {
                    throw new InternalServerErrorException(e);
                }
                Channel ch = ctx.channel();
                writeFuture=ch.write(new DefaultHttpContent(buf));
                try {
                    while (!ch.isWritable() && ch.isOpen()) {
                        writeFuture.await(5, TimeUnit.SECONDS);
                    }
                } catch (InterruptedException e) {
                    log.warn("Interrupted while waiting for channel to become writable", e);
                }
            }
        } catch (URISyntaxException | HornetQException | YamcsApiException | YamcsException e) {
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
}
