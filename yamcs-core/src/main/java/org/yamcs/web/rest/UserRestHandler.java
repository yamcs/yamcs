package org.yamcs.web.rest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
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

    @Route(path = "/api/user", method = "GET")
    public void getUser(RestRequest req) throws HttpException {
        User user = req.getUser();
        UserInfo userInfo = toUserInfo(user, true);
        completeOK(req, userInfo);
    }

    public static UserInfo toUserInfo(User user, boolean addClientInfo) {
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

        for (ClientInfo ci : ManagementService.getInstance().getClientInfo(userInfob.getLogin())) {
            userInfob.addClientInfo(ci);
        }

        return userInfob.build();
    }
}
