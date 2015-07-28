package org.yamcs.web.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Handles everything under /api. In the future could also be used to handle multiple versions,
 * if ever needed. (e.g. /api/v2).
 */
public class ApiRequestHandler extends AbstractRestRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiRequestHandler.class);
    public static final String ARCHIVE_PATH = "archive";
    public static final String MDB_PATH = "mdb";
    public static final String COMMANDING_PATH = "commanding";
    public static final String PARAMETER_PATH = "parameter";
    public static final String MANAGEMENT_PATH = "management";
    public static final String PROCESSOR_PATH = "processor";

    static ArchiveRequestHandler archiveRequestHandler=new ArchiveRequestHandler();
    static MdbRequestHandler mdbRequestHandler=new MdbRequestHandler();
    static CommandingRequestHandler commandingRequestHandler=new CommandingRequestHandler();
    static ParameterRequestHandler parameterRequestHandler=new ParameterRequestHandler();
    static ManagementRequestHandler managementRequestHandler=new ManagementRequestHandler();
    static ProcessorRequestHandler processorRequestHandler=new ProcessorRequestHandler();
    
    @Override
    public RestResponse handleRequest(RestRequest ctx) {
        String[] path = ctx.remainingUri.split("/", 2);
        if (path.length == 0) {
            sendError(ctx.getChannelHandlerContext(), HttpResponseStatus.NOT_FOUND);
        } else {
            try {
                // Chop off first path segment (incl. any '/') while keeping querystring in place
                String choppedUri = ctx.remainingUri.substring(path.length > 1 ? path[0].length() + 1 : path[0].length());
                ctx.remainingUri = choppedUri;
                if(path[0].startsWith(ARCHIVE_PATH)) {
                    sendResponse(archiveRequestHandler.handleRequest(ctx));
                } else if(path[0].startsWith(MDB_PATH)) {
                    sendResponse(mdbRequestHandler.handleRequest(ctx));
                } else if(path[0].startsWith(COMMANDING_PATH)) {
                    sendResponse(commandingRequestHandler.handleRequest(ctx));
                } else if(path[0].startsWith(PARAMETER_PATH)) {
                    sendResponse(parameterRequestHandler.handleRequest(ctx));
                } else if(path[0].startsWith(MANAGEMENT_PATH)) {
                    sendResponse(managementRequestHandler.handleRequest(ctx));
                } else if(path[0].startsWith(PROCESSOR_PATH)) {
                    sendResponse(processorRequestHandler.handleRequest(ctx));
                } else {
                    log.warn("Unknown request received: '{}'", path[0]);
                    sendError(ctx.getChannelHandlerContext(), HttpResponseStatus.NOT_FOUND);
                }
            } catch (InternalServerErrorException e) {
                log.error("Reporting internal server error to rest client", e);
                sendError(ctx, e.getHttpResponseStatus(), e);
            } catch (RestException e) {
                log.warn("Sending nominal exception back to rest client", e);
                sendError(ctx, e.getHttpResponseStatus(), e);
            } catch (Exception e) {
                log.error("Unexpected error " + e, e);
                sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, e);
            }
        }
        return null;
    }
}
