package org.yamcs.http.api.archive;

import org.yamcs.archive.CommandHistoryRecorder;
import org.yamcs.archive.GPBHelper;
import org.yamcs.http.HttpException;
import org.yamcs.http.api.RestHandler;
import org.yamcs.http.api.RestRequest;
import org.yamcs.http.api.RestStreams;
import org.yamcs.http.api.Route;
import org.yamcs.http.api.SqlBuilder;
import org.yamcs.http.api.RestRequest.IntervalResult;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Rest.ListCommandsResponse;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class ArchiveCommandRestHandler extends RestHandler {

    @Route(path = "/api/archive/:instance/commands")
    @Route(path = "/api/archive/:instance/commands/:name*")
    public void listCommands(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        if (ydb.getTable(CommandHistoryRecorder.TABLE_NAME) == null) {
            completeOK(req, ListCommandsResponse.newBuilder().build());
            return;
        }

        long pos = req.getQueryParameterAsLong("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);
        boolean desc = req.asksDescending(true);

        EventPageToken nextToken = null;
        if (req.hasQueryParameter("next")) {
            String next = req.getQueryParameter("next");
            nextToken = EventPageToken.decode(next);
        }

        SqlBuilder sqlb = new SqlBuilder(CommandHistoryRecorder.TABLE_NAME);
        IntervalResult ir = req.scanForInterval();
        if (ir.hasInterval()) {
            sqlb.where(ir.asSqlCondition("gentime"));
        }
        if (req.hasRouteParam("name")) {
            XtceDb mdb = XtceDbFactory.getInstance(instance);
            MetaCommand cmd = verifyCommand(req, mdb, req.getRouteParam("name"));
            checkObjectPrivileges(req, ObjectPrivilegeType.CommandHistory, cmd.getQualifiedName());
            sqlb.where("cmdName = ?", cmd.getQualifiedName());
        }

        if (req.hasQueryParameter("q")) {
            sqlb.where("cmdName like ?", "%" + req.getQueryParameter("q") + "%");
        }
        if (nextToken != null) {
            // TODO this currently ignores the origin column (also part of the key)
            // Requires string comparison in StreamSQL, and an even more complicated query condition...
            if (desc) {
                sqlb.where("(gentime < ? or (gentime = ? and seqNum < ?))",
                        nextToken.gentime, nextToken.gentime, nextToken.seqNum);
            } else {
                sqlb.where("(gentime > ? or (gentime = ? and seqNum > ?))",
                        nextToken.gentime, nextToken.gentime, nextToken.seqNum);
            }
        }

        sqlb.descend(desc);
        sqlb.limit(pos, limit + 1); // one more to detect hasMore

        ListCommandsResponse.Builder responseb = ListCommandsResponse.newBuilder();
        RestStreams.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

            CommandHistoryEntry last;
            int count;

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                if (++count <= limit) {
                    CommandHistoryEntry che = GPBHelper.tupleToCommandHistoryEntry(tuple);
                    responseb.addEntry(che);
                    last = che;
                }
            }

            @Override
            public void streamClosed(Stream stream) {
                if (count > limit) {
                    CommandId cmdId = last.getCommandId();
                    CommandPageToken token = new CommandPageToken(
                            cmdId.getGenerationTime(), cmdId.getOrigin(),
                            cmdId.getSequenceNumber());
                    responseb.setContinuationToken(token.encodeAsString());
                }
                completeOK(req, responseb.build());
            }
        });
    }
}
