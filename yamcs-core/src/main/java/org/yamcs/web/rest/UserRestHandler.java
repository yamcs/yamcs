package org.yamcs.web.rest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.yamcs.ConnectedClient;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Rest.ListUsersResponse;
import org.yamcs.protobuf.YamcsManagement.ClientInfo.ClientState;
import org.yamcs.protobuf.YamcsManagement.ObjectPrivilegeInfo;
import org.yamcs.protobuf.YamcsManagement.UserInfo;
import org.yamcs.security.ObjectPrivilege;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.security.User;
import org.yamcs.web.HttpException;

/**
 * Handles incoming requests related to the user
 */
public class UserRestHandler extends RestHandler {

    @Route(path = "/api/users", method = "GET")
    public void listUsers(RestRequest req) throws HttpException {
        List<User> users = securityStore.getUsers();
        Collections.sort(users, (u1, u2) -> u1.getUsername().compareToIgnoreCase(u2.getUsername()));

        ListUsersResponse.Builder responseb = ListUsersResponse.newBuilder();
        for (User user : users) {
            UserInfo userInfo = toUserInfo(user);
            responseb.addUsers(userInfo);
        }
        completeOK(req, responseb.build());
    }

    @Route(path = "/api/user", method = "GET")
    public void getUser(RestRequest req) throws HttpException {
        User user = req.getUser();
        UserInfo userInfo = toUserInfo(user);
        completeOK(req, userInfo);
    }

    public static UserInfo toUserInfo(User user) {
        UserInfo.Builder userInfob;
        userInfob = UserInfo.newBuilder();
        userInfob.setLogin(user.getUsername());
        userInfob.setSuperuser(user.isSuperuser());

        List<String> unsortedSystemPrivileges = new ArrayList<>();
        for (SystemPrivilege privilege : user.getSystemPrivileges()) {
            unsortedSystemPrivileges.add(privilege.getName());
        }
        Collections.sort(unsortedSystemPrivileges);
        userInfob.addAllSystemPrivilege(unsortedSystemPrivileges);

        List<ObjectPrivilegeInfo> unsortedObjectPrivileges = new ArrayList<>();
        for (Entry<ObjectPrivilegeType, Set<ObjectPrivilege>> privilege : user.getObjectPrivileges().entrySet()) {
            ObjectPrivilegeInfo.Builder infob = ObjectPrivilegeInfo.newBuilder();
            infob.setType(privilege.getKey().toString());
            for (ObjectPrivilege objectPrivilege : privilege.getValue()) {
                infob.addObject(objectPrivilege.getObject());
            }
            unsortedObjectPrivileges.add(infob.build());
        }
        Collections.sort(unsortedObjectPrivileges, (p1, p2) -> p1.getType().compareTo(p2.getType()));
        userInfob.addAllObjectPrivilege(unsortedObjectPrivileges);

        for (ConnectedClient client : ManagementService.getInstance().getClients(user.getUsername())) {
            userInfob.addClientInfo(YamcsToGpbAssembler.toClientInfo(client, ClientState.CONNECTED));
        }

        return userInfob.build();
    }
}
