package org.yamcs.web.rest.archive;

import org.yamcs.protobuf.Archive.StreamInfo;
import org.yamcs.protobuf.Rest.ListStreamsResponse;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.web.HttpException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.Route;
import org.yamcs.yarch.AbstractStream;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class ArchiveStreamRestHandler extends RestHandler {

    @Route(path = "/api/archive/:instance/streams", method = "GET")
    public void listStreams(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);

        ListStreamsResponse.Builder responseb = ListStreamsResponse.newBuilder();
        for (AbstractStream stream : ydb.getStreams()) {
            if (!hasObjectPrivilege(req, ObjectPrivilegeType.Stream, stream.getName())) {
                continue;
            }
            responseb.addStream(ArchiveHelper.toStreamInfo(stream));
        }
        completeOK(req, responseb.build());
    }

    @Route(path = "/api/archive/:instance/streams/:name", method = "GET")
    public void getStream(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        Stream stream = verifyStream(req, ydb, req.getRouteParam("name"));

        StreamInfo response = ArchiveHelper.toStreamInfo(stream);
        completeOK(req, response);
    }
}
