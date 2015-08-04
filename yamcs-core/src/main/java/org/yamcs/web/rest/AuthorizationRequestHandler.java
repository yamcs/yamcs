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
     * Sends the parameters for the requested yamcs instance. If no namespaces are specified, send qualified names.
     */
    private Rest.RestListAuthorisationsResponse listAuthorisations(AuthenticationToken authToken) throws RestException {
        Rest.RestListAuthorisationsResponse.Builder responseb = Rest.RestListAuthorisationsResponse.newBuilder();

        User user = Privilege.getInstance().getUser(authToken);
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


}
