package org.yamcs.web.rest;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.TimeInterval;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.archive.EventRecorder;
import org.yamcs.archive.TagDb;
import org.yamcs.archive.TagReceiver;
import org.yamcs.archive.XtceTmRecorder;
import org.yamcs.protobuf.Archive.DumpArchiveRequest;
import org.yamcs.protobuf.Archive.DumpArchiveResponse;
import org.yamcs.protobuf.Archive.GetTagsRequest;
import org.yamcs.protobuf.Archive.GetTagsResponse;
import org.yamcs.protobuf.Archive.InsertTagRequest;
import org.yamcs.protobuf.Archive.InsertTagResponse;
import org.yamcs.protobuf.Archive.StreamInfo;
import org.yamcs.protobuf.Archive.TableData;
import org.yamcs.protobuf.Archive.TableData.TableRecord;
import org.yamcs.protobuf.Archive.TableInfo;
import org.yamcs.protobuf.Archive.UpdateTagRequest;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Pvalue.SampleSeries;
import org.yamcs.protobuf.Rest.ListEventsResponse;
import org.yamcs.protobuf.Rest.ListPacketsResponse;
import org.yamcs.protobuf.Rest.ListStreamsResponse;
import org.yamcs.protobuf.Rest.ListTablesResponse;
import org.yamcs.protobuf.SchemaArchive;
import org.yamcs.protobuf.SchemaCommanding;
import org.yamcs.protobuf.SchemaPvalue;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs;
import org.yamcs.protobuf.Yamcs.ArchiveTag;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.ParameterReplayRequest;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.protobuf.Yamcs.ReplayStatus;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.ui.ParameterRetrievalGui;
import org.yamcs.utils.ParameterFormatter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.rest.RestParameterSampler.Sample;
import org.yamcs.web.rest.RestUtils.IntervalResult;
import org.yamcs.web.rest.RestUtils.MatchResult;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.AbstractStream;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;

import com.csvreader.CsvWriter;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.protobuf.MessageLite;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.protostuff.JsonIOUtil;
import io.protostuff.Schema;

/** 
 * Serves archived data through a web api.
 *
 * <p>Archive requests use chunked encoding with an unspecified content length, which enables
 * us to send large dumps without needing to determine a content length on the server. At the
 * moment every hornetq message from the archive replay is put in a separate chunk and flushed.
 */
