package org.yamcs.web.rest;


import java.util.Arrays;

import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.SchemaYamcsManagement;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.UserInfo;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.security.Privilege;
import org.yamcs.security.User;

/**
 * Handles incoming requests related to the user
 */
public class UserRequestHandler extends RestRequestHandler {
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (req.hasPathSegment(pathOffset)) {
            throw new NotFoundException(req);
        }
        
        UserInfo info = getUser(req.getAuthToken());
        return new RestResponse(req, info, SchemaYamcsManagement.UserInfo.WRITE);
    }

    private UserInfo getUser(AuthenticationToken authToken) throws RestException {
        User user = Privilege.getInstance().getUser(authToken);
        
        UserInfo.Builder userInfob;
        if (user == null) {
            userInfob = buildFullyPrivilegedUser();
            userInfob.setLogin(ManagementService.ANONYMOUS);
        } else {
            userInfob = UserInfo.newBuilder();
            userInfob.setLogin(user.getPrincipalName());
            userInfob.addAllRoles(Arrays.asList(user.getRoles()));
            userInfob.addAllTmParaPrivileges(user.getTmParaPrivileges());
            userInfob.addAllTmParaSetPrivileges(user.getTmParaSetPrivileges());
            userInfob.addAllTmPacketPrivileges(user.getTmPacketPrivileges());
            userInfob.addAllTcPrivileges(user.getTcPrivileges());
            userInfob.addAllSystemPrivileges(user.getSystemPrivileges());
        }
        
        for (ClientInfo ci : ManagementService.getInstance().getClientInfo(userInfob.getLogin())) {
            userInfob.addClientInfo(ci);
        }

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
