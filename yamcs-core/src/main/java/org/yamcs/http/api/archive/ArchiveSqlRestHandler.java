package org.yamcs.http.api.archive;

import org.yamcs.http.BadRequestException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.api.RestHandler;
import org.yamcs.http.api.RestRequest;
import org.yamcs.http.api.Route;
import org.yamcs.protobuf.Archive.ExecuteSqlRequest;
import org.yamcs.protobuf.Archive.ExecuteSqlResponse;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;

public class ArchiveSqlRestHandler extends RestHandler {

    @Route(rpc = "StreamArchive.ExecuteSql")
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
