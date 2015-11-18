package org.yamcs.web.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.yamcs.archive.EventRecorder;
import org.yamcs.archive.XtceTmRecorder;
import org.yamcs.protobuf.Archive.TableData.TableRecord;
import org.yamcs.protobuf.SchemaArchive;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.web.rest.RestUtils.IntervalResult;
import org.yamcs.web.rest.RestUtils.MatchResult;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;

import com.csvreader.CsvWriter;

/** 
 * Serves archived data through a web api.
 *
 * <p>Archive requests use chunked encoding with an unspecified content length, which enables
 * us to send large dumps without needing to determine a content length on the server. At the
 * moment every hornetq message from the archive replay is put in a separate chunk and flushed.
 */
public class ArchiveDownloadHandler extends RestRequestHandler {
    
    @Override
    public String getPath() {
        return "downloads";
    }
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        String instance = req.getFromContext(RestRequest.CTX_INSTANCE);
        if (!req.hasPathSegment(pathOffset)) {
            throw new NotFoundException(req);
        } else {
            switch (req.getPathSegment(pathOffset)) {
            case "parameters":
                req.assertGET();
                MatchResult<Parameter> mr = RestUtils.matchParameterName(req, pathOffset);
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
            RestParameterReplayListener l = new ReplayToChunkedParameterCSV(req, idList);
            l.setNoRepeat(noRepeat);
            RestReplays.replay(req, rr, l);
        } else {
            RestParameterReplayListener l = new ReplayToChunkedParameterProtobuf(req);
            l.setNoRepeat(noRepeat);
            RestReplays.replay(req, rr, l);
        }
    }
    
    private void downloadPackets(RestRequest req) throws RestException {
        StringBuilder sqlb = new StringBuilder("select * from ").append(XtceTmRecorder.TABLE_NAME);
        IntervalResult ir = RestUtils.scanForInterval(req);
        if (ir.hasInterval()) {
            sqlb.append(" where ").append(ir.asSqlCondition("gentime"));
        }
        if (RestUtils.asksDescending(req, false)) {
            sqlb.append(" order desc");
        }
        String sql = sqlb.toString();
        
        RestStreams.stream(req, sql, new StreamToChunkedProtobufEncoder<TmPacketData>(req, SchemaYamcs.TmPacketData.WRITE) {
            
            @Override
            public TmPacketData mapTuple(Tuple tuple) {
                return ArchiveHelper.tupleToPacketData(tuple);
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
        
        StringBuilder sqlb = new StringBuilder("select ");
        if (cols == null) {
            sqlb.append("*");
        } else if (cols.isEmpty()) {
            throw new BadRequestException("No columns are specified.");
        } else {
            for (int i = 0; i < cols.size(); i++) {
                if (i != 0) sqlb.append(", ");
                sqlb.append(cols.get(i));
            }
        }
        sqlb.append(" from ").append(table.getName());
        if (RestUtils.asksDescending(req, false)) {
            sqlb.append(" order desc");
        }
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
        StringBuilder sqlb = new StringBuilder("select * from ").append(EventRecorder.TABLE_NAME);
        IntervalResult ir = RestUtils.scanForInterval(req);
        if (ir.hasInterval()) {
            sqlb.append(" where ").append(ir.asSqlCondition("gentime"));
        }
        if (RestUtils.asksDescending(req, false)) {
            sqlb.append(" order desc");
        }
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
