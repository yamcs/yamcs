package org.yamcs.http.api;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.yamcs.http.BadRequestException;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.protobuf.Rest.CreateServiceAccountRequest;
import org.yamcs.protobuf.Rest.CreateServiceAccountResponse;
import org.yamcs.protobuf.Rest.ListServiceAccountResponse;
import org.yamcs.protobuf.YamcsManagement.ServiceAccountInfo;
import org.yamcs.security.ApplicationCredentials;
import org.yamcs.security.Directory;
import org.yamcs.security.ServiceAccount;

/**
 * Handles incoming requests related to service accounts
 */
public class ServiceAccountRestHandler extends RestHandler {

    @Route(path = "/api/service-accounts", method = "GET")
    public void listServiceAccounts(RestRequest req) throws HttpException {
        if (!req.getUser().isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }
        Directory directory = securityStore.getDirectory();
        List<ServiceAccount> serviceAccounts = directory.getServiceAccounts();
        Collections.sort(serviceAccounts, (r1, r2) -> r1.getName().compareToIgnoreCase(r2.getName()));

        ListServiceAccountResponse.Builder responseb = ListServiceAccountResponse.newBuilder();
        for (ServiceAccount serviceAccount : serviceAccounts) {
            ServiceAccountInfo serviceAccountInfo = toServiceAccountInfo(serviceAccount);
            responseb.addServiceAccounts(serviceAccountInfo);
        }
        completeOK(req, responseb.build());
    }

    @Route(path = "/api/service-accounts/:name", method = "GET")
    public void getServiceAccount(RestRequest req) throws HttpException {
        if (!req.getUser().isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }
        Directory directory = securityStore.getDirectory();
        String name = req.getRouteParam("name");
        ServiceAccount serviceAccount = directory.getServiceAccount(name);
        if (serviceAccount == null) {
            throw new NotFoundException(req);
        }
        completeOK(req, toServiceAccountInfo(serviceAccount));
    }

    @Route(path = "/api/service-accounts/:name", method = "DELETE")
    public void deleteServiceAccount(RestRequest req) throws HttpException {
        if (!req.getUser().isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }
        Directory directory = securityStore.getDirectory();
        directory.deleteServiceAccount(req.getRouteParam("name"));
        completeOK(req);
    }

    @Route(path = "/api/service-accounts", method = "POST")
    public void createServiceAccount(RestRequest req) throws HttpException {
        if (!req.getUser().isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }
        CreateServiceAccountRequest request = req.bodyAsMessage(CreateServiceAccountRequest.newBuilder()).build();
        if (!request.hasName()) {
            throw new BadRequestException("No name was specified");
        }
        Directory directory = securityStore.getDirectory();
        if (directory.getServiceAccount(request.getName()) != null) {
            throw new BadRequestException("An account named '" + request.getName() + "' already exists");
        }

        ServiceAccount serviceAccount = new ServiceAccount(request.getName(), req.getUser());
        ApplicationCredentials credentials;
        try {
            credentials = directory.addServiceAccount(serviceAccount);
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }

        CreateServiceAccountResponse.Builder responseb = CreateServiceAccountResponse.newBuilder();
        responseb.setName(serviceAccount.getName());
        responseb.setApplicationId(credentials.getApplicationId());
        responseb.setApplicationSecret(credentials.getApplicationSecret());
        completeOK(req, responseb.build());
    }

    private static ServiceAccountInfo toServiceAccountInfo(ServiceAccount serviceAccount) {
        ServiceAccountInfo.Builder b = ServiceAccountInfo.newBuilder();
        b.setName(serviceAccount.getName());
        b.setActive(serviceAccount.isActive());
        if (serviceAccount.getDisplayName() != null) {
            b.setDisplayName(serviceAccount.getDisplayName());
        }
        return b.build();
    }
}