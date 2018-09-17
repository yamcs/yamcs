package org.yamcs.web.rest.archive;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.yamcs.StandardTupleDefinitions;
import org.yamcs.YamcsServer;
import org.yamcs.api.MediaType;
import org.yamcs.archive.CommandHistoryRecorder;
import org.yamcs.archive.EventRecorder;
import org.yamcs.archive.GPBHelper;
import org.yamcs.archive.XtceTmRecorder;
import org.yamcs.protobuf.Archive.TableData.TableRecord;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Rest.BulkDownloadParameterValueRequest;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.ParameterReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.GpbExtensionRegistry;
import org.yamcs.web.HttpException;
import org.yamcs.web.HttpServer;
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
import org.yamcs.yarch.YarchDatabaseInstance;

import com.csvreader.CsvWriter;

import io.netty.buffer.ByteBufOutputStream;

/**
 * Serves archived data through a web api.
 *
 * <p>
 * Archive requests use chunked encoding with an unspecified content length, which enables us to send large dumps
 * without needing to determine a content length on the server.
 */
public class ArchiveDownloadRestHandler extends RestHandler {

    private GpbExtensionRegistry gpbExtensionRegistry;

    @Route(path = "/api/archive/:instance/downloads/parameters", method = { "GET", "POST" })
    public void downloadParameters(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));

        ReplayRequest.Builder rr = ReplayRequest.newBuilder().setEndAction(EndAction.QUIT);
        rr.setSpeed(ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP));

        List<NamedObjectId> ids = new ArrayList<>();
        XtceDb mdb = XtceDbFactory.getInstance(instance);
        String namespace = null;

        // First try from body
        if (req.hasBody()) {
            BulkDownloadParameterValueRequest request = req
                    .bodyAsMessage(BulkDownloadParameterValueRequest.newBuilder()).build();
            if (request.hasStart()) {
                rr.setStart(RestRequest.parseTime(request.getStart()));
            }
            if (request.hasStop()) {
                rr.setStop(RestRequest.parseTime(request.getStop()));
            }
            for (NamedObjectId id : request.getIdList()) {
                Parameter p = mdb.getParameter(id);
                if (p == null) {
                    throw new BadRequestException("Invalid parameter name specified " + id);
                }
                checkObjectPrivileges(req, ObjectPrivilegeType.ReadParameter, p.getQualifiedName());
                ids.add(id);
            }
            if (request.hasNamespace()) {
                namespace = request.getNamespace();
            }
        }

        // Next, try query param (potentially overriding previous)
        IntervalResult ir = req.scanForInterval();
        if (ir.hasStart()) {
            rr.setStart(req.getQueryParameterAsDate("start"));
        }
        if (ir.hasStop()) {
            rr.setStop(req.getQueryParameterAsDate("stop"));
        }
        if (req.hasQueryParameter("namespace")) {
            namespace = req.getQueryParameter("namespace");
        }
        if (req.hasQueryParameter("parameters")) {
            for (String para : req.getQueryParameterList("parameters")) {
                for (String name : para.split(",")) {
                    NamedObjectId id;
                    if (namespace == null) {
                        id = NamedObjectId.newBuilder().setName(name).build();
                    } else {
                        id = NamedObjectId.newBuilder().setNamespace(namespace).setName(name).build();
                    }
                    Parameter p = mdb.getParameter(id);
                    if (p == null) {
                        throw new BadRequestException("Invalid parameter name specified " + id);
                    }
                    checkObjectPrivileges(req, ObjectPrivilegeType.ReadParameter, p.getQualifiedName());
                    ids.add(id);
                }
            }
        }

        if (ids.isEmpty()) {
            for (Parameter p : mdb.getParameters()) {
                if (!hasObjectPrivilege(req, ObjectPrivilegeType.ReadParameter, p.getQualifiedName())) {
                    continue;
                }
                if (namespace != null) {
                    String alias = p.getAlias(namespace);
                    if (alias != null) {
                        ids.add(NamedObjectId.newBuilder().setNamespace(namespace).setName(alias).build());
                    }
                } else {
                    ids.add(NamedObjectId.newBuilder().setName(p.getQualifiedName()).build());
                }
            }
        }
        rr.setParameterRequest(ParameterReplayRequest.newBuilder().addAllNameFilter(ids));

        String filename = "parameter-data";
        if (req.asksFor(MediaType.CSV)) {
            // Added on-demand for CSV (for Protobuf this is always added)
            boolean addRaw = false;
            boolean addMonitoring = false;
            if (req.hasQueryParameter("extra")) {
                for (String para : req.getQueryParameterList("extra")) {
                    for (String option : para.split(",")) {
                        if (option.equals("raw")) {
                            addRaw = true;
                        } else if (option.equals("monitoring")) {
                            addMonitoring = true;
                        } else {
                            throw new BadRequestException("Unexpected option for parameter 'extra': " + option);
                        }
                    }
                }
            }
            RestParameterReplayListener l = new ParameterReplayToChunkedCSVEncoder(req, ids, addRaw, addMonitoring,
                    filename);
            RestReplays.replay(instance, req.getUser(), rr.build(), l);
        } else {
            RestParameterReplayListener l = new ParameterReplayToChunkedProtobufEncoder(req, filename);
            RestReplays.replay(instance, req.getUser(), rr.build(), l);
        }
    }

    @Route(path = "/api/archive/:instance/downloads/parameters/:name*", method = "GET")
    public void downloadParameter(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        NamedObjectId requestedId = verifyParameterId(req, mdb, req.getRouteParam("name"));

        ReplayRequest rr = ArchiveHelper.toParameterReplayRequest(req, requestedId, false);
        boolean noRepeat = req.getQueryParameterAsBoolean("norepeat", false);

        String filename = requestedId.getName();
        if (req.asksFor(MediaType.CSV)) {
            // Added on-demand for CSV (for Protobuf this is always added)
            boolean addRaw = false;
            boolean addMonitoring = false;
            if (req.hasQueryParameter("extra")) {
                for (String para : req.getQueryParameterList("extra")) {
                    for (String option : para.split(",")) {
                        if (option.equals("raw")) {
                            addRaw = true;
                        } else if (option.equals("monitoring")) {
                            addMonitoring = true;
                        } else {
                            throw new BadRequestException("Unexpected option for parameter 'extra': " + option);
                        }
                    }
                }
            }
            List<NamedObjectId> idList = Arrays.asList(requestedId);
            RestParameterReplayListener l = new ParameterReplayToChunkedCSVEncoder(req, idList, addRaw, addMonitoring,
                    filename);
            l.setNoRepeat(noRepeat);
            System.out.println("will do replay " + rr);
            RestReplays.replay(instance, req.getUser(), rr, l);
        } else {
            RestParameterReplayListener l = new ParameterReplayToChunkedProtobufEncoder(req, filename);
            l.setNoRepeat(noRepeat);
            RestReplays.replay(instance, req.getUser(), rr, l);
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

        checkObjectPrivileges(req, ObjectPrivilegeType.ReadPacket, nameSet);

        SqlBuilder sqlb = new SqlBuilder(XtceTmRecorder.TABLE_NAME);
        IntervalResult ir = req.scanForInterval();
        if (ir.hasInterval()) {
            sqlb.where(ir.asSqlCondition("gentime"));
        }
        if (!nameSet.isEmpty()) {
            sqlb.whereColIn("pname", nameSet);
        }
        sqlb.descend(req.asksDescending(false));
        String sql = sqlb.toString();

        String filename = "packets";
        if (req.asksFor(MediaType.OCTET_STREAM)) {
            RestStreams.stream(instance, sql, sqlb.getQueryArguments(),
                    new StreamToChunkedTransferEncoder(req, MediaType.OCTET_STREAM, filename + ".raw") {
                        @Override
                        public void processTuple(Tuple tuple, ByteBufOutputStream bufOut) throws IOException {
                            byte[] raw = (byte[]) tuple.getColumn(StandardTupleDefinitions.TM_PACKET_COLUMN);
                            bufOut.write(raw);
                        }
                    });
        } else {
            RestStreams.stream(instance, sql, sqlb.getQueryArguments(),
                    new StreamToChunkedProtobufEncoder<TmPacketData>(req, filename) {
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

        checkObjectPrivileges(req, ObjectPrivilegeType.CommandHistory, nameSet);

        SqlBuilder sqlb = new SqlBuilder(CommandHistoryRecorder.TABLE_NAME);
        IntervalResult ir = req.scanForInterval();
        if (ir.hasInterval()) {
            sqlb.where(ir.asSqlCondition("gentime"));
        }
        if (!nameSet.isEmpty()) {
            sqlb.whereColIn("cmdName", nameSet);
        }
        sqlb.descend(req.asksDescending(false));
        String sql = sqlb.toString();

        RestStreams.stream(instance, sql, sqlb.getQueryArguments(),
                new StreamToChunkedProtobufEncoder<CommandHistoryEntry>(req, "commands") {
                    @Override
                    public CommandHistoryEntry mapTuple(Tuple tuple) {
                        return GPBHelper.tupleToCommandHistoryEntry(tuple);
                    }
                });
    }

    @Route(path = "/api/archive/:instance/downloads/tables/:name", method = "GET")
    public void downloadTableData(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        checkSystemPrivilege(req, SystemPrivilege.ReadTables);

        TableDefinition table = verifyTable(req, ydb, req.getRouteParam("name"));

        boolean dumpFormat = req.hasQueryParameter("format")
                && "dump".equalsIgnoreCase(req.getQueryParameter("format"));

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

        if (dumpFormat) {
            RestStreams.stream(instance, sql, new TableDumpEncoder(req));
        } else {
            RestStreams.stream(instance, sql, new StreamToChunkedProtobufEncoder<TableRecord>(req) {
                @Override
                public TableRecord mapTuple(Tuple tuple) {
                    TableRecord.Builder rec = TableRecord.newBuilder();
                    rec.addAllColumn(ArchiveHelper.toColumnDataList(tuple));
                    return rec.build();
                }
            });
        }
    }

    @Route(path = "/api/archive/:instance/downloads/events", method = "GET")
    public void downloadEvents(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        ArchiveEventRestHandler.verifyEventArchiveSupport(instance);
        checkSystemPrivilege(req, SystemPrivilege.ReadEvents);

        Set<String> sourceSet = new HashSet<>();
        for (String names : req.getQueryParameterList("source", Collections.emptyList())) {
            for (String name : names.split(",")) {
                sourceSet.add(name);
            }
        }

        String severity = req.getQueryParameter("severity", "INFO").toUpperCase();

        SqlBuilder sqlb = new SqlBuilder(EventRecorder.TABLE_NAME);
        IntervalResult ir = req.scanForInterval();
        if (ir.hasInterval()) {
            sqlb.where(ir.asSqlCondition("gentime"));
        }
        if (!sourceSet.isEmpty()) {
            sqlb.whereColIn("source", sourceSet);
        }
        switch (severity) {
        case "INFO":
            break;
        case "WATCH":
            sqlb.where("body.severity != 'INFO'");
            break;
        case "WARNING":
            sqlb.whereColIn("body.severity", Arrays.asList("WARNING", "DISTRESS", "CRITICAL", "SEVERE", "ERROR"));
            break;
        case "DISTRESS":
            sqlb.whereColIn("body.severity", Arrays.asList("DISTRESS", "CRITICAL", "SEVERE", "ERROR"));
            break;
        case "CRITICAL":
            sqlb.whereColIn("body.severity", Arrays.asList("CRITICAL", "SEVERE", "ERROR"));
            break;
        case "SEVERE":
            sqlb.whereColIn("body.severity", Arrays.asList("SEVERE", "ERROR"));
            break;
        default:
            sqlb.whereColIn("body.severity = ?", Arrays.asList(severity));
        }
        if (req.hasQueryParameter("q")) {
            sqlb.where("body.message like ?", "%" + req.getQueryParameter("q") + "%");
        }

        sqlb.descend(req.asksDescending(false));
        String sql = sqlb.toString();

        if (req.asksFor(MediaType.CSV)) {
            transferChunkedCSVEvents(req, instance, sql, sqlb.getQueryArguments());
        } else {
            transferChunkedProtobufEvents(req, instance, sql, sqlb.getQueryArguments());
        }
    }

    private void transferChunkedCSVEvents(RestRequest req, String instance, String sql, List<Object> sqlArgs)
            throws HttpException {
        RestStreams.stream(instance, sql, sqlArgs, new StreamToChunkedCSVEncoder(req, "events.csv") {

            @Override
            public String[] getCSVHeader() {
                return ArchiveHelper.getEventCSVHeader(getExtensionRegistry());
            }

            @Override
            public void processTuple(Tuple tuple, CsvWriter csvWriter) throws IOException {
                String[] record = ArchiveHelper.tupleToCSVEvent(tuple, getExtensionRegistry());
                csvWriter.writeRecord(record);
            }
        });
    }

    private void transferChunkedProtobufEvents(RestRequest req, String instance, String sql, List<Object> sqlArgs)
            throws HttpException {
        RestStreams.stream(instance, sql, sqlArgs, new StreamToChunkedProtobufEncoder<Event>(req, "events") {

            @Override
            public Event mapTuple(Tuple tuple) {
                return ArchiveHelper.tupleToEvent(tuple, getExtensionRegistry());
            }
        });
    }

    private GpbExtensionRegistry getExtensionRegistry() {
        if (gpbExtensionRegistry == null) {
            List<HttpServer> services = YamcsServer.getGlobalServices(HttpServer.class);
            gpbExtensionRegistry = services.get(0).getGpbExtensionRegistry();
        }
        return gpbExtensionRegistry;
    }
}
