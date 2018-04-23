package org.yamcs.web.rest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.UserInfo;
import org.yamcs.security.Privilege;
import org.yamcs.security.User;
import org.yamcs.web.HttpException;

/**
 * Handles incoming requests related to the user
 */
public class UserRestHandler extends RestHandler {

    @Route(path = "/api/user", method = "GET")
    public void getUser(RestRequest req) throws HttpException {
        User user = Privilege.getInstance().getUser(req.getAuthToken());
        UserInfo userInfo = toUserInfo(user, true);
        completeOK(req, userInfo);
    }

    public static UserInfo toUserInfo(User user, boolean addClientInfo) {
        UserInfo.Builder userInfob;
        if (user == null) {
            userInfob = buildFullyPrivilegedUser();
            userInfob.setLogin(Privilege.getInstance().getDefaultUser());
        } else {
            userInfob = UserInfo.newBuilder();
            userInfob.setLogin(user.getPrincipalName());
            userInfob.addAllRoles(asSortedList(user.getRoles()));
            userInfob.addAllTmParaPrivileges(asSortedList(user.getTmParaPrivileges()));
            userInfob.addAllTmParaSetPrivileges(asSortedList(user.getTmParaSetPrivileges()));
            userInfob.addAllTmPacketPrivileges(asSortedList(user.getTmPacketPrivileges()));
            userInfob.addAllTcPrivileges(asSortedList(user.getTcPrivileges()));
            userInfob.addAllSystemPrivileges(asSortedList(user.getSystemPrivileges()));
            userInfob.addAllStreamPrivileges(asSortedList(user.getStreamPrivileges()));
            userInfob.addAllCmdHistoryPrivileges(asSortedList(user.getCmdHistoryPrivileges()));
        }

        for (ClientInfo ci : ManagementService.getInstance().getClientInfo(userInfob.getLogin())) {
            userInfob.addClientInfo(ci);
        }

        return userInfob.build();
    }

    private static UserInfo.Builder buildFullyPrivilegedUser() {
        UserInfo.Builder userInfob = UserInfo.newBuilder();
        // userInfob.addRoles("admin");
        userInfob.addTmParaPrivileges(".*");
        userInfob.addTmParaSetPrivileges(".*");
        userInfob.addTmPacketPrivileges(".*");
        userInfob.addTcPrivileges(".*");
        userInfob.addTcPrivileges(".*");
        userInfob.addSystemPrivileges(".*");
        userInfob.addStreamPrivileges(".*");
        userInfob.addCmdHistoryPrivileges(".*");
        return userInfob;
    }

    private static List<String> asSortedList(String[] unsorted) {
        Arrays.sort(unsorted);
        return Arrays.asList(unsorted);
    }

    private static List<String> asSortedList(Collection<String> unsorted) {
        List<String> sorted = new ArrayList<>(unsorted);
        Collections.sort(sorted);
        return sorted;
    }
}
