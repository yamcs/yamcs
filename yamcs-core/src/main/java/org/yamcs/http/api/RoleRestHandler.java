package org.yamcs.http.api;

import java.util.Collections;
import java.util.List;

import org.yamcs.http.HttpException;
import org.yamcs.protobuf.Rest.ListRolesResponse;
import org.yamcs.protobuf.YamcsManagement.RoleInfo;
import org.yamcs.security.Role;

/**
 * Handles incoming requests related to roles
 */
public class RoleRestHandler extends RestHandler {

    @Route(path = "/api/roles", method = "GET")
    public void listRoles(RestRequest req) throws HttpException {
        List<Role> roles = securityStore.getDirectory().getRoles();
        Collections.sort(roles, (r1, r2) -> r1.getName().compareToIgnoreCase(r2.getName()));

        ListRolesResponse.Builder responseb = ListRolesResponse.newBuilder();
        for (Role role : roles) {
            RoleInfo roleInfo = toRoleInfo(role);
            responseb.addRoles(roleInfo);
        }
        completeOK(req, responseb.build());
    }

    public static RoleInfo toRoleInfo(Role role) {
        RoleInfo.Builder roleInfob = RoleInfo.newBuilder();
        roleInfob.setName(role.getName());
        if (role.getDescription() != null) {
            roleInfob.setDescription(role.getDescription());
        }
        return roleInfob.build();
    }
}
