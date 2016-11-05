package org.yamcs.web.rest;

import java.util.List;

import org.yamcs.YamcsServer;
import org.yamcs.protobuf.Rest.EditServiceRequest;
import org.yamcs.protobuf.Rest.ListServiceInfoResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.YamcsManagement.ServiceInfo;
import org.yamcs.security.Privilege;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.ForbiddenException;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.NotFoundException;

import com.google.common.util.concurrent.Service;

/**
 * Gives information on services (instance specific or server wide)
 */
public class ServiceRestHandler extends RestHandler {
    static String GLOBAL_INSTANCE = "_global";


    @Route(path="/api/services/:instance?", method="GET")
    public void listServices(RestRequest req) throws HttpException {        
        checkPrivileges(req);
        String instance = req.getRouteParam("instance");
        if(instance==null) throw new BadRequestException("No instance specified");
        boolean global = false;
        if(GLOBAL_INSTANCE.equals(instance)) {
            global = true;
        } else {
            verifyInstance(req, instance);
        }


        List<ServiceInfo> slist;

        if(global) {
            slist = YamcsServer.getGlobalServices();
        } else {
            YamcsServer ys = YamcsServer.getInstance(instance);
            slist = ys.getServices();
        }

        ListServiceInfoResponse.Builder responseb = ListServiceInfoResponse.newBuilder();
        responseb.addAllService(slist);

        completeOK(req, responseb.build(), SchemaRest.ListServiceInfoResponse.WRITE);
    }


    @Route(path="/api/services/:instance/:name", method={"PATCH", "PUT", "POST"})
    @Route(path="/api/services/:instance/service/:name", method={"PATCH", "PUT", "POST"})
    public void editService(RestRequest req) throws HttpException {
        checkPrivileges(req);
        String instance = req.getRouteParam("instance");
        if(instance==null) throw new BadRequestException("No instance specified");
        boolean global = false;
        if(GLOBAL_INSTANCE.equals(instance)) {
            global = true;
        } else {
            verifyInstance(req, instance);
        }
        String serviceName = req.getRouteParam("name");

        EditServiceRequest request = req.bodyAsMessage(SchemaRest.EditServiceRequest.MERGE).build();
        String state = null;
        if (request.hasState()) state = request.getState();
        if (req.hasQueryParameter("state")) state = req.getQueryParameter("state");

        if(serviceName==null) throw new BadRequestException("No service name specified");

        if (state != null) {
            switch (state.toLowerCase()) {
            case "stop":
            case "stopped":
                Service s;
                if(global) {
                    s = YamcsServer.getGlobalService(serviceName);
                } else {
                    s = YamcsServer.getInstance(instance).getService(serviceName);
                }
                if(s==null) throw new NotFoundException(req, "No service by name '"+serviceName+"'");

                s.stopAsync();
                completeOK(req);
                return;
            case "running":
                try {
                    if(global) {
                        YamcsServer.startGlobalService(serviceName);
                    } else {
                        YamcsServer.getInstance(instance).startService(serviceName);
                    }
                    completeOK(req);
                } catch (Exception e) {
                    completeWithError(req, new InternalServerErrorException(e));
                }
                return;
            default:
                throw new BadRequestException("Unsupported service state '" + state + "'");
            }
        } else {
            completeOK(req);
        }
    }

    private void checkPrivileges(RestRequest req) throws HttpException {
        if(!Privilege.getInstance().hasPrivilege(req.getAuthToken(), Privilege.Type.SYSTEM, Privilege.SystemPrivilege.MayControlServices.name()))  {
            throw new ForbiddenException("No privilege for this operation");
        }
    }
}
