package org.yamcs.web.rest.archive;

import org.yamcs.protobuf.Archive.StreamInfo;
import org.yamcs.protobuf.Rest.ListStreamsResponse;
import org.yamcs.protobuf.SchemaArchive;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.web.HttpException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.Route;
import org.yamcs.yarch.AbstractStream;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;

public class ArchiveStreamRestHandler extends RestHandler {

    @Route(path = "/api/archive/:instance/streams", method = "GET")
    public void listStreams(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        YarchDatabase ydb = YarchDatabase.getInstance(instance);
        
        ListStreamsResponse.Builder responseb = ListStreamsResponse.newBuilder();
        for (AbstractStream stream : ydb.getStreams()) {
            responseb.addStream(ArchiveHelper.toStreamInfo(stream));
        }
        completeOK(req, responseb.build(), SchemaRest.ListStreamsResponse.WRITE);
    }
    
    @Route(path = "/api/archive/:instance/streams/:name", method = "GET")
    public void getStream(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        YarchDatabase ydb = YarchDatabase.getInstance(instance);
        Stream stream = verifyStream(req, ydb, req.getRouteParam("name"));
        
        StreamInfo response = ArchiveHelper.toStreamInfo(stream);
        completeOK(req, response, SchemaArchive.StreamInfo.WRITE);
    }    
}
