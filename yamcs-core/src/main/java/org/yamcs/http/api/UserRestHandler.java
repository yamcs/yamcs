package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.yamcs.ConnectedClient;
import org.yamcs.YamcsServer;
import org.yamcs.http.HttpException;
import org.yamcs.http.NotFoundException;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Rest.ListUsersResponse;
import org.yamcs.protobuf.YamcsManagement.ClientInfo.ClientState;
import org.yamcs.protobuf.YamcsManagement.ObjectPrivilegeInfo;
import org.yamcs.protobuf.YamcsManagement.UserInfo;
import org.yamcs.security.Directory;
import org.yamcs.security.ObjectPrivilege;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.security.User;
import org.yamcs.utils.TimeEncoding;

/**
 * Handles incoming requests related to the user
 */
public class UserRestHandler extends RestHandler {

    @Route(path = "/api/users", method = "GET")
    public void listUsers(RestRequest req) throws HttpException {
        List<User> users = securityStore.getDirectory().getUsers();
        Collections.sort(users, (u1, u2) -> u1.getUsername().compareToIgnoreCase(u2.getUsername()));

        ListUsersResponse.Builder responseb = ListUsersResponse.newBuilder();
        for (User user : users) {
            UserInfo userb = toUserInfo(user, req.getUser().isSuperuser());
            responseb.addUsers(userb);
        }
        completeOK(req, responseb.build());
    }

    @Route(path = "/api/users/:username", method = "GET")
    public void getUser(RestRequest req) throws HttpException {
        String username = req.getRouteParam("username");
        User user = securityStore.getDirectory().getUser(username);
        if (user == null) {
            throw new NotFoundException(req);
        }
        completeOK(req, toUserInfo(user, req.getUser().isSuperuser()));
    }

    @Route(path = "/api/user", method = "GET")
    public void getMyUser(RestRequest req) throws HttpException {
        User user = req.getUser();
        completeOK(req, toUserInfo(user, true));
    }

    public static UserInfo toUserInfo(User user, boolean sensitiveDetails) {
        UserInfo.Builder userb;
        userb = UserInfo.newBuilder();
        userb.setUsername(user.getUsername());
        userb.setLogin(user.getUsername());
        userb.setActive(user.isActive());
        userb.setSuperuser(user.isSuperuser());
        if (user.getName() != null) {
            userb.setName(user.getName());
        }
        if (user.getEmail() != null) {
            userb.setEmail(user.getEmail());
        }
        if (sensitiveDetails) {
            Directory directory = YamcsServer.getServer().getSecurityStore().getDirectory();
            User createdBy = directory.getUser(user.getCreatedBy());
            if (createdBy != null) {
                userb.setCreatedBy(toUserInfo(createdBy, false));
            }
            userb.setCreationTime(TimeEncoding.toProtobufTimestamp(user.getCreationTime()));
            if (user.getConfirmationTime() != TimeEncoding.INVALID_INSTANT) {
                userb.setConfirmationTime(TimeEncoding.toProtobufTimestamp(user.getConfirmationTime()));
            }
            if (user.getLastLoginTime() != TimeEncoding.INVALID_INSTANT) {
                userb.setLastLoginTime(TimeEncoding.toProtobufTimestamp(user.getLastLoginTime()));
            }

            List<String> unsortedSystemPrivileges = new ArrayList<>();
            for (SystemPrivilege privilege : user.getSystemPrivileges()) {
                unsortedSystemPrivileges.add(privilege.getName());
            }
            Collections.sort(unsortedSystemPrivileges);
            userb.addAllSystemPrivilege(unsortedSystemPrivileges);

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
            userb.addAllObjectPrivilege(unsortedObjectPrivileges);

            for (ConnectedClient client : ManagementService.getInstance().getClients(user.getUsername())) {
                userb.addClientInfo(YamcsToGpbAssembler.toClientInfo(client, ClientState.CONNECTED));
            }
        }

        return userb.build();
    }
}
