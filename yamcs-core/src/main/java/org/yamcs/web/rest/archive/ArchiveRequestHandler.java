package org.yamcs.web.rest.archive;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.protobuf.Archive.DumpArchiveRequest;
import org.yamcs.protobuf.Archive.DumpArchiveResponse;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.SchemaArchive;
import org.yamcs.protobuf.SchemaCommanding;
import org.yamcs.protobuf.SchemaPvalue;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.protobuf.Yamcs.ReplayStatus;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.ui.ParameterRetrievalGui;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.rest.InternalServerErrorException;
import org.yamcs.web.rest.NotFoundException;
import org.yamcs.web.rest.RestException;
import org.yamcs.web.rest.RestReplayListener;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequestHandler;
import org.yamcs.web.rest.RestResponse;
import org.yamcs.web.rest.RestUtils;
import org.yamcs.web.rest.mdb.MDBRequestHandler;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.YarchDatabase;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.protobuf.MessageLite;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.protostuff.JsonIOUtil;
import io.protostuff.Schema;

/**
 * Serves archived data through a web api. The default built-in tables are given
 * specialised APIs. For mission-specific tables the generic table API could be
 * used.
 */
public class ArchiveRequestHandler extends RestRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(ArchiveRequestHandler.class.getName());
    
    private Map<String, RestRequestHandler> subHandlers = new LinkedHashMap<>();
    private CsvGenerator csvGenerator = null;
    
    public ArchiveRequestHandler() {
        subHandlers.put("alarms", new ArchiveAlarmRequestHandler());
        subHandlers.put("commands", new ArchiveCommandRequestHandler());
        subHandlers.put("downloads", new ArchiveDownloadHandler());
        subHandlers.put("events", new ArchiveEventRequestHandler());
        subHandlers.put("indexes", new ArchiveIndexHandler());
        subHandlers.put("packets", new ArchivePacketRequestHandler());
        subHandlers.put("parameters", new ArchiveParameterRequestHandler());
        subHandlers.put("streams", new ArchiveStreamRequestHandler());
        subHandlers.put("tables", new ArchiveTableRequestHandler());
        subHandlers.put("tags", new ArchiveTagRequestHandler());
    }
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            throw new NotFoundException(req);
        }
        
        String instance = req.getPathSegment(pathOffset);
        if (!YamcsServer.hasInstance(instance)) {
            throw new NotFoundException(req, "No instance '" + instance + "'");
        }
        req.addToContext(RestRequest.CTX_INSTANCE, instance);
        req.addToContext(MDBRequestHandler.CTX_MDB, XtceDbFactory.getInstance(instance));
        
        pathOffset++;
        if (!req.hasPathSegment(pathOffset)) {
            return handleDumpRequest(req);
        } else {
            String segment = req.getPathSegment(pathOffset);
            RestRequestHandler handler = subHandlers.get(segment);
            if (handler != null) {
                return handler.handleRequest(req, pathOffset + 1);
            } else {
                throw new NotFoundException(req);
            }
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
                String instance = req.getFromContext(RestRequest.CTX_INSTANCE);
                String filename = YarchDatabase.getHome() + "/" + instance + "/profiles/" + profile + ".profile";
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
     * 
     * @deprecated the profiles stuff still needs migration
     */
    @Deprecated
    private RestResponse handleDumpRequest(RestRequest req) throws RestException {
        // req.assertGET();
        DumpArchiveRequest request = req.bodyAsMessage(SchemaArchive.DumpArchiveRequest.MERGE).build();

        // Check if a profile has been specified in the request
        // Currently, profiles apply only for Parameter requests
        Yamcs.NamedObjectList requestedProfile = getParametersProfile(req);
        
        // This method also supports CSV as a response
        String targetContentType;
        if (req.asksFor(CSV_MIME_TYPE)) {
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

        RestUtils.startChunkedTransfer(req, targetContentType);
        ChannelHandlerContext ctx = req.getChannelHandlerContext();
        
        RestReplays.replay(req, replayRequest, new RestReplayListener() {
            
            @Override
            public void onNewData(ProtoDataType type, MessageLite data) {
                try {
                    DumpArchiveResponse.Builder builder = DumpArchiveResponse.newBuilder();
                    MessageAndSchema restMessage = fromReplayMessage(type, data);
                    mergeMessage(type, restMessage.message, builder);
    
                    // Write a chunk containing a delimited message
                    ByteBuf buf = ctx.alloc().buffer();
                    try (ByteBufOutputStream channelOut = new ByteBufOutputStream(buf)) {
                        if (PROTOBUF_MIME_TYPE.equals(targetContentType)) {
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
                    }
    
                    RestUtils.writeChunk(req, buf);
                } catch (IOException e) {
                    log.info("skipping chunk");
                }
            }
            
            @Override
            public void stateChanged(ReplayStatus rs) {
                RestUtils.stopChunkedTransfer(req);
                log.trace("All chunks were written out");
            }
        });
        return null;
    }

    private static void mergeMessage(ProtoDataType dataType, MessageLite message, DumpArchiveResponse.Builder builder) {
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
            
    private static MessageAndSchema<?> fromReplayMessage(ProtoDataType dataType, MessageLite msg) {
        switch (dataType) {
            case PARAMETER:
            case PP:
                ParameterData.Builder parameterData = ParameterData.newBuilder((ParameterData) msg);
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
                return new MessageAndSchema<>((TmPacketData) msg, SchemaYamcs.TmPacketData.WRITE);
            case CMD_HISTORY:
                return new MessageAndSchema<>((CommandHistoryEntry) msg, SchemaCommanding.CommandHistoryEntry.WRITE);
            case EVENT:
                return new MessageAndSchema<>((Event) msg, SchemaYamcs.Event.WRITE);
            default:
                throw new IllegalStateException("Unsupported message of type " + dataType);
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
