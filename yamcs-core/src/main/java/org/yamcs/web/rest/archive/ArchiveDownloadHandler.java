package org.yamcs.web.rest.archive;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.yamcs.archive.EventRecorder;
import org.yamcs.archive.GPBHelper;
import org.yamcs.archive.XtceTmRecorder;
import org.yamcs.cmdhistory.CommandHistoryRecorder;
import org.yamcs.protobuf.Archive.TableData.TableRecord;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.SchemaArchive;
import org.yamcs.protobuf.SchemaCommanding;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.tctm.TmProviderAdapter;
import org.yamcs.web.rest.BadRequestException;
import org.yamcs.web.rest.NotFoundException;
import org.yamcs.web.rest.ParameterReplayToChunkedCSVEncoder;
import org.yamcs.web.rest.ParameterReplayToChunkedProtobufEncoder;
import org.yamcs.web.rest.RestException;
import org.yamcs.web.rest.RestParameterReplayListener;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequestHandler;
import org.yamcs.web.rest.RestResponse;
import org.yamcs.web.rest.RestStreams;
import org.yamcs.web.rest.RestUtils;
import org.yamcs.web.rest.RestUtils.IntervalResult;
import org.yamcs.web.rest.SqlBuilder;
import org.yamcs.web.rest.StreamToChunkedCSVEncoder;
import org.yamcs.web.rest.StreamToChunkedProtobufEncoder;
import org.yamcs.web.rest.StreamToChunkedTransferEncoder;
import org.yamcs.web.rest.mdb.MissionDatabaseHelper;
import org.yamcs.web.rest.mdb.MissionDatabaseHelper.MatchResult;
import org.yamcs.xtce.Parameter;
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
public class ArchiveDownloadHandler extends RestRequestHandler {
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        String instance = req.getFromContext(RestRequest.CTX_INSTANCE);
        if (!req.hasPathSegment(pathOffset)) {
            throw new NotFoundException(req);
        } else {
            switch (req.getPathSegment(pathOffset)) {
            case "parameters":
                req.assertGET();
                MatchResult<Parameter> mr = MissionDatabaseHelper.matchParameterName(req, pathOffset + 1);
                if (!mr.matches()) {
                    throw new NotFoundException(req);
                } else {
                    NamedObjectId parameterId = mr.getRequestedId();
                    downloadParameter(req, parameterId);
                    return null;
                }
            case "packets":
                req.assertGET();
                downloadPackets(req);
                return null;
            case "commands":
                req.assertGET();
                downloadCommands(req);
                return null;
            case "events":
                req.assertGET();
                downloadEvents(req);
                return null;
            case "tables":
                req.assertGET();
                String tableName = req.getPathSegment(pathOffset + 1);
                TableDefinition table = YarchDatabase.getInstance(instance).getTable(tableName);
                if (table == null) {
                    throw new NotFoundException(req, "No table named '" + tableName + "'");
                } else {
                    downloadTableData(req, table);
                    return null;
                }
            default:
                throw new NotFoundException(req);
            }
        }
    }
    
    private void downloadParameter(RestRequest req, NamedObjectId id) throws RestException {
        ReplayRequest rr = ArchiveHelper.toParameterReplayRequest(req, id, false);
        boolean noRepeat = req.getQueryParameterAsBoolean("norepeat", false);
        
        if (req.asksFor(CSV_MIME_TYPE)) {
            List<NamedObjectId> idList = Arrays.asList(id);
            RestParameterReplayListener l = new ParameterReplayToChunkedCSVEncoder(req, idList);
            l.setNoRepeat(noRepeat);
            RestReplays.replay(req, rr, l);
        } else {
            RestParameterReplayListener l = new ParameterReplayToChunkedProtobufEncoder(req);
            l.setNoRepeat(noRepeat);
            RestReplays.replay(req, rr, l);
        }
    }
    
    private void downloadPackets(RestRequest req) throws RestException {
        Set<String> nameSet = new HashSet<>();
        for (String names : req.getQueryParameterList("name", Collections.emptyList())) {
            for (String name : names.split(",")) {
                nameSet.add(name.trim());
            }
        }
        
        SqlBuilder sqlb = new SqlBuilder(XtceTmRecorder.TABLE_NAME);
        IntervalResult ir = RestUtils.scanForInterval(req);
        if (ir.hasInterval()) {
            sqlb.where(ir.asSqlCondition("gentime"));
        }
        if (!nameSet.isEmpty()) {
            sqlb.where("pname in ('" + String.join("','", nameSet) + "')");
        }
        sqlb.descend(RestUtils.asksDescending(req, false));
        String sql = sqlb.toString();
        
        if (req.asksFor(BINARY_MIME_TYPE)) {
            RestStreams.stream(req, sql, new StreamToChunkedTransferEncoder(req, BINARY_MIME_TYPE) {
                @Override
                public void processTuple(Tuple tuple, ByteBufOutputStream bufOut) throws IOException {
                    byte[] raw = (byte[]) tuple.getColumn(TmProviderAdapter.PACKET_COLUMN);
                    bufOut.write(raw);
                }
            });
        } else {
            RestStreams.stream(req, sql, new StreamToChunkedProtobufEncoder<TmPacketData>(req, SchemaYamcs.TmPacketData.WRITE) {
                @Override
                public TmPacketData mapTuple(Tuple tuple) {
                    return GPBHelper.tupleToTmPacketData(tuple);
                }
            });
        }
    }
    
    private void downloadCommands(RestRequest req) throws RestException {
        Set<String> nameSet = new HashSet<>();
        for (String names : req.getQueryParameterList("name", Collections.emptyList())) {
            for (String name : names.split(",")) {
                nameSet.add(name.trim());
            }
        }
        
        SqlBuilder sqlb = new SqlBuilder(CommandHistoryRecorder.TABLE_NAME);
        IntervalResult ir = RestUtils.scanForInterval(req);
        if (ir.hasInterval()) {
            sqlb.where(ir.asSqlCondition("gentime"));
        }
        if (!nameSet.isEmpty()) {
            sqlb.where("cmdName in ('" + String.join("','", nameSet) + "')");
        }
        sqlb.descend(RestUtils.asksDescending(req, false));
        String sql = sqlb.toString();
        
        RestStreams.stream(req, sql, new StreamToChunkedProtobufEncoder<CommandHistoryEntry>(req, SchemaCommanding.CommandHistoryEntry.WRITE) {
            @Override
            public CommandHistoryEntry mapTuple(Tuple tuple) {
                return GPBHelper.tupleToCommandHistoryEntry(tuple);
            }
        });
    }
    
    private void downloadTableData(RestRequest req, TableDefinition table) throws RestException {
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
        sqlb.descend(RestUtils.asksDescending(req, false));
        String sql = sqlb.toString();
        
        RestStreams.stream(req, sql, new StreamToChunkedProtobufEncoder<TableRecord>(req, SchemaArchive.TableData.TableRecord.WRITE) {
            
            @Override
            public TableRecord mapTuple(Tuple tuple) {
                TableRecord.Builder rec = TableRecord.newBuilder();
                rec.addAllColumn(ArchiveHelper.toColumnDataList(tuple));
                return rec.build();
            }
        });
    }

    private void downloadEvents(RestRequest req) throws RestException {
        Set<String> sourceSet = new HashSet<>();
        for (String names : req.getQueryParameterList("source", Collections.emptyList())) {
            for (String name : names.split(",")) {
                sourceSet.add(name);
            }
        }

        SqlBuilder sqlb = new SqlBuilder(EventRecorder.TABLE_NAME);
        IntervalResult ir = RestUtils.scanForInterval(req);
        if (ir.hasInterval()) {
            sqlb.where(ir.asSqlCondition("gentime"));
        }
        if (!sourceSet.isEmpty()) {
            sqlb.where("source in ('" + String.join("','", sourceSet) + "')");
        }
        sqlb.descend(RestUtils.asksDescending(req, false));
        String sql = sqlb.toString();
        
        if (req.asksFor(CSV_MIME_TYPE)) {
            transferChunkedCSVEvents(req, sql);
        } else {
            transferChunkedProtobufEvents(req, sql);
        }
    }
    
    private void transferChunkedCSVEvents(RestRequest req, String sql) throws RestException {
        RestStreams.stream(req, sql, new StreamToChunkedCSVEncoder(req) {
            
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
    
    private void transferChunkedProtobufEvents(RestRequest req, String sql) throws RestException {
        RestStreams.stream(req, sql, new StreamToChunkedProtobufEncoder<Event>(req, SchemaYamcs.Event.WRITE) {
            
            @Override
            public Event mapTuple(Tuple tuple) {
                return ArchiveHelper.tupleToEvent(tuple);
            }
        });
    }
}
