package org.yamcs.http.api.archive;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.yamcs.archive.CommandHistoryRecorder;
import org.yamcs.archive.EventRecorder;
import org.yamcs.archive.GPBHelper;
import org.yamcs.archive.XtceTmRecorder;
import org.yamcs.http.HttpException;
import org.yamcs.http.HttpServer;
import org.yamcs.http.ProtobufRegistry;
import org.yamcs.http.api.RestHandler;
import org.yamcs.http.api.RestRequest;
import org.yamcs.http.api.RestRequest.IntervalResult;
import org.yamcs.http.api.RestStreams;
import org.yamcs.http.api.Route;
import org.yamcs.http.api.SqlBuilder;
import org.yamcs.http.api.StreamArchiveApi;
import org.yamcs.http.api.StreamToChunkedProtobufEncoder;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.yarch.Tuple;

/*
 * NOTE:
 * 
 * The value of these methods should be reconsidered. We already have a route that allows paging.
 * If we still need these, they should be migrated to StreamArchiveApi with server streaming
 * (see :streamParameterValues route)
 */

/**
 * Serves archived data through a web api.
 *
 * <p>
 * Archive requests use chunked encoding with an unspecified content length, which enables us to send large dumps
 * without needing to determine a content length on the server.
 */
public class ArchiveDownloadRestHandler extends RestHandler {

    private ProtobufRegistry protobufRegistry;

    @Route(path = "/api/archive/{instance}/downloads/packets", method = "GET")
    public void downloadPackets(RestRequest req) throws HttpException {
        String instance = verifyInstance(req.getRouteParam("instance"));

        Set<String> nameSet = new HashSet<>();
        for (String names : req.getQueryParameterList("name", Collections.emptyList())) {
            for (String name : names.split(",")) {
                nameSet.add(name.trim());
            }
        }

        checkObjectPrivileges(req.getUser(), ObjectPrivilegeType.ReadPacket, nameSet);

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

        RestStreams.stream(instance, sql, sqlb.getQueryArguments(),
                new StreamToChunkedProtobufEncoder<TmPacketData>(req, filename) {
                    @Override
                    public TmPacketData mapTuple(Tuple tuple) {
                        return GPBHelper.tupleToTmPacketData(tuple);
                    }
                });
    }

    @Route(path = "/api/archive/{instance}/downloads/commands", method = "GET")
    public void downloadCommands(RestRequest req) throws HttpException {
        String instance = verifyInstance(req.getRouteParam("instance"));

        Set<String> nameSet = new HashSet<>();
        for (String names : req.getQueryParameterList("name", Collections.emptyList())) {
            for (String name : names.split(",")) {
                nameSet.add(name.trim());
            }
        }

        checkObjectPrivileges(req.getUser(), ObjectPrivilegeType.CommandHistory, nameSet);

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

    @Route(path = "/api/archive/{instance}/downloads/events", method = "GET")
    public void downloadEvents(RestRequest req) throws HttpException {
        String instance = verifyInstance(req.getRouteParam("instance"));
        StreamArchiveApi.verifyEventArchiveSupport(instance);
        checkSystemPrivilege(req.getUser(), SystemPrivilege.ReadEvents);

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

        transferChunkedProtobufEvents(req, instance, sql, sqlb.getQueryArguments());
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

    private ProtobufRegistry getExtensionRegistry() {
        if (protobufRegistry == null) {
            List<HttpServer> services = yamcsServer.getGlobalServices(HttpServer.class);
            protobufRegistry = services.get(0).getProtobufRegistry();
        }
        return protobufRegistry;
    }
}
