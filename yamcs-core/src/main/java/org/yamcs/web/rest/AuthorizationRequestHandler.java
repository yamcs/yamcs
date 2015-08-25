package org.yamcs.web.rest;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Rest;
import org.yamcs.protobuf.SchemaPvalue;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.Privilege;
import org.yamcs.security.User;

import java.util.Arrays;

/**
 * Handles incoming requests related to the Authorisations
 */
public class AuthorizationRequestHandler implements RestRequestHandler {
    private static final Logger log = LoggerFactory.getLogger(AuthorizationRequestHandler.class);

    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException{

        if (!req.hasPathSegment(pathOffset)) {
            throw new NotFoundException(req);
        }
        
        Rest.RestListAuthorisationsResponse  rlar = listAuthorisations(req.authToken);
        return new RestResponse(req, rlar, SchemaRest.RestListAuthorisationsResponse.WRITE);
    }

    /**
     * Sends list of authorizations
     */
    private Rest.RestListAuthorisationsResponse listAuthorisations(AuthenticationToken authToken) throws RestException {
        Rest.RestListAuthorisationsResponse.Builder responseb = Rest.RestListAuthorisationsResponse.newBuilder();

        User user = Privilege.getInstance().getUser(authToken);
        if(user == null) {

            return buildFullAuthoriztion(responseb);
        }

        Rest.UserAuthorizationsInfo.Builder userAuthorizationsInfob = Rest.UserAuthorizationsInfo.newBuilder();
        userAuthorizationsInfob.addAllRoles(Arrays.asList(user.getRoles()));
        userAuthorizationsInfob.addAllTmParaPrivileges(user.getTmParaPrivileges());
        userAuthorizationsInfob.addAllTmParaSetPrivileges(user.getTmParaSetPrivileges());
        userAuthorizationsInfob.addAllTmPacketPrivileges(user.getTmPacketPrivileges());
        userAuthorizationsInfob.addAllTcPrivileges(user.getTcPrivileges());
        userAuthorizationsInfob.addAllSystemPrivileges(user.getSystemPrivileges());

        responseb.setUserAuthorizationsInfo(userAuthorizationsInfob.build());
        return responseb.build();
    }


    private Rest.RestListAuthorisationsResponse buildFullAuthoriztion(Rest.RestListAuthorisationsResponse.Builder responseb) {
        Rest.UserAuthorizationsInfo.Builder userAuthorizationsInfob = Rest.UserAuthorizationsInfo.newBuilder();
        userAuthorizationsInfob.addRoles("admin");
        userAuthorizationsInfob.addTmParaPrivileges(".*");
        userAuthorizationsInfob.addTmParaSetPrivileges(".*");
        userAuthorizationsInfob.addTmPacketPrivileges(".*");
        userAuthorizationsInfob.addTcPrivileges(".*");
        for(Privilege.SystemPrivilege sp : Privilege.SystemPrivilege.values()) {
            userAuthorizationsInfob.addSystemPrivileges(sp.name());
        }
        responseb.setUserAuthorizationsInfo(userAuthorizationsInfob.build());
        return responseb.build();
    }


}
