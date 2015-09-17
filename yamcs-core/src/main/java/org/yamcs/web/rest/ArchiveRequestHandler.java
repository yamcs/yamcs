package org.yamcs.web.rest;

import static org.yamcs.web.AbstractRequestHandler.BINARY_MIME_TYPE;
import static org.yamcs.web.AbstractRequestHandler.CSV_MIME_TYPE;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.TimeInterval;
import org.yamcs.YamcsException;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsSession;
import org.yamcs.archive.TagDb;
import org.yamcs.archive.TagReceiver;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Rest.GetTagsRequest;
import org.yamcs.protobuf.Rest.GetTagsResponse;
import org.yamcs.protobuf.Rest.InsertTagRequest;
import org.yamcs.protobuf.Rest.InsertTagResponse;
import org.yamcs.protobuf.Rest.RestDumpArchiveRequest;
import org.yamcs.protobuf.Rest.RestDumpArchiveResponse;
import org.yamcs.protobuf.Rest.UpdateTagRequest;
import org.yamcs.protobuf.SchemaCommanding;
import org.yamcs.protobuf.SchemaPvalue;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeedType;
import org.yamcs.protobuf.Yamcs.StringMessage;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.security.UsernamePasswordToken;
import org.yamcs.ui.ParameterRetrievalGui;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.protobuf.MessageLite;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.protostuff.JsonIOUtil;
import io.protostuff.Schema;

/** 
 * Serves archived data through a web api.
 *
 * <p>Archive requests use chunked encoding with an unspecified content length, which enables
 * us to send large dumps without needing to determine a content length on the server. At the
 * moment every hornetq message from the archive replay is put in a separate chunk and flushed.
 * <p>
 * /(instance)/api/archive
 */
