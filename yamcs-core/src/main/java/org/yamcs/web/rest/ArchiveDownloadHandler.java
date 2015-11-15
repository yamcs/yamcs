package org.yamcs.web.rest;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.yamcs.archive.EventRecorder;
import org.yamcs.archive.XtceTmRecorder;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.web.rest.RestUtils.IntervalResult;
import org.yamcs.web.rest.RestUtils.MatchResult;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.Tuple;

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
            default:
                throw new NotFoundException(req);
            }
        }
    }
    
    private void downloadParameter(RestRequest req, NamedObjectId id) throws RestException {
        ReplayRequest rr = ArchiveHelper.toParameterReplayRequest(req, id, false);
        
        if (req.asksFor(CSV_MIME_TYPE)) {
            List<NamedObjectId> idList = Arrays.asList(id);
            RestReplays.replay(req, rr, new ReplayToChunkedParameterCSV(req, idList));
        } else {
            RestReplays.replay(req, rr, new ReplayToChunkedParameterProtobuf(req));
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
