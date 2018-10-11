package org.yamcs.web.rest.archive;

import org.yamcs.protobuf.Archive.ExecuteSqlRequest;
import org.yamcs.protobuf.Archive.ExecuteSqlResponse;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.Route;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;

public class ArchiveSqlRestHandler extends RestHandler {

    @Route(path = "/api/archive/:instance/sql", method = "POST")
    public void executeSql(RestRequest req) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.ControlArchiving);

        String instance = verifyInstance(req, req.getRouteParam("instance"));
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);

        ExecuteSqlRequest msg = req.bodyAsMessage(ExecuteSqlRequest.newBuilder()).build();
        if (!msg.hasStatement()) {
            completeOK(req);
        }

        ExecuteSqlResponse.Builder responseb = ExecuteSqlResponse.newBuilder();
        try {
            StreamSqlResult result = ydb.execute(msg.getStatement());
            String stringOutput = result.toString();
            if (stringOutput != null) {
                responseb.setResult(stringOutput);
            }
            completeOK(req, responseb.build());
        } catch (ParseException e) {
            throw new BadRequestException(e);
        } catch (StreamSqlException e) {
            throw new InternalServerErrorException(e);
        }
    }
}
