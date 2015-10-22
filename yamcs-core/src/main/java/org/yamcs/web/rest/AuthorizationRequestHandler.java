package org.yamcs.web.rest;


import java.util.Arrays;

import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.ListAuthorizationsResponse;
import org.yamcs.protobuf.Yamcs.UserAuthorizationInfo;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.Privilege;
import org.yamcs.security.User;

/**
 * Handles incoming requests related to the Authorisations
 */
public class AuthorizationRequestHandler extends RestRequestHandler {
    
    @Override
    public String getPath() {
        return "authorization";
    }

    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException{

        if (!req.hasPathSegment(pathOffset)) {
            throw new NotFoundException(req);
        }
        
        ListAuthorizationsResponse  rlar = listAuthorizations(req.authToken);
        return new RestResponse(req, rlar, SchemaYamcs.ListAuthorizationsResponse.WRITE);
    }

    /**
     * Sends list of authorizations
     */
    private ListAuthorizationsResponse listAuthorizations(AuthenticationToken authToken) throws RestException {
        ListAuthorizationsResponse.Builder responseb = ListAuthorizationsResponse.newBuilder();

        User user = Privilege.getInstance().getUser(authToken);
        if(user == null) {
            return buildFullAuthorization(responseb);
        }

        UserAuthorizationInfo.Builder userAuthorizationsInfob = UserAuthorizationInfo.newBuilder();
        userAuthorizationsInfob.addAllRoles(Arrays.asList(user.getRoles()));
        userAuthorizationsInfob.addAllTmParaPrivileges(user.getTmParaPrivileges());
        userAuthorizationsInfob.addAllTmParaSetPrivileges(user.getTmParaSetPrivileges());
        userAuthorizationsInfob.addAllTmPacketPrivileges(user.getTmPacketPrivileges());
        userAuthorizationsInfob.addAllTcPrivileges(user.getTcPrivileges());
        userAuthorizationsInfob.addAllSystemPrivileges(user.getSystemPrivileges());

        responseb.setUserAuthorizationInfo(userAuthorizationsInfob.build());
        return responseb.build();
    }


    private ListAuthorizationsResponse buildFullAuthorization(ListAuthorizationsResponse.Builder responseb) {
        UserAuthorizationInfo.Builder userAuthorizationsInfob = UserAuthorizationInfo.newBuilder();
        userAuthorizationsInfob.addRoles("admin");
        userAuthorizationsInfob.addTmParaPrivileges(".*");
        userAuthorizationsInfob.addTmParaSetPrivileges(".*");
        userAuthorizationsInfob.addTmPacketPrivileges(".*");
        userAuthorizationsInfob.addTcPrivileges(".*");
        for(Privilege.SystemPrivilege sp : Privilege.SystemPrivilege.values()) {
            userAuthorizationsInfob.addSystemPrivileges(sp.name());
        }
        responseb.setUserAuthorizationInfo(userAuthorizationsInfob.build());
        return responseb.build();
    }
}
