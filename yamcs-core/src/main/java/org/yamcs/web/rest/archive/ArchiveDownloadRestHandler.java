package org.yamcs.web.rest.archive;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.yamcs.api.MediaType;
import org.yamcs.archive.EventRecorder;
import org.yamcs.archive.GPBHelper;
import org.yamcs.archive.XtceTmRecorder;
import org.yamcs.cmdhistory.CommandHistoryRecorder;
import org.yamcs.protobuf.Archive.TableData.TableRecord;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.SchemaArchive;
import org.yamcs.protobuf.SchemaCommanding;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.ParameterReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.tctm.TmProviderAdapter;
import org.yamcs.ui.ParameterRetrievalGui;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.rest.ParameterReplayToChunkedCSVEncoder;
import org.yamcs.web.rest.ParameterReplayToChunkedProtobufEncoder;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestParameterReplayListener;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequest.IntervalResult;
import org.yamcs.web.rest.RestStreams;
import org.yamcs.web.rest.Route;
import org.yamcs.web.rest.SqlBuilder;
import org.yamcs.web.rest.StreamToChunkedCSVEncoder;
import org.yamcs.web.rest.StreamToChunkedProtobufEncoder;
import org.yamcs.web.rest.StreamToChunkedTransferEncoder;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;

import com.csvreader.CsvWriter;

import io.netty.buffer.ByteBufOutputStream;

/** 
 * Serves archived data through a web api.
 *
 * <p>Archive requests use chunked encoding with an unspecified content length, which enables
 * us to send large dumps without needing to determine a content length on the server.
 */
public class ArchiveDownloadRestHandler extends RestHandler {
    
    // TODO needs more work. Which is also why it's not documented yet
    // In general, i'm just not the biggest fan of these 'profiles'. But if we do it, we should
    // support POST, PATCH, GET etc
    @Route(path = "/api/archive/:instance/downloads/parameters", method = "GET")
    public void downloadParameters(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        
        ReplayRequest.Builder rr = ReplayRequest.newBuilder().setEndAction(EndAction.QUIT);
        rr.setSpeed(ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP));
        
        IntervalResult ir = req.scanForInterval();
        if (ir.hasStart()) {
            rr.setStart(req.getQueryParameterAsDate("start"));
        }
        if (ir.hasStop()) {
            rr.setStop(req.getQueryParameterAsDate("stop"));
        }
        
        if (req.getQueryParameters().containsKey("profile")) {
            String profile = req.getQueryParameter("profile");
            String filename = YarchDatabase.getHome() + "/" + instance + "/profiles/" + profile + ".profile";
            List<NamedObjectId> ids;
            try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
                ids = ParameterRetrievalGui.loadParameters(reader);
                rr.setParameterRequest(ParameterReplayRequest.newBuilder().addAllNameFilter(ids));
            } catch (FileNotFoundException e) {
                throw new BadRequestException("No profile '" + profile + "' could be found");
            } catch (IOException e) {
                throw new InternalServerErrorException("Could not load profile file", e);
            }
            
