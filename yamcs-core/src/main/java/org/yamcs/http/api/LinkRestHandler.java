package org.yamcs.http.api;

import java.util.List;

import org.yamcs.http.BadRequestException;
import org.yamcs.http.HttpException;
import org.yamcs.http.NotFoundException;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Rest.EditLinkRequest;
import org.yamcs.protobuf.Rest.ListLinkInfoResponse;
import org.yamcs.protobuf.YamcsManagement.LinkInfo;
import org.yamcs.security.SystemPrivilege;

/**
 * Gives information on data links
 */
public class LinkRestHandler extends RestHandler {

    @Route(path = "/api/links/:instance?", method = "GET")
    public void listLinks(RestRequest req) throws HttpException {
        String instance = req.getRouteParam("instance");
        if (instance != null) {
            verifyInstance(req, instance);
        }

        List<LinkInfo> links = ManagementService.getInstance().getLinkInfo();
        ListLinkInfoResponse.Builder responseb = ListLinkInfoResponse.newBuilder();

        for (LinkInfo link : links) {
            if (instance == null || instance.equals(link.getInstance())) {
                responseb.addLink(link);
            }
        }
        completeOK(req, responseb.build());
    }

    @Route(path = "/api/links/:instance/:name", method = "GET")
    @Route(path = "/api/links/:instance/link/:name", method = "GET")
    public void getLink(RestRequest req) throws HttpException {
        LinkInfo linkInfo = verifyLink(req, req.getRouteParam("instance"), req.getRouteParam("name"));
        completeOK(req, linkInfo);
    }

    @Route(path = "/api/links/:instance/:name", method = { "PATCH", "PUT", "POST" })
    @Route(path = "/api/links/:instance/link/:name", method = { "PATCH", "PUT", "POST" })
    public void editLink(RestRequest req) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.ControlLinks);

        LinkInfo linkInfo = verifyLink(req, req.getRouteParam("instance"), req.getRouteParam("name"));

        EditLinkRequest request = req.bodyAsMessage(EditLinkRequest.newBuilder()).build();
        String state = null;
        if (request.hasState()) {
            state = request.getState();
        }
        if (req.hasQueryParameter("state")) {
            state = req.getQueryParameter("state");
        }

        ManagementService mservice = ManagementService.getInstance();
        if (state != null) {
            switch (state.toLowerCase()) {
            case "enabled":
                try {
                    mservice.enableLink(linkInfo.getInstance(), linkInfo.getName());
                } catch (IllegalArgumentException e) {
                    throw new NotFoundException(e);
                }
                break;
            case "disabled":
                try {
                    mservice.disableLink(linkInfo.getInstance(), linkInfo.getName());
                } catch (IllegalArgumentException e) {
                    throw new NotFoundException(e);
                }
                break;
            default:
                throw new BadRequestException("Unsupported link state '" + state + "'");
            }
        }

        if (request.hasResetCounters() && request.getResetCounters()) {
            try {
                mservice.resetCounters(linkInfo.getInstance(), linkInfo.getName());
            } catch (IllegalArgumentException e) {
                throw new NotFoundException(e);
            }
        }

        completeOK(req);
    }
}
