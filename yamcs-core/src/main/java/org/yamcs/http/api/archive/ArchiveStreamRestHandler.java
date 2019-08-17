package org.yamcs.http.api.archive;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.http.HttpException;
import org.yamcs.http.api.RestHandler;
import org.yamcs.http.api.RestRequest;
import org.yamcs.http.api.Route;
import org.yamcs.protobuf.Archive.ListStreamsResponse;
import org.yamcs.protobuf.Archive.StreamInfo;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class ArchiveStreamRestHandler extends RestHandler {

    @Route(rpc = "yamcs.protobuf.archive.StreamArchive.ListStreams")
    public void listStreams(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);

        ListStreamsResponse.Builder responseb = ListStreamsResponse.newBuilder();
        List<Stream> streams = new ArrayList<>(ydb.getStreams());
        streams.sort((s1, s2) -> s1.getName().compareToIgnoreCase(s2.getName()));
        for (Stream stream : streams) {
            if (!hasObjectPrivilege(req, ObjectPrivilegeType.Stream, stream.getName())) {
                continue;
            }
            responseb.addStream(ArchiveHelper.toStreamInfo(stream));
        }
        completeOK(req, responseb.build());
    }

    @Route(rpc = "yamcs.protobuf.archive.StreamArchive.GetStream")
    public void getStream(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        Stream stream = verifyStream(req, ydb, req.getRouteParam("name"));

        StreamInfo response = ArchiveHelper.toStreamInfo(stream);
        completeOK(req, response);
    }
}
