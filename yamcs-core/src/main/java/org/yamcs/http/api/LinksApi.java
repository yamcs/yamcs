package org.yamcs.http.api;

import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.NotFoundException;
import org.yamcs.management.LinkListener;
import org.yamcs.management.LinkManager;
import org.yamcs.protobuf.links.AbstractLinksApi;
import org.yamcs.protobuf.links.EditLinkRequest;
import org.yamcs.protobuf.links.GetLinkRequest;
import org.yamcs.protobuf.links.LinkEvent;
import org.yamcs.protobuf.links.LinkInfo;
import org.yamcs.protobuf.links.ListLinksRequest;
import org.yamcs.protobuf.links.ListLinksResponse;
import org.yamcs.protobuf.links.SubscribeLinksRequest;
import org.yamcs.security.SystemPrivilege;

public class LinksApi extends AbstractLinksApi<Context> {

    @Override
    public void listLinks(Context ctx, ListLinksRequest request, Observer<ListLinksResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadLinks);

        ListLinksResponse.Builder responseb = ListLinksResponse.newBuilder();

        if (request.hasInstance()) {
            LinkManager lmgr = ManagementApi.verifyInstanceObj(request.getInstance()).getLinkManager();
            for (LinkInfo link : lmgr.getLinkInfo()) {
                responseb.addLinks(link);
            }
        } else {
            for (YamcsServerInstance ysi : YamcsServer.getInstances()) {
                LinkManager lmgr = ysi.getLinkManager();
                for (LinkInfo link : lmgr.getLinkInfo()) {
                    responseb.addLinks(link);
                }
            }
        }

        observer.complete(responseb.build());
    }

    @Override
    public void subscribeLinks(Context ctx, SubscribeLinksRequest request, Observer<LinkEvent> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadLinks);
        String instance = ManagementApi.verifyInstance(request.getInstance());
        YamcsServerInstance ysi = ManagementApi.verifyInstanceObj(instance);

        LinkManager linkManager = ysi.getLinkManager();
        for (LinkInfo linkInfo : linkManager.getLinkInfo()) {
            if (instance.equals(linkInfo.getInstance())) {
                observer.next(LinkEvent.newBuilder()
                        .setType(LinkEvent.Type.REGISTERED)
                        .setLinkInfo(linkInfo)
                        .build());
            }
        }

        LinkListener listener = new LinkListener() {
            @Override
            public void linkRegistered(LinkInfo linkInfo) {
                if (instance.equals(linkInfo.getInstance())) {
                    observer.next(LinkEvent.newBuilder()
                            .setType(LinkEvent.Type.REGISTERED)
                            .setLinkInfo(linkInfo)
                            .build());
                }
            }

            @Override
            public void linkUnregistered(LinkInfo linkInfo) {
                // TODO Currently not handled correctly by ManagementService
            }

            @Override
            public void linkChanged(LinkInfo linkInfo) {
                if (instance.equals(linkInfo.getInstance())) {
                    observer.next(LinkEvent.newBuilder()
                            .setType(LinkEvent.Type.UPDATED)
                            .setLinkInfo(linkInfo)
                            .build());
                }
            }
        };

        observer.setCancelHandler(() -> linkManager.removeLinkListener(listener));
        linkManager.addLinkListener(listener);
    }

    @Override
    public void getLink(Context ctx, GetLinkRequest request, Observer<LinkInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadLinks);

        LinkInfo linkInfo = verifyLink(request.getInstance(), request.getName());
        observer.complete(linkInfo);
    }

    @Override
    public void updateLink(Context ctx, EditLinkRequest request, Observer<LinkInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlLinks);

        LinkInfo linkInfo = verifyLink(request.getInstance(), request.getName());

        String state = null;
        if (request.hasState()) {
            state = request.getState();
        }

        LinkManager lmgr = ManagementApi.verifyInstanceObj(request.getInstance()).getLinkManager();
        if (state != null) {
            switch (state.toLowerCase()) {
            case "enabled":
                try {
                    lmgr.enableLink(linkInfo.getName());
                } catch (IllegalArgumentException e) {
                    throw new NotFoundException(e);
                }
                break;
            case "disabled":
                try {
                    lmgr.disableLink(linkInfo.getName());
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
                lmgr.resetCounters(linkInfo.getName());
            } catch (IllegalArgumentException e) {
                throw new NotFoundException(e);
            }
        }

        linkInfo = lmgr.getLinkInfo(request.getName());
        observer.complete(linkInfo);
    }

    public static LinkInfo verifyLink(String instance, String linkName) {
        YamcsServerInstance ysi = ManagementApi.verifyInstanceObj(instance);
        LinkManager lmgr = ysi.getLinkManager();
        LinkInfo linkInfo = lmgr.getLinkInfo(linkName);
        if (linkInfo == null) {
            throw new NotFoundException("No link named '" + linkName + "' within instance '" + instance + "'");
        }
        return linkInfo;
    }
}
