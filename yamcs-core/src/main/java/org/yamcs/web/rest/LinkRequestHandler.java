package org.yamcs.web.rest;

import java.util.List;

import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Rest.EditLinkRequest;
import org.yamcs.protobuf.Rest.ListLinkInfoResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcsManagement;
import org.yamcs.protobuf.YamcsManagement.LinkInfo;

/**
 * Gives information on data links
 */
public class LinkRequestHandler extends RestRequestHandler {
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (req.hasPathSegment(pathOffset)) {
            String instance = req.getPathSegment(pathOffset);
            if (!YamcsServer.hasInstance(instance)) {
                throw new NotFoundException(req, "No instance named '" + instance + "'");
            }
            
            pathOffset++;
            if (req.hasPathSegment(pathOffset)) {
                String linkName = req.getPathSegment(pathOffset);
                LinkInfo linkInfo = ManagementService.getInstance().getLinkInfo(instance, linkName);
                if (linkInfo == null) {
                    throw new NotFoundException(req, "No link named '" + linkName + "' within instance '" + instance + "'");
                } else {
                    if (req.isGET()) {
                        return getLink(req, linkInfo);
                    } else if (req.isPATCH() || req.isPOST() || req.isPUT()) {
                        return editLink(req, linkInfo);
                    } else {
                        throw new MethodNotAllowedException(req);
                    }
                }
            } else {
                req.assertGET();
                return listLinks(req, instance);
            }
        } else {
            req.assertGET();
            return listLinks(req, null);
        }
    }
    
    private RestResponse getLink(RestRequest req, LinkInfo linkInfo) throws RestException {
        return new RestResponse(req, linkInfo, SchemaYamcsManagement.LinkInfo.WRITE);
    }
    
    private RestResponse listLinks(RestRequest req, String instance) throws RestException {
        List<LinkInfo> links = ManagementService.getInstance().getLinkInfo();
        ListLinkInfoResponse.Builder responseb = ListLinkInfoResponse.newBuilder();
        for (LinkInfo link : links) {
            if (instance == null || instance.equals(link.getInstance())) {
                responseb.addLink(link);
            }
        }
        return new RestResponse(req, responseb.build(), SchemaRest.ListLinkInfoResponse.WRITE);
    }
    
    private RestResponse editLink(RestRequest req, LinkInfo linkInfo) throws RestException {
        EditLinkRequest request = req.bodyAsMessage(SchemaRest.EditLinkRequest.MERGE).build();
        String state = null;
        if (request.hasState()) state = request.getState();
        if (req.hasQueryParameter("state")) state = req.getQueryParameter("state");
        
        if (state != null) {
            ManagementService mservice = ManagementService.getInstance();
            switch (state.toLowerCase()) {
            case "enabled":
                try {
                    mservice.enableLink(linkInfo.getInstance(), linkInfo.getName());
                    return new RestResponse(req);
                } catch (YamcsException e) {
                    throw new InternalServerErrorException(e);
                }
            case "disabled":
                try {
                    mservice.disableLink(linkInfo.getInstance(), linkInfo.getName());
                    return new RestResponse(req);                    
                } catch (YamcsException e) {
                    throw new InternalServerErrorException(e);
                }
            default:
                throw new BadRequestException("Unsupported link state '" + state + "'");
            }
        } else {
            return new RestResponse(req);
        }
    }
}
