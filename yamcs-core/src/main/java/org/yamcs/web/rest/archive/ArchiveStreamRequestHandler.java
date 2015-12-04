package org.yamcs.web.rest.archive;

import org.yamcs.protobuf.Archive.StreamInfo;
import org.yamcs.protobuf.Rest.ListStreamsResponse;
import org.yamcs.protobuf.SchemaArchive;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.web.rest.NotFoundException;
import org.yamcs.web.rest.RestException;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequestHandler;
import org.yamcs.web.rest.RestResponse;
import org.yamcs.yarch.AbstractStream;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;

public class ArchiveStreamRequestHandler extends RestRequestHandler {

    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        String instance = req.getFromContext(RestRequest.CTX_INSTANCE);
        YarchDatabase ydb = YarchDatabase.getInstance(instance);
        if (!req.hasPathSegment(pathOffset)) {
            req.assertGET();
            return listStreams(req, ydb);
        } else {
            String streamName = req.getPathSegment(pathOffset);
            Stream stream = ydb.getStream(streamName);
            if (stream == null) {
                throw new NotFoundException(req, "No stream named '" + streamName + "'");
            } else {
                return handleStreamRequest(req, pathOffset + 1, stream);
            }
        }
    }
    
    private RestResponse handleStreamRequest(RestRequest req, int pathOffset, Stream stream) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            req.assertGET();
            return getStream(req, stream);
        } else {
            String resource = req.getPathSegment(pathOffset + 1);
            throw new NotFoundException(req, "No resource '" + resource + "' for stream '" + stream.getName() +  "'");
        }
    } 
    
    private RestResponse listStreams(RestRequest req, YarchDatabase ydb) throws RestException {
        ListStreamsResponse.Builder responseb = ListStreamsResponse.newBuilder();
        for (AbstractStream stream : ydb.getStreams()) {
            responseb.addStream(ArchiveHelper.toStreamInfo(stream));
        }
        return new RestResponse(req, responseb.build(), SchemaRest.ListStreamsResponse.WRITE);
    }
    
    private RestResponse getStream(RestRequest req, Stream stream) throws RestException {
        StreamInfo response = ArchiveHelper.toStreamInfo(stream);
        return new RestResponse(req, response, SchemaArchive.StreamInfo.WRITE);
    }    
}
