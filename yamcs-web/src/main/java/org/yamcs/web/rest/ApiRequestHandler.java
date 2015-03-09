package org.yamcs.web.rest;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
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

    static ArchiveRequestHandler archiveRequestHandler=new ArchiveRequestHandler();
    static MdbRequestHandler mdbRequestHandler=new MdbRequestHandler();
    static CommandingRequestHandler commandingRequestHandler=new CommandingRequestHandler();

    @Override
    public void handleRequest(ChannelHandlerContext ctx, HttpRequest req, MessageEvent evt, String yamcsInstance, String remainingUri) throws RestException {
        String[] path = remainingUri.split("/", 2);
        if (path.length == 0) {
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
        } else {
            try {
                if(ARCHIVE_PATH.equals(path[0])) {
                    archiveRequestHandler.handleRequest(ctx, req, evt, yamcsInstance, path.length>1? path[1] : null);
                } else if(MDB_PATH.equals(path[0])) {
                    mdbRequestHandler.handleRequest(ctx, req, evt, yamcsInstance, path.length>1? path[1] : null);
                } else if(COMMANDING_PATH.equals(path[0])) {
                    commandingRequestHandler.handleRequest(ctx, req, evt, yamcsInstance, path.length>1? path[1] : null);
                }
            } catch (BadRequestException e) {
                sendError(e, req, new QueryStringDecoder(remainingUri), ctx, HttpResponseStatus.BAD_REQUEST);
            } catch (RestException e) {
                sendError(e, req, new QueryStringDecoder(remainingUri), ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            } catch (Exception e) {
                log.error("Unexpected error ", e);
                sendError(e, req, new QueryStringDecoder(remainingUri), ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }
}
