package org.yamcs.web.rest.archive;

import org.yamcs.archive.GPBHelper;
import org.yamcs.cmdhistory.CommandHistoryRecorder;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Rest.ListCommandsResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.web.rest.NotFoundException;
import org.yamcs.web.rest.RestException;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequestHandler;
import org.yamcs.web.rest.RestResponse;
import org.yamcs.web.rest.RestStreamSubscriber;
import org.yamcs.web.rest.RestStreams;
import org.yamcs.web.rest.RestUtils;
import org.yamcs.web.rest.RestUtils.IntervalResult;
import org.yamcs.web.rest.SqlBuilder;
import org.yamcs.web.rest.mdb.MissionDatabaseHelper;
import org.yamcs.web.rest.mdb.MissionDatabaseHelper.MatchResult;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;

public class ArchiveCommandRequestHandler extends RestRequestHandler {

    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            req.assertGET();
            return listCommands(req, null);
        } else {
            MatchResult<MetaCommand> mr = MissionDatabaseHelper.matchCommandName(req, pathOffset);
            if (!mr.matches()) {
                throw new NotFoundException(req);
            }
            return listCommands(req, mr.getMatch().getQualifiedName());
        }
    }
    
    private RestResponse listCommands(RestRequest req, String commandName) throws RestException {
        long pos = req.getQueryParameterAsLong("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);
        
        SqlBuilder sqlb = new SqlBuilder(CommandHistoryRecorder.TABLE_NAME);
        IntervalResult ir = RestUtils.scanForInterval(req);
        if (ir.hasInterval()) {
            sqlb.where(ir.asSqlCondition("gentime"));
        }
        if (commandName != null) {
            sqlb.where("cmdName = '" + commandName + "'");
        }
        sqlb.descend(RestUtils.asksDescending(req, true));
        
        ListCommandsResponse.Builder responseb = ListCommandsResponse.newBuilder();
        RestStreams.streamAndWait(req, sqlb.toString(), new RestStreamSubscriber(pos, limit) {

            @Override
            public void processTuple(Stream stream, Tuple tuple) {
                CommandHistoryEntry che = GPBHelper.tupleToCommandHistoryEntry(tuple);
                responseb.addEntry(che);
            }
        });
        return new RestResponse(req, responseb.build(), SchemaRest.ListCommandsResponse.WRITE);
    }
}
