package org.yamcs.web.rest;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles everything under /api. In the future could also be used to handle multiple versions,
 * if every needed. (e.g. /api/v2).
 */
public class ApiRequestHandler extends AbstractRestRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiRequestHandler.class);
    public static final String ARCHIVE_PATH = "archive";
    public static final String MDB_PATH = "mdb";
    public static final String COMMANDING_PATH = "commanding";
    public static final String PARAMETER_PATH = "parameter";

    static ArchiveRequestHandler archiveRequestHandler=new ArchiveRequestHandler();
    static MdbRequestHandler mdbRequestHandler=new MdbRequestHandler();
    static CommandingRequestHandler commandingRequestHandler=new CommandingRequestHandler();
    static ParameterRequestHandler parameterRequestHandler=new ParameterRequestHandler();
    
    @Override
    public void handleRequest(ChannelHandlerContext ctx, FullHttpRequest req, String yamcsInstance, String remainingUri) throws RestException {
        String[] path = remainingUri.split("/", 2);
        if (path.length == 0) {
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
        } else {
            try {
                if(ARCHIVE_PATH.equals(path[0])) {
                    archiveRequestHandler.handleRequest(ctx, req, yamcsInstance, path.length>1? path[1] : null);
                } else if(MDB_PATH.equals(path[0])) {
                    mdbRequestHandler.handleRequest(ctx, req, yamcsInstance, path.length>1? path[1] : null);
                } else if(COMMANDING_PATH.equals(path[0])) {
                    commandingRequestHandler.handleRequest(ctx, req, yamcsInstance, path.length>1? path[1] : null);
                } else if(PARAMETER_PATH.equals(path[0])) {
                    parameterRequestHandler.handleRequest(ctx, req, yamcsInstance, path.length>1? path[1] : null);
                } else {
                    log.warn("Unknown request received {}", path[0]);
                    sendError(ctx, HttpResponseStatus.NOT_FOUND);
                }
            } catch (InternalServerErrorException e) {
                log.error("Reporting internal server error to rest client", e);
                sendError(e, req, new QueryStringDecoder(remainingUri), ctx, e.getHttpResponseStatus());
            } catch (RestException e) {
                log.trace("Sending nominal exception back to rest client", e);
                sendError(e, req, new QueryStringDecoder(remainingUri), ctx, e.getHttpResponseStatus());
            } catch (Exception e) {
                log.error("Unexpected error " + e, e);
                sendError(e, req, new QueryStringDecoder(remainingUri), ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }
}