public class ArchiveRequestHandler implements RestRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(ArchiveRequestHandler.class.getName());

    // This is a guideline, not a hard limit because because calculations don't include wrapping message
    private static final int MAX_BYTE_SIZE = 1048576;
    
    private CsvGenerator csvGenerator = null;
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            return handleDumpRequest(req); // GET /(instance)/api/archive
        }
        
        switch (req.getPathSegment(pathOffset)) {
        case "tags":
            return handleTagsRequest(req, pathOffset + 1);
            
        default:
            throw new NotFoundException(req);
        }
    }
    
    /**
     *  csv generator should be created only once per request since its insert a header in first row
     */
    private void initCsvGenerator(Yamcs.ReplayRequest replayRequest) {
        csvGenerator = null;
        if(replayRequest.hasParameterRequest()) {
            csvGenerator = new CsvGenerator();
            csvGenerator.initParameterFormatter(replayRequest.getParameterRequest().getNameFilterList());
        }
    }

    // Check if a profile has been specified in the request
    // Currently, profiles apply only for Parameter requests
    // Returns null if no profile is specified
    private Yamcs.NamedObjectList getParametersProfile(RestRequest req) throws InternalServerErrorException {
        Yamcs.NamedObjectList requestedProfile = null;
        if(req.getQueryParameters().containsKey("profile"))
        {
            try {
                String profile = req.getQueryParameters().get("profile").get(0);
                String filename = YarchDatabase.getHome() + "/" + req.yamcsInstance + "/profiles/" + profile + ".profile";
                requestedProfile = Yamcs.NamedObjectList.newBuilder().addAllList(ParameterRetrievalGui.loadParameters(new BufferedReader(new FileReader(filename)))).build();
            }
            catch (Exception e)
            {
                log.error("Unable to open requested profile.", e);
                throw new InternalServerErrorException("Unable to open requested profile.", e);
            }
        }
        return requestedProfile;
    }

    /**
     * Sends a replay request to the ReplayServer, and from that returns either a normal
     * HttpResponse (when the 'stream'-option is false), or a Chunked-Encoding HttpResponse followed
     * by multiple HttpChunks where messages are delimited by their byte size (when the 'stream'-option
     * is true).
     */
    private RestResponse handleDumpRequest(RestRequest req) throws RestException {
       // req.assertGET();
        RestDumpArchiveRequest request = req.bodyAsMessage(SchemaRest.RestDumpArchiveRequest.MERGE).build();

        // Check if a profile has been specified in the request
        // Currently, profiles apply only for Parameter requests
        Yamcs.NamedObjectList requestedProfile = getParametersProfile(req);
        
        // This method also supports CSV as a response
        String targetContentType;
        if (req.getHttpRequest().headers().contains(Names.ACCEPT)
                && req.getHttpRequest().headers().get(Names.ACCEPT).equals(CSV_MIME_TYPE)) {
            targetContentType = CSV_MIME_TYPE;
        } else {
            targetContentType = req.deriveTargetContentType();
        }

        ReplayRequest.Builder rrb = ReplayRequest.newBuilder();
        
        rrb.setSpeed(ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP));
        rrb.setEndAction(EndAction.QUIT);
        
        if(request.hasStart()) {
            rrb.setStart(request.getStart());
        } else if(request.hasUtcStart()) {
            rrb.setUtcStart(request.getUtcStart());
        }
        
        if(request.hasStop()) {
            rrb.setStop(request.getStop());
        } else if(request.hasUtcStop()) {
            rrb.setUtcStop(request.getUtcStop());
        }
        
        
        if (request.hasParameterRequest()) {
            if(requestedProfile != null) {
                Yamcs.ParameterReplayRequest prr = rrb.getParameterRequest().newBuilderForType().addAllNameFilter(requestedProfile.getListList()).build();
                rrb.setParameterRequest(prr);
            } else {
                rrb.setParameterRequest(request.getParameterRequest());
            }
        }
        if (request.hasPacketRequest())
            rrb.setPacketRequest(request.getPacketRequest());
        if (request.hasEventRequest())
            rrb.setEventRequest(request.getEventRequest());
        if (request.hasCommandHistoryRequest())
            rrb.setCommandHistoryRequest(request.getCommandHistoryRequest());
        if (request.hasPpRequest())
            rrb.setPpRequest(request.getPpRequest());
        ReplayRequest replayRequest = rrb.build();

        if(CSV_MIME_TYPE.equals(targetContentType))
        {
            initCsvGenerator(replayRequest);
        }

        // Start a short-lived replay
        YamcsSession ys = null;
        YamcsClient msgClient = null;
        try {
            String yamcsConnectionData = "yamcs://";
            if(req.authToken!=null && req.authToken.getClass() == UsernamePasswordToken.class)
            {
                yamcsConnectionData += ((UsernamePasswordToken)req.authToken).getUsername()
                        + ":" + ((UsernamePasswordToken)req.authToken).getPasswordS() +"@" ;
            }
            yamcsConnectionData += "localhost/"+req.yamcsInstance;

            ys = YamcsSession.newBuilder().setConnectionParams(yamcsConnectionData).build();
            msgClient = ys.newClientBuilder().setRpc(true).setDataConsumer(null, null).build();
            SimpleString replayServer = Protocol.getYarchReplayControlAddress(req.yamcsInstance);
            StringMessage answer = (StringMessage) msgClient.executeRpc(replayServer, "createReplay", replayRequest, StringMessage.newBuilder());

            // Server is good to go, start the replay
            SimpleString replayAddress=new SimpleString(answer.getMessage());
            msgClient.executeRpc(replayAddress, "start", null, null);

            if (request.hasStream() && request.getStream()) {
                try {
                    streamResponse(msgClient, req, targetContentType);
                    return null; // !
                } catch (Exception e) {
                    // Not throwing RestException up, since we are likely a few chunks in already.
                    log.error("Skipping chunk", e);
                    return null; // !
                }
            } else {
                return writeAggregatedResponse(msgClient, req, targetContentType);
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

    private void streamResponse(YamcsClient msgClient, RestRequest req, String targetContentType)
    throws HornetQException, YamcsApiException, IOException {

        RestUtils.startChunkedTransfer(req, targetContentType);
        ChannelHandlerContext ctx = req.getChannelHandlerContext();

        while(true) {
            ClientMessage msg = msgClient.dataConsumer.receive();

            if (Protocol.endOfStream(msg)) {
                RestUtils.stopChunkedTransfer(req);
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
            ByteBuf buf = ctx.alloc().buffer();
            ByteBufOutputStream channelOut = new ByteBufOutputStream(buf);

            if (BINARY_MIME_TYPE.equals(targetContentType)) {
                builder.build().writeDelimitedTo(channelOut);
            } else if (CSV_MIME_TYPE.equals(targetContentType)) {
                if(csvGenerator != null) {
                    csvGenerator.insertRows(builder.build(), channelOut);
                }
            } else {
                JsonGenerator generator = req.createJsonGenerator(channelOut);
                JsonIOUtil.writeTo(generator, restMessage.message, restMessage.schema, false);
                generator.close();
            }

            Channel ch = ctx.channel();
            ChannelFuture writeFuture = ctx.writeAndFlush(new DefaultHttpContent(buf));
            try {
                while (!ch.isWritable() && ch.isOpen()) {
                    writeFuture.await(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for channel to become writable", e);
                // TODO return? throw up?
            }
        }
    }

    /**
     * Current implementation seems sub-optimal, because every message is
     * encoded twice. Once for getting a decent byte size estimate, and a second
     * time for writing the aggregate result. However, since we hard-limit to
     * about 1MB and since we expect most clients that fetch from the archive to
     * stream the response instead, I don't consider this a problem at this
     * stage. This method is mostly here for small interactive requests through
     * tools like curl.
     */
    private RestResponse writeAggregatedResponse(YamcsClient msgClient, RestRequest req, String targetContentType)
    throws RestException, HornetQException, YamcsApiException {
        int sizeEstimate = 0;
        RestDumpArchiveResponse.Builder builder = RestDumpArchiveResponse.newBuilder();
        while(true) {
            ClientMessage msg = msgClient.dataConsumer.receive();

            if (Protocol.endOfStream(msg)) {
                log.trace("All done. Send to client");
                return new RestResponse(req, builder.build(), SchemaRest.RestDumpArchiveResponse.WRITE);
            }

            ProtoDataType dataType = ProtoDataType.valueOf(msg.getIntProperty(Protocol.DATA_TYPE_HEADER_NAME));
            if (dataType == null) {
                log.trace("Ignoring hornetq message of type null");
                continue;
            }

            MessageAndSchema restMessage = fromReplayMessage(dataType, msg);
            try {
                if (BINARY_MIME_TYPE.equals(targetContentType)) {
                    sizeEstimate += restMessage.message.getSerializedSize();
                } else {
                    ByteArrayOutputStream tempOut= new ByteArrayOutputStream();
                    JsonGenerator generator = req.createJsonGenerator(tempOut);
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
    
    /**
     * GET /(instance)/api/archive/tags
     * POST /(instance)/api/archive/tags
     * PUT /(instance)/api/archive/tags/(old_start)/(id)
     * DELETE /(instance)/api/archive/tags/(old_start)/(id)
     */
    private RestResponse handleTagsRequest(RestRequest req, int pathOffset) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            if (req.isGET()) {
                return getTags(req);
            } else if (req.isPOST()) {
                return insertTag(req);
            } else {
                throw new MethodNotAllowedException(req);
            }            
        } else {
            if (!req.hasPathSegment(pathOffset + 1)) {
                throw new NotFoundException(req);
            }
            
            long tagTime;
            try {
                tagTime = Long.parseLong(req.getPathSegment(pathOffset));
            } catch (NumberFormatException e) {
                throw new BadRequestException("Invalid tag time: " + req.getPathSegment(pathOffset));
            }
            
            int tagId;
            try {
                tagId = Integer.parseInt(req.getPathSegment(pathOffset + 1));
            } catch (NumberFormatException e) {
                throw new BadRequestException("Invalid tag id: " + req.getPathSegment(pathOffset + 1));
            }
            
            if (req.isPUT()) {
                return updateTag(req, tagTime, tagId);
            } else if (req.isDELETE()) {
                return deleteTag(req, tagTime, tagId);
            } else {
                throw new MethodNotAllowedException(req);
            }
        }
    }
    
    /**
     * Lists all tags (optionally filtered by request-body)
     * <p>
     * GET /(instance)/api/archive/tags
     */
    private RestResponse getTags(RestRequest req) throws RestException {
        TagDb tagDb = getTagDb(req.yamcsInstance);
        
        // Start with default open-ended
        TimeInterval interval = new TimeInterval();
        
        // Check any additional options
        if (req.hasBody()) {
            GetTagsRequest request = req.bodyAsMessage(SchemaRest.GetTagsRequest.MERGE).build();
            if (request.hasStart()) interval.setStart(request.getStart());
            if (request.hasStop()) interval.setStop(request.getStop());
        }
        
        // Build response with a callback from the TagDb, this is all happening on
        // the same thread.
        GetTagsResponse.Builder responseb = GetTagsResponse.newBuilder();
        try {
            tagDb.getTags(new TimeInterval(), new TagReceiver() {
                @Override
                public void onTag(ArchiveTag tag) {
                   responseb.addTags(tag);
                }

                @Override public void finished() {}
            });
        } catch (IOException e) {
            throw new InternalServerErrorException("Could not load tags", e);
        }
        return new RestResponse(req, responseb.build(), SchemaRest.GetTagsResponse.WRITE);
    }
    
    /**
     * Adds a new tag. The newly added tag is returned as a response so the user
     * knows the assigned id.
     * <p>
     * POST /(instance)/api/archive/tags
     */
    private RestResponse insertTag(RestRequest req) throws RestException {
        TagDb tagDb = getTagDb(req.yamcsInstance);
        InsertTagRequest request = req.bodyAsMessage(SchemaRest.InsertTagRequest.MERGE).build();
        if (!request.hasName())
            throw new BadRequestException("Name is required");
        
        // Translate to yamcs-api
        ArchiveTag.Builder tagb = ArchiveTag.newBuilder().setName(request.getName());
        if (request.hasStart()) tagb.setStart(request.getStart());
        if (request.hasStop()) tagb.setStop(request.getStop());
        if (request.hasDescription()) tagb.setDescription(request.getDescription());
        if (request.hasColor()) tagb.setColor(request.getColor());
        
        // Do the insert
        ArchiveTag newTag;
        try {
            newTag = tagDb.insertTag(tagb.build());
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }

        // Echo back the tag, with its new ID
        InsertTagResponse.Builder responseb = InsertTagResponse.newBuilder();
        responseb.setTag(newTag);
        return new RestResponse(req, responseb.build(), SchemaRest.InsertTagResponse.WRITE);
    }
    
    /**
     * Updates an existing tag. Returns nothing
     * <p>
     * PUT /(instance)/api/archive/tags/(tag-time)/(tag-id)
     */
    private RestResponse updateTag(RestRequest req, long tagTime, int tagId) throws RestException {
        TagDb tagDb = getTagDb(req.yamcsInstance);
        UpdateTagRequest request = req.bodyAsMessage(SchemaRest.UpdateTagRequest.MERGE).build();
        if (tagId < 1)
            throw new BadRequestException("Invalid tag ID");
        if (!request.hasName())
            throw new BadRequestException("Name is required");
        
        // Translate to yamcs-api
        ArchiveTag.Builder tagb = ArchiveTag.newBuilder().setName(request.getName());
        if (request.hasStart()) tagb.setStart(request.getStart());
        if (request.hasStop()) tagb.setStop(request.getStop());
        if (request.hasDescription()) tagb.setDescription(request.getDescription());
        if (request.hasColor()) tagb.setColor(request.getColor());
        
        // Do the update
        try {
            tagDb.updateTag(tagTime, tagId, tagb.build());
        } catch (YamcsException e) {
            throw new InternalServerErrorException(e);
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }
        
        return new RestResponse(req);
    }
    
    /**
     * Deletes the identified tag. Returns nothing, but if the tag
     * didn't exist, it returns a 404 Not Found
     */
    private RestResponse deleteTag(RestRequest req, long tagTime, int tagId) throws RestException {
        if (tagId < 1)
            throw new BadRequestException("Invalid tag ID");
        
        TagDb tagDb = getTagDb(req.yamcsInstance);
        try {
            tagDb.deleteTag(tagTime, tagId);
        } catch (YamcsException e) { // Delete-tag returns an exception when it's not found
            throw new NotFoundException(req);
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }
        
        return new RestResponse(req);
    }
    
    private static TagDb getTagDb(String yamcsInstance) throws RestException {
        try {
            return YarchDatabase.getInstance(yamcsInstance).getDefaultStorageEngine().getTagDb();
        } catch (YarchException e) {
            throw new InternalServerErrorException("Could not load tag-db", e);
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
