package org.yamcs.http.api;

import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.audit.AuditLog;
import org.yamcs.management.LinkListener;
import org.yamcs.management.LinkManager;
import org.yamcs.protobuf.links.AbstractLinksApi;
import org.yamcs.protobuf.links.DisableLinkRequest;
import org.yamcs.protobuf.links.EditLinkRequest;
import org.yamcs.protobuf.links.EnableLinkRequest;
import org.yamcs.protobuf.links.GetLinkRequest;
import org.yamcs.protobuf.links.LinkEvent;
import org.yamcs.protobuf.links.LinkInfo;
import org.yamcs.protobuf.links.ListLinksRequest;
import org.yamcs.protobuf.links.ListLinksResponse;
import org.yamcs.protobuf.links.ResetLinkCountersRequest;
import org.yamcs.protobuf.links.RunActionRequest;
import org.yamcs.protobuf.links.SubscribeLinksRequest;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.tctm.LinkActionProvider;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;

public class LinksApi extends AbstractLinksApi<Context> {

    public LinksApi(AuditLog auditLog) {
        auditLog.addPrivilegeChecker(getClass().getSimpleName(), user -> {
            return user.hasSystemPrivilege(SystemPrivilege.ReadLinks);
        });
    }

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

        LinkInfo linkInfo = verifyLink(request.getInstance(), request.getLink());
        observer.complete(linkInfo);
    }

    @Override
    public void enableLink(Context ctx, EnableLinkRequest request, Observer<LinkInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlLinks);
        LinkInfo linkInfo = verifyLink(request.getInstance(), request.getLink());
        LinkManager lmgr = ManagementApi.verifyInstanceObj(request.getInstance()).getLinkManager();
        try {
            lmgr.enableLink(linkInfo.getName());
        } catch (IllegalArgumentException e) {
            throw new NotFoundException(e);
        }

        linkInfo = lmgr.getLinkInfo(request.getLink());
        observer.complete(linkInfo);
    }

    @Override
    public void disableLink(Context ctx, DisableLinkRequest request, Observer<LinkInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlLinks);
        LinkInfo linkInfo = verifyLink(request.getInstance(), request.getLink());
        LinkManager lmgr = ManagementApi.verifyInstanceObj(request.getInstance()).getLinkManager();
        try {
            lmgr.disableLink(linkInfo.getName());
        } catch (IllegalArgumentException e) {
            throw new NotFoundException(e);
        }

        linkInfo = lmgr.getLinkInfo(request.getLink());
        observer.complete(linkInfo);
    }

    @Override
    public void resetLinkCounters(Context ctx, ResetLinkCountersRequest request, Observer<LinkInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlLinks);
        LinkInfo linkInfo = verifyLink(request.getInstance(), request.getLink());
        LinkManager lmgr = ManagementApi.verifyInstanceObj(request.getInstance()).getLinkManager();
        try {
            lmgr.resetCounters(linkInfo.getName());
        } catch (IllegalArgumentException e) {
            throw new NotFoundException(e);
        }

        linkInfo = lmgr.getLinkInfo(request.getLink());
        observer.complete(linkInfo);
    }

    @Override
    public void updateLink(Context ctx, EditLinkRequest request, Observer<LinkInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlLinks);

        LinkInfo linkInfo = verifyLink(request.getInstance(), request.getLink());

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

        linkInfo = lmgr.getLinkInfo(request.getLink());
        observer.complete(linkInfo);
    }

    @Override
    public void runAction(Context ctx, RunActionRequest request, Observer<Struct> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlLinks);
        verifyLink(request.getInstance(), request.getLink());

        Gson gson = new Gson();
        JsonObject actionMessage = null;
        try {
            String json = JsonFormat.printer().print(request.getMessage());
            actionMessage = gson.fromJson(json, JsonElement.class).getAsJsonObject();
        } catch (InvalidProtocolBufferException e) {
            // Should not happen, it's already been converted from JSON through transcoding
            throw new InternalServerErrorException(e);
        }

        var linkManager = ManagementApi.verifyInstanceObj(request.getInstance()).getLinkManager();
        var link = linkManager.getLink(request.getLink());

        if (link instanceof LinkActionProvider) {
            var action = ((LinkActionProvider) link).getAction(request.getAction());
            if (action != null) {
                JsonElement response = action.execute(link, actionMessage);
                if (response == null) {
                    observer.next(Struct.getDefaultInstance());
                    return;
                } else {
                    var json = response.toString();
                    var responseb = Struct.newBuilder();
                    try {
                        JsonFormat.parser().merge(json, responseb);
                    } catch (InvalidProtocolBufferException e) {
                        throw new InternalServerErrorException(e);
                    }
                    observer.next(responseb.build());
                    return;
                }
            }
        }

        throw new BadRequestException("Unknown action '" + request.getAction() + "'");
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