public class ArchiveRequestHandler extends RestRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(ArchiveRequestHandler.class.getName());
    
    private static ArchiveDownloadHandler downloadHandler = new ArchiveDownloadHandler();
    
    private CsvGenerator csvGenerator = null;
    
    @Override
    public String getPath() {
        return "archive";
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
        
        YarchDatabase ydb = YarchDatabase.getInstance(instance);
        
        pathOffset++;
        if (!req.hasPathSegment(pathOffset)) {
            return handleDumpRequest(req);
        } else {
            switch (req.getPathSegment(pathOffset)) {
            case "tags":
                return handleTagsRequest(req, pathOffset + 1);
            case "tables":
                return handleTablesRequest(req, pathOffset + 1, ydb);
            case "streams":
                return handleStreamsRequest(req, pathOffset + 1, ydb);
            case "parameters":
                return handleParametersRequest(req, pathOffset + 1);
            case "events":
            case "events.csv":
            case "events.json":
            case "events.proto":
                req.assertGET();
                return listEvents(req);
            case "packets":
                req.assertGET();
                return listPackets(req);
            case "downloads":
                return downloadHandler.handleRequest(req, pathOffset + 1);
            default:
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
     * @deprecated
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
            public void newData(ProtoDataType type, MessageLite data) {
                try {
                    DumpArchiveResponse.Builder builder = DumpArchiveResponse.newBuilder();
                    MessageAndSchema restMessage = fromReplayMessage(type, data);
                    mergeMessage(type, restMessage.message, builder);
    
                    // Write a chunk containing a delimited message
                    ByteBuf buf = ctx.alloc().buffer();
                    try (ByteBufOutputStream channelOut = new ByteBufOutputStream(buf)) {
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
    
    /**
     * GET /api/archive/:instance/tags
     * POST /api/archive/:instance/tags
     * PUT /api/archive/:instance/tags/(old_start)/(id)
     * DELETE /api/archive/:instance/tags/(old_start)/(id)
     */
    private RestResponse handleTagsRequest(RestRequest req, int pathOffset) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            if (req.isGET()) {
                return listTags(req);
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
    
    private RestResponse handleTablesRequest(RestRequest req, int pathOffset, YarchDatabase ydb) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            req.assertGET();
            return listTables(req, ydb);            
        } else {
            String tableName = req.getPathSegment(pathOffset);
            TableDefinition table = ydb.getTable(tableName);
            if (table == null) {
                throw new NotFoundException(req, "No table named '" + tableName + "'");
            } else {
                return handleTableRequest(req, pathOffset + 1, table);
            }
        }
    }
    
    private RestResponse handleTableRequest(RestRequest req, int pathOffset, TableDefinition table) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            req.assertGET();
            return getTable(req, table);
        } else {
            String resource = req.getPathSegment(pathOffset);
            switch (resource) {
            case "data":
                return getTableData(req, table);
            default:
                throw new NotFoundException(req, "No resource '" + resource + "' for table '" + table.getName() + "'");                
            }
        }
    }
    
    private RestResponse listTables(RestRequest req, YarchDatabase ydb) throws RestException {
        ListTablesResponse.Builder responseb = ListTablesResponse.newBuilder();
        for (TableDefinition def : ydb.getTableDefinitions()) {
            responseb.addTable(ArchiveHelper.toTableInfo(def));
        }
        return new RestResponse(req, responseb.build(), SchemaRest.ListTablesResponse.WRITE);
    }
    
    private RestResponse getTable(RestRequest req, TableDefinition table) throws RestException {
        TableInfo response = ArchiveHelper.toTableInfo(table);
        return new RestResponse(req, response, SchemaArchive.TableInfo.WRITE);
    }
    
    private RestResponse getTableData(RestRequest req, TableDefinition table) throws RestException {
        List<String> cols = null;        
        if (req.hasQueryParameter("cols")) {
            cols = new ArrayList<>(); // Order, and non-unique
            for (String para : req.getQueryParameterList("cols")) {
                for (String col : para.split(",")) {
                    cols.add(col.trim());
                }
            }
        }
        long pos = req.getQueryParameterAsLong("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);
        
        StringBuilder buf = new StringBuilder("select ");
        if (cols == null) {
            buf.append("*");
        } else if (cols.isEmpty()) {
            throw new BadRequestException("No columns are specified.");
        } else {
            for (int i = 0; i < cols.size(); i++) {
                if (i != 0) buf.append(", ");
                buf.append(cols.get(i));
            }
        }
        buf.append(" from ").append(table.getName());
        if (RestUtils.asksDescending(req, true)) {
            buf.append(" order desc");
        }
        
        String sql = buf.toString();
        
        TableData.Builder responseb = TableData.newBuilder();
        RestStreams.streamAndWait(req, sql, new LimitedStreamSubscriber(pos, limit) {
            
            @Override
            public void onTuple(Tuple tuple) {
                TableRecord.Builder rec = TableRecord.newBuilder();
                rec.addAllColumn(ArchiveHelper.toColumnDataList(tuple));
                responseb.addRecord(rec); // TODO estimate byte size
            }
        });
        
        return new RestResponse(req, responseb.build(), SchemaArchive.TableData.WRITE);
    }
    
    private RestResponse handleStreamsRequest(RestRequest req, int pathOffset, YarchDatabase ydb) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            req.assertGET();
            return listStreams(req, ydb);
        } else {
            String streamName = req.getPathSegment(pathOffset);
            Stream stream = ydb.getStream(streamName);
            if (stream == null) {
                throw new NotFoundException(req, "No stream named '" + streamName + "'");
            } else {
                return handleStreamRequest(req, pathOffset + 1, stream);
            }
        }
    }
    
    private RestResponse handleStreamRequest(RestRequest req, int pathOffset, Stream stream) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            req.assertGET();
            return getStream(req, stream);
        } else {
            String resource = req.getPathSegment(pathOffset + 1);
            throw new NotFoundException(req, "No resource '" + resource + "' for stream '" + stream.getName() +  "'");
        }
    } 
    
    private RestResponse listStreams(RestRequest req, YarchDatabase ydb) throws RestException {
        ListStreamsResponse.Builder responseb = ListStreamsResponse.newBuilder();
        for (AbstractStream stream : ydb.getStreams()) {
            responseb.addStream(ArchiveHelper.toStreamInfo(stream));
        }
        return new RestResponse(req, responseb.build(), SchemaRest.ListStreamsResponse.WRITE);
    }
    
    private RestResponse getStream(RestRequest req, Stream stream) throws RestException {
        StreamInfo response = ArchiveHelper.toStreamInfo(stream);
        return new RestResponse(req, response, SchemaArchive.StreamInfo.WRITE);
    }
    
    private RestResponse handleParametersRequest(RestRequest req, int pathOffset) throws RestException {
        MatchResult<Parameter> mr = RestUtils.matchParameterName(req, pathOffset);
        if (!mr.matches()) {
            throw new NotFoundException(req);
        }
        
        pathOffset = mr.getPathOffset();
        if (req.hasPathSegment(pathOffset)) {
            switch (req.getPathSegment(pathOffset)) {
            case "samples":
                return getParameterSamples(req, mr.getRequestedId(), mr.getMatch());
            default:
                throw new NotFoundException(req, "No resource '" + req.getPathSegment(pathOffset) + "' for parameter " + mr.getRequestedId());
            }
        } else {
            return listParameterHistory(req, mr.getRequestedId());
        }
    }
    
    /**
     * A series is a list of samples that are determined in one-pass while processing a stream result.
     * Final API unstable.
     * <p>
     * If no query parameters are defined, the series covers *all* data.
     */
    private RestResponse getParameterSamples(RestRequest req, NamedObjectId id, Parameter p) throws RestException {
        ParameterType ptype = p.getParameterType();
        if (ptype == null) {
            throw new BadRequestException("Requested parameter has no type");
        } else if (!(ptype instanceof FloatParameterType) && !(ptype instanceof IntegerParameterType)) {
            throw new BadRequestException("Only integer or float parameters can be sampled. Got " + ptype.getClass());
        }
        
        ReplayRequest.Builder rr = ReplayRequest.newBuilder().setEndAction(EndAction.QUIT);
        rr.setParameterRequest(ParameterReplayRequest.newBuilder().addNameFilter(id));
        rr.setSpeed(ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP));
        
        if (req.hasQueryParameter("start")) {
            rr.setStart(req.getQueryParameterAsDate("start"));
        }
        rr.setStop(req.getQueryParameterAsDate("stop", TimeEncoding.getWallclockTime()));
        
        RestParameterSampler sampler = new RestParameterSampler(rr.getStop());
        
        RestReplays.replayAndWait(req, rr.build(), new RestReplayListener() {

            @Override
            public void newData(ProtoDataType type, MessageLite data) {
                ParameterData pdata = (ParameterData) data;
                for (ParameterValue pval : pdata.getParameterList()) {
                    switch (pval.getEngValue().getType()) {
                    case DOUBLE:
                        sampler.process(pval.getGenerationTime(), pval.getEngValue().getDoubleValue());
                        break;
                    case FLOAT:
                        sampler.process(pval.getGenerationTime(), pval.getEngValue().getFloatValue());
                        break;
                    case SINT32:
                        sampler.process(pval.getGenerationTime(), pval.getEngValue().getSint32Value());
                        break;
                    case SINT64:
                        sampler.process(pval.getGenerationTime(), pval.getEngValue().getSint64Value());
                        break;
                    case UINT32:
                        sampler.process(pval.getGenerationTime(), pval.getEngValue().getUint32Value()&0xFFFFFFFFL);
                        break;
                    case UINT64:
                        sampler.process(pval.getGenerationTime(), pval.getEngValue().getUint64Value());
                        break;
                    default:
                        log.warn("Unexpected value type " + pval.getEngValue().getType());
                    }
                }
            }
        });
        
        SampleSeries.Builder series = SampleSeries.newBuilder();
        for (Sample s : sampler.collect()) {
            series.addSample(ArchiveHelper.toGPBSample(s));
        }
        
        return new RestResponse(req, series.build(), SchemaPvalue.SampleSeries.WRITE);
    }
    
    private RestResponse listParameterHistory(RestRequest req, NamedObjectId id) throws RestException {
        long pos = req.getQueryParameterAsLong("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);
        ReplayRequest rr = ArchiveHelper.toParameterReplayRequest(req, id, true);

        if (req.asksFor(CSV_MIME_TYPE)) {
            ByteBuf buf = req.getChannelHandlerContext().alloc().buffer();
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new ByteBufOutputStream(buf)))) {
                List<NamedObjectId> idList = Arrays.asList(id);
                ParameterFormatter csvFormatter = new ParameterFormatter(bw, idList);
                limit++; // Allow one extra line for the CSV header
                RestReplays.replayAndWait(req, rr, new LimitedReplayListener(pos, limit) {

                    @Override
                    public void onNewData(ProtoDataType type, MessageLite data) {
                        ParameterData pdata = (ParameterData) data;
                        try {
                            csvFormatter.writeParameters(pdata.getParameterList());
                        } catch (IOException e) {
                            log.error("Error while writing parameter line", e);
                        }
                    }
                });
            } catch (IOException e) {
                throw new InternalServerErrorException(e);
            }
            return new RestResponse(req, CSV_MIME_TYPE, buf);
        } else {
            ParameterData.Builder resultb = ParameterData.newBuilder();
            RestReplays.replayAndWait(req, rr, new LimitedReplayListener(pos, limit) {
                
                @Override
                public void onNewData(ProtoDataType type, MessageLite data) {
                    ParameterData pdata = (ParameterData) data;
                    resultb.addAllParameter(pdata.getParameterList());
                }
            });
            return new RestResponse(req, resultb.build(), SchemaPvalue.ParameterData.WRITE);
        }
    }
    
    private RestResponse listEvents(RestRequest req) throws RestException {
        long pos = req.getQueryParameterAsLong("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);
        
        StringBuilder sqlb = new StringBuilder("select * from ").append(EventRecorder.TABLE_NAME);
        IntervalResult ir = RestUtils.scanForInterval(req);
        if (ir.hasInterval()) {
            sqlb.append(" where ").append(ir.asSqlCondition("gentime"));
        }
        if (RestUtils.asksDescending(req, true)) {
            sqlb.append(" order desc");
        }
        
        if (req.asksFor(CSV_MIME_TYPE)) {
            ByteBuf buf = req.getChannelHandlerContext().alloc().buffer();
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new ByteBufOutputStream(buf)));
            CsvWriter w = new CsvWriter(bw, '\t');
            try {
                w.writeRecord(ArchiveHelper.EVENT_CSV_HEADER);
            } catch (IOException e) {
                throw new InternalServerErrorException(e);
            }
                
            RestStreams.streamAndWait(req, sqlb.toString(), new LimitedStreamSubscriber(pos, limit) {

                @Override
                public void onTuple(Tuple tuple) {
                    try {
                        w.writeRecord(ArchiveHelper.tupleToCSVEvent(tuple));
                    } catch (IOException e) {
                        // TODO maybe support passing up as rest exception using custom listeners
                        log.error("Could not write csv record ", e);
                    }
                }
            });
            w.close();
            return new RestResponse(req, CSV_MIME_TYPE, buf);
        } else {
            ListEventsResponse.Builder responseb = ListEventsResponse.newBuilder();
            RestStreams.streamAndWait(req, sqlb.toString(), new LimitedStreamSubscriber(pos, limit) {

                @Override
                public void onTuple(Tuple tuple) {
                    Event.Builder event = Event.newBuilder((Event) tuple.getColumn("body"));
                    event.setGenerationTimeUTC(TimeEncoding.toString(event.getGenerationTime()));
                    event.setReceptionTimeUTC(TimeEncoding.toString(event.getReceptionTime()));
                    responseb.addEvent(event);    
                }
            });
            
            return new RestResponse(req, responseb.build(), SchemaRest.ListEventsResponse.WRITE);
        }
    }
    
    private RestResponse listPackets(RestRequest req) throws RestException {
        long pos = req.getQueryParameterAsLong("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);
        
        StringBuilder sqlb = new StringBuilder("select * from ").append(XtceTmRecorder.TABLE_NAME);
        IntervalResult ir = RestUtils.scanForInterval(req);
        if (ir.hasInterval()) {
            sqlb.append(" where ").append(ir.asSqlCondition("gentime"));
        }
        if (RestUtils.asksDescending(req, true)) {
            sqlb.append(" order desc");
        }
        
        ListPacketsResponse.Builder responseb = ListPacketsResponse.newBuilder();
        RestStreams.streamAndWait(req, sqlb.toString(), new LimitedStreamSubscriber(pos, limit) {

            @Override
            public void onTuple(Tuple tuple) {
                TmPacketData pdata = ArchiveHelper.tupleToPacketData(tuple);
                responseb.addPacket(pdata);
            }
        });
        
        return new RestResponse(req, responseb.build(), SchemaRest.ListPacketsResponse.WRITE);
    }
    
    /**
     * Lists all tags (optionally filtered by request-body)
     */
    private RestResponse listTags(RestRequest req) throws RestException {
        String instance = req.getFromContext(RestRequest.CTX_INSTANCE);
        TagDb tagDb = getTagDb(instance);
        
        // Start with default open-ended
        TimeInterval interval = new TimeInterval();
        
        // Check any additional options
        if (req.hasBody()) {
            GetTagsRequest request = req.bodyAsMessage(SchemaArchive.GetTagsRequest.MERGE).build();
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
        return new RestResponse(req, responseb.build(), SchemaArchive.GetTagsResponse.WRITE);
    }
    
    /**
     * Adds a new tag. The newly added tag is returned as a response so the user
     * knows the assigned id.
     * <p>
     * POST /archive/:instance/tags
     */
    private RestResponse insertTag(RestRequest req) throws RestException {
        String instance = req.getFromContext(RestRequest.CTX_INSTANCE);
        TagDb tagDb = getTagDb(instance);
        InsertTagRequest request = req.bodyAsMessage(SchemaArchive.InsertTagRequest.MERGE).build();
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
        return new RestResponse(req, responseb.build(), SchemaArchive.InsertTagResponse.WRITE);
    }
    
    /**
     * Updates an existing tag. Returns nothing
     * <p>
     * PUT /archive/:instance/tags/(tag-time)/(tag-id)
     */
    private RestResponse updateTag(RestRequest req, long tagTime, int tagId) throws RestException {
        String instance = req.getFromContext(RestRequest.CTX_INSTANCE);
        TagDb tagDb = getTagDb(instance);
        UpdateTagRequest request = req.bodyAsMessage(SchemaArchive.UpdateTagRequest.MERGE).build();
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
        
        String instance = req.getFromContext(RestRequest.CTX_INSTANCE);
        TagDb tagDb = getTagDb(instance);
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
