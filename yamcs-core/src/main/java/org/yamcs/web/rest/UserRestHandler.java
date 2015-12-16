package org.yamcs.web.rest;


import java.util.Arrays;

import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.SchemaYamcsManagement;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.UserInfo;
import org.yamcs.security.Privilege;
import org.yamcs.security.User;
import org.yamcs.web.HttpException;

import io.netty.channel.ChannelFuture;

/**
 * Handles incoming requests related to the user
 */
public class UserRestHandler extends RestHandler {
    

    @Route(path = "/api/user", method = "GET")
    public ChannelFuture getUser(RestRequest req) throws HttpException {
        User user = Privilege.getInstance().getUser(req.getAuthToken());
        
        UserInfo.Builder userInfob;
        if (user == null) {
            userInfob = buildFullyPrivilegedUser();
            userInfob.setLogin(Privilege.getDefaultUser());
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

        UserInfo info = userInfob.build();
        return sendOK(req, info, SchemaYamcsManagement.UserInfo.WRITE);
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
