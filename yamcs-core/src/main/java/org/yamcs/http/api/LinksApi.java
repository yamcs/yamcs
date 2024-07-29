package org.yamcs.http.api;

import java.util.concurrent.TimeUnit;

import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.actions.Action;
import org.yamcs.actions.ActionHelper;
import org.yamcs.api.Observer;
import org.yamcs.client.utils.WellKnownTypes;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.audit.AuditLog;
import org.yamcs.management.LinkManager;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.parameter.SystemParametersProducer;
import org.yamcs.parameter.SystemParametersService;
import org.yamcs.protobuf.links.AbstractLinksApi;
import org.yamcs.protobuf.links.DisableLinkRequest;
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
import org.yamcs.tctm.Link;
import org.yamcs.tctm.LinkActionProvider;
import org.yamcs.xtce.Parameter;

import com.google.protobuf.Struct;

public class LinksApi extends AbstractLinksApi<Context> {

    public LinksApi(AuditLog auditLog) {
        auditLog.addPrivilegeChecker(getClass().getSimpleName(), user -> {
            return user.hasSystemPrivilege(SystemPrivilege.ReadLinks);
        });
    }

    @Override
    public void listLinks(Context ctx, ListLinksRequest request, Observer<ListLinksResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadLinks);

        var responseb = ListLinksResponse.newBuilder();

        if (request.hasInstance()) {
            var linkManager = InstancesApi.verifyInstanceObj(request.getInstance()).getLinkManager();
            for (var link : linkManager.getLinks()) {
                responseb.addLinks(toLink(request.getInstance(), link));
            }
        } else {
            for (YamcsServerInstance ysi : YamcsServer.getInstances()) {
                var linkManager = ysi.getLinkManager();
                for (var link : linkManager.getLinks()) {
                    responseb.addLinks(toLink(request.getInstance(), link));
                }
            }
        }

        observer.complete(responseb.build());
    }

    @Override
    public void subscribeLinks(Context ctx, SubscribeLinksRequest request, Observer<LinkEvent> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadLinks);
        String instance = InstancesApi.verifyInstance(request.getInstance());
        YamcsServerInstance ysi = InstancesApi.verifyInstanceObj(instance);

        LinkManager linkManager = ysi.getLinkManager();

        var exec = YamcsServer.getServer().getThreadPoolExecutor();
        var future = exec.scheduleAtFixedRate(() -> {
            var b = LinkEvent.newBuilder();
            for (var link : linkManager.getLinks()) {
                b.addLinks(toLink(instance, link));
            }
            observer.next(b.build());
        }, 0, 1, TimeUnit.SECONDS);
        observer.setCancelHandler(() -> future.cancel(false));
    }

    @Override
    public void getLink(Context ctx, GetLinkRequest request, Observer<LinkInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadLinks);

        Link link = verifyLink(request.getInstance(), request.getLink());
        observer.complete(toLink(request.getInstance(), link));
    }

    @Override
    public void enableLink(Context ctx, EnableLinkRequest request, Observer<LinkInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlLinks);
        Link link = verifyLink(request.getInstance(), request.getLink());
        LinkManager lmgr = InstancesApi.verifyInstanceObj(request.getInstance()).getLinkManager();
        try {
            lmgr.enableLink(link.getName());
        } catch (IllegalArgumentException e) {
            throw new NotFoundException(e);
        }

        observer.complete(toLink(request.getInstance(), link));
    }

    @Override
    public void disableLink(Context ctx, DisableLinkRequest request, Observer<LinkInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlLinks);
        Link link = verifyLink(request.getInstance(), request.getLink());
        LinkManager lmgr = InstancesApi.verifyInstanceObj(request.getInstance()).getLinkManager();
        try {
            lmgr.disableLink(link.getName());
        } catch (IllegalArgumentException e) {
            throw new NotFoundException(e);
        }

        observer.complete(toLink(request.getInstance(), link));
    }

    @Override
    public void resetLinkCounters(Context ctx, ResetLinkCountersRequest request, Observer<LinkInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlLinks);
        Link link = verifyLink(request.getInstance(), request.getLink());
        LinkManager lmgr = InstancesApi.verifyInstanceObj(request.getInstance()).getLinkManager();
        try {
            lmgr.resetCounters(link.getName());
        } catch (IllegalArgumentException e) {
            throw new NotFoundException(e);
        }

        observer.complete(toLink(request.getInstance(), link));
    }

    @Override
    public void runAction(Context ctx, RunActionRequest request, Observer<Struct> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlLinks);
        verifyLink(request.getInstance(), request.getLink());

        var linkManager = InstancesApi.verifyInstanceObj(request.getInstance()).getLinkManager();
        var link = linkManager.getLink(request.getLink());

        Action<Link> action = null;
        if (link instanceof LinkActionProvider) {
            action = ((LinkActionProvider) link).getAction(request.getAction());
        }
        if (action == null) {
            throw new BadRequestException("Unknown action '" + request.getAction() + "'");
        }

        ActionHelper.runAction(link, action, request.getMessage(), observer);
    }

    public static Link verifyLink(String instance, String linkName) {
        YamcsServerInstance ysi = InstancesApi.verifyInstanceObj(instance);
        LinkManager lmgr = ysi.getLinkManager();
        Link link = lmgr.getLink(linkName);
        if (link == null) {
            throw new NotFoundException("No link named '" + linkName + "' within instance '" + instance + "'");
        }
        return link;
    }

    private static LinkInfo toLink(String yamcsInstance, Link link) {
        var b = LinkInfo.newBuilder()
                .setInstance(yamcsInstance)
                .setName(link.getName())
                .setDisabled(link.isDisabled())
                .setStatus(link.getLinkStatus().name())
                .setType(link.getClass().getName())
                .setDataInCount(link.getDataInCount())
                .setDataOutCount(link.getDataOutCount());
        var detailedStatus = link.getDetailedStatus();
        if (detailedStatus != null) {
            b.setDetailedStatus(detailedStatus);
        }
        var extra = link.getExtraInfo();
        if (extra != null) {
            b.setExtra(WellKnownTypes.toStruct(extra));
        }
        var parent = link.getParent();
        if (parent != null) {
            b.setParentName(parent.getName());
        }
        if (link instanceof LinkActionProvider) {
            b.clearActions();
            for (var action : ((LinkActionProvider) link).getActions()) {
                b.addActions(ActionHelper.toActionInfo(action));
            }
        }
        if (link instanceof SystemParametersProducer) {
            var systemParametersService = SystemParametersService.getInstance(yamcsInstance);
            if (systemParametersService != null) {
                var mdb = MdbFactory.getInstance(yamcsInstance);
                var spaceSystemName = systemParametersService.getNamespace() + "/links/" + link.getName();
                var spaceSystem = mdb.getSpaceSystem(spaceSystemName);
                if (spaceSystem != null) {
                    spaceSystem.getParameters(true).stream()
                            .map(Parameter::getQualifiedName)
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .forEach(b::addParameters);
                }
            }
        }

        return b.build();
    }
}
