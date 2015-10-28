package org.yamcs.web.rest;


import java.util.Arrays;

import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.UserInfo;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.Privilege;
import org.yamcs.security.User;

/**
 * Handles incoming requests related to the user
 */
public class UserRequestHandler extends RestRequestHandler {
    
    @Override
    public String getPath() {
        return "user";
    }

    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (req.hasPathSegment(pathOffset)) {
            throw new NotFoundException(req);
        }
        
        UserInfo info = getUser(req.authToken);
        return new RestResponse(req, info, SchemaYamcs.UserInfo.WRITE);
    }

    private UserInfo getUser(AuthenticationToken authToken) throws RestException {
        User user = Privilege.getInstance().getUser(authToken);
        if(user == null) {
            UserInfo.Builder infob = buildFullyPrivilegedUser();
            infob.setLogin("anonymous");
            return infob.build();
        }

        UserInfo.Builder userInfob = UserInfo.newBuilder();
        userInfob.setLogin(user.getPrincipalName());
        userInfob.addAllRoles(Arrays.asList(user.getRoles()));
        userInfob.addAllTmParaPrivileges(user.getTmParaPrivileges());
        userInfob.addAllTmParaSetPrivileges(user.getTmParaSetPrivileges());
        userInfob.addAllTmPacketPrivileges(user.getTmPacketPrivileges());
        userInfob.addAllTcPrivileges(user.getTcPrivileges());
        userInfob.addAllSystemPrivileges(user.getSystemPrivileges());

        return userInfob.build();
    }

    private UserInfo.Builder buildFullyPrivilegedUser() {
        UserInfo.Builder userInfob = UserInfo.newBuilder();
        userInfob.addRoles("admin");
        userInfob.addTmParaPrivileges(".*");
        userInfob.addTmParaSetPrivileges(".*");
        userInfob.addTmPacketPrivileges(".*");
        userInfob.addTcPrivileges(".*");
        for(Privilege.SystemPrivilege sp : Privilege.SystemPrivilege.values()) {
            userInfob.addSystemPrivileges(sp.name());
        }
        return userInfob;
    }
}
