package org.yamcs.web.rest.archive;

import org.yamcs.archive.CommandHistoryRecorder;
import org.yamcs.archive.GPBHelper;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Rest.ListCommandsResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.web.HttpException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequest.IntervalResult;
import org.yamcs.web.rest.RestStreamSubscriber;
import org.yamcs.web.rest.RestStreams;
import org.yamcs.web.rest.Route;
import org.yamcs.web.rest.SqlBuilder;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;

public class ArchiveCommandRestHandler extends RestHandler {
    
    @Route(path = "/api/archive/:instance/commands/:name*")
    public void listCommands(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        
        long pos = req.getQueryParameterAsLong("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);
        
        SqlBuilder sqlb = new SqlBuilder(CommandHistoryRecorder.TABLE_NAME);
        IntervalResult ir = req.scanForInterval();
        if (ir.hasInterval()) {
            sqlb.where(ir.asSqlCondition("gentime"));
        }
        if (req.hasRouteParam("name")) {
            XtceDb mdb = XtceDbFactory.getInstance(instance);
            MetaCommand cmd = verifyCommand(req, mdb, req.getRouteParam("name"));
            sqlb.where("cmdName = '" + cmd.getQualifiedName() + "'");
        }
        sqlb.descend(req.asksDescending(true));
        
        ListCommandsResponse.Builder responseb = ListCommandsResponse.newBuilder();
        RestStreams.stream(instance, sqlb.toString(), new RestStreamSubscriber(pos, limit) {

            @Override
            public void processTuple(Stream stream, Tuple tuple) {
                CommandHistoryEntry che = GPBHelper.tupleToCommandHistoryEntry(tuple);
                responseb.addEntry(che);
            }
            @Override
            public void streamClosed(Stream stream) {
                completeOK(req, responseb.build(), SchemaRest.ListCommandsResponse.WRITE);
            }
        });
        
    }
}