            if (req.asksFor(MediaType.CSV)) {
                RestParameterReplayListener l = new ParameterReplayToChunkedCSVEncoder(req, ids);
                RestReplays.replay(instance, req.getAuthToken(), rr.build(), l);
            } else {
                RestParameterReplayListener l = new ParameterReplayToChunkedProtobufEncoder(req);
                RestReplays.replay(instance, req.getAuthToken(), rr.build(), l);
            }
        } else {
            throw new BadRequestException("profile query parameter must be specified. This operation needs more integration work");
        }
    }
    
    @Route(path = "/api/archive/:instance/downloads/parameters/:name*", method = "GET")
    public void downloadParameter(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        
        XtceDb mdb = XtceDbFactory.getInstance(instance);
        Parameter p = verifyParameter(req, mdb, req.getRouteParam("name"));
        
        ReplayRequest rr = ArchiveHelper.toParameterReplayRequest(req, p, false);
        boolean noRepeat = req.getQueryParameterAsBoolean("norepeat", false);
        
        if (req.asksFor(MediaType.CSV)) {
            List<NamedObjectId> idList = Arrays.asList(NamedObjectId.newBuilder().setName(p.getQualifiedName()).build());
            RestParameterReplayListener l = new ParameterReplayToChunkedCSVEncoder(req, idList);
            l.setNoRepeat(noRepeat);
            RestReplays.replay(instance, req.getAuthToken(), rr, l);
        } else {
            RestParameterReplayListener l = new ParameterReplayToChunkedProtobufEncoder(req);
            l.setNoRepeat(noRepeat);
            RestReplays.replay(instance, req.getAuthToken(), rr, l);
        }
    }
    
    @Route(path = "/api/archive/:instance/downloads/packets", method = "GET")
    public void downloadPackets(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        
        Set<String> nameSet = new HashSet<>();
        for (String names : req.getQueryParameterList("name", Collections.emptyList())) {
            for (String name : names.split(",")) {
                nameSet.add(name.trim());
            }
        }
        
        SqlBuilder sqlb = new SqlBuilder(XtceTmRecorder.TABLE_NAME);
        IntervalResult ir = req.scanForInterval();
        if (ir.hasInterval()) {
            sqlb.where(ir.asSqlCondition("gentime"));
        }
        if (!nameSet.isEmpty()) {
            sqlb.where("pname in ('" + String.join("','", nameSet) + "')");
        }
        sqlb.descend(req.asksDescending(false));
        String sql = sqlb.toString();
        
        if (req.asksFor(MediaType.OCTET_STREAM)) {
            RestStreams.stream(instance, sql, new StreamToChunkedTransferEncoder(req, MediaType.OCTET_STREAM) {
                @Override
                public void processTuple(Tuple tuple, ByteBufOutputStream bufOut) throws IOException {
                    byte[] raw = (byte[]) tuple.getColumn(TmProviderAdapter.PACKET_COLUMN);
                    bufOut.write(raw);
                }
            });
        } else {
            RestStreams.stream(instance, sql, new StreamToChunkedProtobufEncoder<TmPacketData>(req, SchemaYamcs.TmPacketData.WRITE) {
                @Override
                public TmPacketData mapTuple(Tuple tuple) {
                    return GPBHelper.tupleToTmPacketData(tuple);
                }
            });
        }
    }
    
    @Route(path = "/api/archive/:instance/downloads/commands", method = "GET")
    public void downloadCommands(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        
        Set<String> nameSet = new HashSet<>();
        for (String names : req.getQueryParameterList("name", Collections.emptyList())) {
            for (String name : names.split(",")) {
                nameSet.add(name.trim());
            }
        }
        
        SqlBuilder sqlb = new SqlBuilder(CommandHistoryRecorder.TABLE_NAME);
        IntervalResult ir = req.scanForInterval();
        if (ir.hasInterval()) {
            sqlb.where(ir.asSqlCondition("gentime"));
        }
        if (!nameSet.isEmpty()) {
            sqlb.where("cmdName in ('" + String.join("','", nameSet) + "')");
        }
        sqlb.descend(req.asksDescending(false));
        String sql = sqlb.toString();
        
        RestStreams.stream(instance, sql, new StreamToChunkedProtobufEncoder<CommandHistoryEntry>(req, SchemaCommanding.CommandHistoryEntry.WRITE) {
            @Override
            public CommandHistoryEntry mapTuple(Tuple tuple) {
                return GPBHelper.tupleToCommandHistoryEntry(tuple);
            }
        });
    }
    
    @Route(path = "/api/archive/:instance/downloads/tables/:name", method = "GET")
    public void downloadTableData(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        YarchDatabase ydb = YarchDatabase.getInstance(instance);
        
        TableDefinition table = verifyTable(req, ydb, req.getRouteParam("name"));
        
        List<String> cols = null;
        if (req.hasQueryParameter("cols")) {
            cols = new ArrayList<>(); // Order, and non-unique
            for (String para : req.getQueryParameterList("cols")) {
                for (String col : para.split(",")) {
                    cols.add(col.trim());
                }
            }
        }
        
        SqlBuilder sqlb = new SqlBuilder(table.getName());
        if (cols != null) {
            if (cols.isEmpty()) {
                throw new BadRequestException("No columns were specified");
            } else {
                cols.forEach(col -> sqlb.select(col));
            }
        }
        sqlb.descend(req.asksDescending(false));
        String sql = sqlb.toString();
        
        RestStreams.stream(instance, sql, new StreamToChunkedProtobufEncoder<TableRecord>(req, SchemaArchive.TableData.TableRecord.WRITE) {
            
            @Override
            public TableRecord mapTuple(Tuple tuple) {
                TableRecord.Builder rec = TableRecord.newBuilder();
                rec.addAllColumn(ArchiveHelper.toColumnDataList(tuple));
                return rec.build();
            }
        });
    }

    @Route(path = "/api/archive/:instance/downloads/events", method = "GET")
    public void downloadEvents(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        
        Set<String> sourceSet = new HashSet<>();
        for (String names : req.getQueryParameterList("source", Collections.emptyList())) {
            for (String name : names.split(",")) {
                sourceSet.add(name);
            }
        }

        SqlBuilder sqlb = new SqlBuilder(EventRecorder.TABLE_NAME);
        IntervalResult ir = req.scanForInterval();
        if (ir.hasInterval()) {
            sqlb.where(ir.asSqlCondition("gentime"));
        }
        if (!sourceSet.isEmpty()) {
            sqlb.where("source in ('" + String.join("','", sourceSet) + "')");
        }
        sqlb.descend(req.asksDescending(false));
        String sql = sqlb.toString();
        
        if (req.asksFor(MediaType.CSV)) {
            transferChunkedCSVEvents(req, instance, sql);
        } else {
            transferChunkedProtobufEvents(req, instance, sql);
        }
    }
    
    private void transferChunkedCSVEvents(RestRequest req, String instance, String sql) throws HttpException {
        RestStreams.stream(instance, sql, new StreamToChunkedCSVEncoder(req) {
            
            @Override
            public String[] getCSVHeader() {
                return ArchiveHelper.EVENT_CSV_HEADER;
            }
            
            @Override
            public void processTuple(Tuple tuple, CsvWriter csvWriter) throws IOException {
                String[] record = ArchiveHelper.tupleToCSVEvent(tuple);
                csvWriter.writeRecord(record);
            }
        });
    }
    
    private void transferChunkedProtobufEvents(RestRequest req, String instance, String sql) throws HttpException {
        RestStreams.stream(instance, sql, new StreamToChunkedProtobufEncoder<Event>(req, SchemaYamcs.Event.WRITE) {
            
            @Override
            public Event mapTuple(Tuple tuple) {
                return ArchiveHelper.tupleToEvent(tuple);
            }
        });
    }
}
