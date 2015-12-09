package org.yamcs.web.rest.archive;

import java.util.LinkedHashMap;
import java.util.Map;

import org.yamcs.YamcsServer;
import org.yamcs.web.HttpException;
import org.yamcs.web.NotFoundException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.mdb.MDBRestHandler;
import org.yamcs.xtceproc.XtceDbFactory;

import io.netty.channel.ChannelFuture;

/**
 * Serves archived data through a web api. The default built-in tables are given
 * specialised APIs. For mission-specific tables the generic table API could be
 * used.
 */
public class ArchiveRestHandler extends RestHandler {

    private Map<String, RestHandler> subHandlers = new LinkedHashMap<>();
    
    public ArchiveRestHandler() {
        subHandlers.put("alarms", new ArchiveAlarmRestHandler());
        subHandlers.put("commands", new ArchiveCommandRestHandler());
        subHandlers.put("downloads", new ArchiveDownloadRestHandler());
        subHandlers.put("events", new ArchiveEventRestHandler());
        subHandlers.put("indexes", new ArchiveIndexRestHandler());
        subHandlers.put("packets", new ArchivePacketRestHandler());
        subHandlers.put("parameters", new ArchiveParameterRestHandler());
        subHandlers.put("streams", new ArchiveStreamRestHandler());
        subHandlers.put("tables", new ArchiveTableRestHandler());
        subHandlers.put("tags", new ArchiveTagRestHandler());
    }
    
    @Override
    public ChannelFuture handleRequest(RestRequest req, int pathOffset) throws HttpException {
        if (!req.hasPathSegment(pathOffset)) {
            // TODO list archives with links or something
            throw new NotFoundException(req);
        }
        
        String instance = req.getPathSegment(pathOffset);
        if (!YamcsServer.hasInstance(instance)) {
            throw new NotFoundException(req, "No instance '" + instance + "'");
        }
        req.addToContext(RestRequest.CTX_INSTANCE, instance);
        req.addToContext(MDBRestHandler.CTX_MDB, XtceDbFactory.getInstance(instance));
        
        pathOffset++;
        if (!req.hasPathSegment(pathOffset)) {
            throw new NotFoundException(req);
        } else {
            String segment = req.getPathSegment(pathOffset);
            RestHandler handler = subHandlers.get(segment);
            if (handler != null) {
                return handler.handleRequest(req, pathOffset + 1);
            } else {
                throw new NotFoundException(req);
            }
        }
    }
}
