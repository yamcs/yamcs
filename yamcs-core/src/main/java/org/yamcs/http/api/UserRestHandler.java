package org.yamcs.http.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.yamcs.ConnectedClient;
import org.yamcs.YamcsServer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Rest.CreateUserRequest;
import org.yamcs.protobuf.Rest.EditUserRequest;
import org.yamcs.protobuf.Rest.ListUsersResponse;
import org.yamcs.protobuf.YamcsManagement.ClientInfo.ClientState;
import org.yamcs.protobuf.YamcsManagement.ExternalIdentityInfo;
import org.yamcs.protobuf.YamcsManagement.GroupInfo;
import org.yamcs.protobuf.YamcsManagement.ObjectPrivilegeInfo;
import org.yamcs.protobuf.YamcsManagement.UserInfo;
import org.yamcs.security.Directory;
import org.yamcs.security.Group;
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
        Collections.sort(users, (u1, u2) -> u1.getName().compareToIgnoreCase(u2.getName()));

        ListUsersResponse.Builder responseb = ListUsersResponse.newBuilder();
        for (User user : users) {
            UserInfo userb = toUserInfo(user, req.getUser().isSuperuser());
            responseb.addUsers(userb);
        }
        completeOK(req, responseb.build());
    }

    @Route(path = "/api/users", method = "POST")
    public void createUser(RestRequest req) throws HttpException {
        if (!req.getUser().isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }
        Directory directory = securityStore.getDirectory();
        CreateUserRequest request = req.bodyAsMessage(CreateUserRequest.newBuilder()).build();
        if (!request.hasName()) {
            throw new BadRequestException("Name is required");
        }
        String name = request.getName().trim();
        if (name.isEmpty()) {
            throw new BadRequestException("Name is required");
        }
        if (directory.getUser(name) != null) {
            throw new BadRequestException("A user named '" + name + "' already exists");
        }

        User user = new User(name, req.getUser());
        if (request.hasDisplayName()) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.hasEmail()) {
            user.setEmail(request.getEmail());
        }
        user.confirm();

        try {
            directory.addUser(user);

            if (request.hasPassword()) {
                directory.changePassword(user, request.getPassword().toCharArray());
            }
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }

        completeOK(req, toUserInfo(user, req.getUser().isSuperuser()));
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

    @Route(path = "/api/users/:username", method = "PATCH")
    public void editUser(RestRequest req) throws HttpException {
        if (!req.getUser().isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }
        String username = req.getRouteParam("username");
        Directory directory = securityStore.getDirectory();
        User user = directory.getUser(username);
        if (user == null) {
            throw new NotFoundException(req);
        }

        EditUserRequest request = req.bodyAsMessage(EditUserRequest.newBuilder()).build();
        if (request.hasPassword() && user.isExternallyManaged()) {
            throw new BadRequestException("Cannot set the password of an externally managed user");
        }
        if (user.equals(req.getUser())) {
            if (request.hasActive()) {
                throw new BadRequestException("You cannot change your own active attribute");
            }
            if (request.hasSuperuser()) {
                throw new BadRequestException("You cannot change your own superuser attribute");
            }
        }

        if (request.hasDisplayName()) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.hasEmail()) {
            user.setEmail(request.getEmail());
        }
        if (request.hasActive()) {
            user.setActive(request.getActive());
        }
        if (request.hasSuperuser()) {
            user.setSuperuser(request.getSuperuser());
        }
        try {
            directory.updateUserProperties(user);

            if (request.hasPassword()) {
                directory.changePassword(user, request.getPassword().toCharArray());
            }
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }
        completeOK(req, toUserInfo(user, req.getUser().isSuperuser()));
    }

    @Route(path = "/api/users/:username/identities/:provider", method = "DELETE")
    public void deleteIdentity(RestRequest req) throws HttpException {
        if (!req.getUser().isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }
        Directory directory = securityStore.getDirectory();
        String username = req.getRouteParam("username");
        User user = directory.getUser(username);
        if (user == null) {
            throw new NotFoundException(req);
        }
        user.deleteIdentity(req.getRouteParam("provider"));
        try {
            directory.updateUserProperties(user);
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
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
        userb.setName(user.getName());
        userb.setLogin(user.getName());
        userb.setActive(user.isActive());
        userb.setSuperuser(user.isSuperuser());
        if (user.getDisplayName() != null) {
            userb.setDisplayName(user.getDisplayName());
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

            user.getIdentityEntrySet().forEach(entry -> {
                userb.addIdentities(ExternalIdentityInfo.newBuilder()
                        .setProvider(entry.getKey())
                        .setIdentity(entry.getValue()));
            });

            for (Group group : directory.getGroups(user)) {
                GroupInfo groupInfo = GroupRestHandler.toGroupInfo(group, false);
                userb.addGroups(groupInfo);
            }
            for (ConnectedClient client : ManagementService.getInstance().getClients(user.getName())) {
                userb.addClientInfo(YamcsToGpbAssembler.toClientInfo(client, ClientState.CONNECTED));
            }
        }

        return userb.build();
    }
}
