package org.yamcs.http.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.yamcs.YamcsServer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.protobuf.CreateGroupRequest;
import org.yamcs.protobuf.CreateServiceAccountRequest;
import org.yamcs.protobuf.CreateServiceAccountResponse;
import org.yamcs.protobuf.CreateUserRequest;
import org.yamcs.protobuf.ExternalIdentityInfo;
import org.yamcs.protobuf.GroupInfo;
import org.yamcs.protobuf.ListGroupsResponse;
import org.yamcs.protobuf.ListPrivilegesResponse;
import org.yamcs.protobuf.ListServiceAccountsResponse;
import org.yamcs.protobuf.ListUsersResponse;
import org.yamcs.protobuf.ObjectPrivilegeInfo;
import org.yamcs.protobuf.ServiceAccountInfo;
import org.yamcs.protobuf.UpdateGroupRequest;
import org.yamcs.protobuf.UpdateUserRequest;
import org.yamcs.protobuf.UserInfo;
import org.yamcs.security.ApplicationCredentials;
import org.yamcs.security.Directory;
import org.yamcs.security.Group;
import org.yamcs.security.ObjectPrivilege;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.ServiceAccount;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.security.User;
import org.yamcs.utils.TimeEncoding;

/**
 * Handles incoming requests related to Identity and Access Management (IAM)
 */
public class IAMRestHandler extends RestHandler {

    @Route(rpc = "yamcs.protobuf.iam.IAM.ListPrivileges")
    public void listSystemPrivileges(RestRequest req) throws HttpException {
        List<SystemPrivilege> privileges = new ArrayList<>(securityStore.getSystemPrivileges());
        Collections.sort(privileges, (p1, p2) -> p1.getName().compareTo(p2.getName()));

        ListPrivilegesResponse.Builder responseb = ListPrivilegesResponse.newBuilder();
        for (SystemPrivilege privilege : privileges) {
            responseb.addSystemPrivileges(privilege.getName());
        }
        completeOK(req, responseb.build());
    }

    @Route(rpc = "yamcs.protobuf.iam.IAM.ListUsers")
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

    @Route(rpc = "yamcs.protobuf.iam.IAM.CreateUser")
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

    @Route(rpc = "yamcs.protobuf.iam.IAM.GetUser")
    public void getUser(RestRequest req) throws HttpException {
        String username = req.getRouteParam("username");
        User user = securityStore.getDirectory().getUser(username);
        if (user == null) {
            throw new NotFoundException(req);
        }
        completeOK(req, toUserInfo(user, req.getUser().isSuperuser()));
    }

    @Route(rpc = "yamcs.protobuf.iam.IAM.UpdateUser")
    public void updateUser(RestRequest req) throws HttpException {
        if (!req.getUser().isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }
        String username = req.getRouteParam("username");
        Directory directory = securityStore.getDirectory();
        User user = directory.getUser(username);
        if (user == null) {
            throw new NotFoundException(req);
        }

        UpdateUserRequest request = req.bodyAsMessage(UpdateUserRequest.newBuilder()).build();
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

    @Route(rpc = "yamcs.protobuf.iam.IAM.DeleteIdentity")
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

    @Route(rpc = "yamcs.protobuf.iam.IAM.GetOwnUser")
    public void getMyUser(RestRequest req) throws HttpException {
        User user = req.getUser();
        completeOK(req, toUserInfo(user, true));
    }

    @Route(rpc = "yamcs.protobuf.iam.IAM.ListServiceAccounts")
    public void listServiceAccounts(RestRequest req) throws HttpException {
        if (!req.getUser().isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }
        Directory directory = securityStore.getDirectory();
        List<ServiceAccount> serviceAccounts = directory.getServiceAccounts();
        Collections.sort(serviceAccounts, (r1, r2) -> r1.getName().compareToIgnoreCase(r2.getName()));

        ListServiceAccountsResponse.Builder responseb = ListServiceAccountsResponse.newBuilder();
        for (ServiceAccount serviceAccount : serviceAccounts) {
            ServiceAccountInfo serviceAccountInfo = toServiceAccountInfo(serviceAccount, false);
            responseb.addServiceAccounts(serviceAccountInfo);
        }
        completeOK(req, responseb.build());
    }

    @Route(rpc = "yamcs.protobuf.iam.IAM.GetServiceAccount")
    public void getServiceAccount(RestRequest req) throws HttpException {
        if (!req.getUser().isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }
        Directory directory = securityStore.getDirectory();
        String name = req.getRouteParam("name");
        ServiceAccount serviceAccount = directory.getServiceAccount(name);
        if (serviceAccount == null) {
            throw new NotFoundException(req);
        }
        completeOK(req, toServiceAccountInfo(serviceAccount, true));
    }

    @Route(rpc = "yamcs.protobuf.iam.IAM.DeleteServiceAccount")
    public void deleteServiceAccount(RestRequest req) throws HttpException {
        if (!req.getUser().isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }
        Directory directory = securityStore.getDirectory();
        directory.deleteServiceAccount(req.getRouteParam("name"));
        completeOK(req);
    }

    @Route(rpc = "yamcs.protobuf.iam.IAM.CreateServiceAccount")
    public void createServiceAccount(RestRequest req) throws HttpException {
        if (!req.getUser().isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }
        CreateServiceAccountRequest request = req.bodyAsMessage(CreateServiceAccountRequest.newBuilder()).build();
        if (!request.hasName()) {
            throw new BadRequestException("No name was specified");
        }
        Directory directory = securityStore.getDirectory();
        if (directory.getServiceAccount(request.getName()) != null) {
            throw new BadRequestException("An account named '" + request.getName() + "' already exists");
        }

        ServiceAccount serviceAccount = new ServiceAccount(request.getName(), req.getUser());
        ApplicationCredentials credentials;
        try {
            credentials = directory.addServiceAccount(serviceAccount);
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }

        CreateServiceAccountResponse.Builder responseb = CreateServiceAccountResponse.newBuilder();
        responseb.setName(serviceAccount.getName());
        responseb.setApplicationId(credentials.getApplicationId());
        responseb.setApplicationSecret(credentials.getApplicationSecret());
        completeOK(req, responseb.build());
    }

    @Route(rpc = "yamcs.protobuf.iam.IAM.ListGroups")
    public void listGroups(RestRequest req) throws HttpException {
        Directory directory = securityStore.getDirectory();
        List<Group> groups = directory.getGroups();
        Collections.sort(groups, (r1, r2) -> r1.getName().compareToIgnoreCase(r2.getName()));

        ListGroupsResponse.Builder responseb = ListGroupsResponse.newBuilder();
        for (Group group : groups) {
            GroupInfo groupInfo = toGroupInfo(group, true);
            responseb.addGroups(groupInfo);
        }
        completeOK(req, responseb.build());
    }

    @Route(rpc = "yamcs.protobuf.iam.IAM.GetGroup")
    public void getGroup(RestRequest req) throws HttpException {
        Directory directory = securityStore.getDirectory();
        String name = req.getRouteParam("name");
        Group group = directory.getGroup(name);
        if (group == null) {
            throw new NotFoundException(req);
        }
        completeOK(req, toGroupInfo(group, true));
    }

    @Route(rpc = "yamcs.protobuf.iam.IAM.CreateGroup")
    public void createGroup(RestRequest req) throws HttpException {
        if (!req.getUser().isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }
        CreateGroupRequest request = req.bodyAsMessage(CreateGroupRequest.newBuilder()).build();
        if (!request.hasName()) {
            throw new BadRequestException("No group name was specified");
        }
        Directory directory = securityStore.getDirectory();
        if (directory.getGroup(request.getName()) != null) {
            throw new BadRequestException("A group named '" + request.getName() + "' already exists");
        }
        Group group = new Group(request.getName());
        if (request.hasDescription()) {
            group.setDescription(request.getDescription());
        }
        for (String username : request.getUsersList()) {
            int memberId = directory.getUser(username).getId();
            group.addMember(memberId);
        }
        for (String serviceAccountName : request.getServiceAccountsList()) {
            int memberId = directory.getServiceAccount(serviceAccountName).getId();
            group.addMember(memberId);
        }

        try {
            directory.addGroup(group);
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }
        completeOK(req, toGroupInfo(group, true));
    }

    @Route(rpc = "yamcs.protobuf.iam.IAM.UpdateGroup")
    public void updateGroup(RestRequest req) throws HttpException {
        if (!req.getUser().isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }
        String name = req.getRouteParam("name");
        Directory directory = securityStore.getDirectory();
        Group group = directory.getGroup(name);
        if (group == null) {
            throw new NotFoundException(req);
        }

        UpdateGroupRequest request = req.bodyAsMessage(UpdateGroupRequest.newBuilder()).build();
        try {
            if (request.hasName() && !request.getName().equals(group.getName())) {
                String newName = request.getName().trim();
                if (newName.isEmpty()) {
                    throw new BadRequestException("Name must not be empty");
                }
                directory.renameGroup(group.getName(), request.getName());
            }
            if (request.hasDescription()) {
                group.setDescription(request.getDescription());
            }
            if (request.hasMemberInfo()) {
                Set<Integer> memberIds = new HashSet<>();
                for (String username : request.getMemberInfo().getUsersList()) {
                    User user = directory.getUser(username);
                    memberIds.add(user.getId());
                }
                for (String serviceAccountName : request.getMemberInfo().getServiceAccountsList()) {
                    ServiceAccount serviceAccount = directory.getServiceAccount(serviceAccountName);
                    memberIds.add(serviceAccount.getId());
                }
                group.setMembers(memberIds);
            }
            directory.updateGroupProperties(group);
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }
        completeOK(req, toGroupInfo(group, true));
    }

    @Route(rpc = "yamcs.protobuf.iam.IAM.DeleteGroup")
    public void deleteGroup(RestRequest req) throws HttpException {
        if (!req.getUser().isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }
        String name = req.getRouteParam("name");
        Directory directory = securityStore.getDirectory();
        Group group = directory.getGroup(name);
        if (group == null) {
            throw new NotFoundException(req);
        }

        try {
            directory.deleteGroup(group);
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }

        completeOK(req, toGroupInfo(group, true));
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
                GroupInfo groupInfo = toGroupInfo(group, false);
                userb.addGroups(groupInfo);
            }
        }

        return userb.build();
    }

    private static GroupInfo toGroupInfo(Group group, boolean addMembers) {
        Directory directory = YamcsServer.getServer().getSecurityStore().getDirectory();

        GroupInfo.Builder groupInfob = GroupInfo.newBuilder();
        groupInfob.setName(group.getName());
        if (group.getDescription() != null) {
            groupInfob.setDescription(group.getDescription());
        }
        if (addMembers) {
            for (int memberId : group.getMembers()) {
                User user = directory.getUser(memberId);
                UserInfo userInfo = toUserInfo(user, false);
                groupInfob.addUsers(userInfo);
            }
        }
        return groupInfob.build();
    }

    private static ServiceAccountInfo toServiceAccountInfo(ServiceAccount serviceAccount, boolean details) {
        ServiceAccountInfo.Builder b = ServiceAccountInfo.newBuilder();
        b.setName(serviceAccount.getName());
        b.setActive(serviceAccount.isActive());
        if (serviceAccount.getDisplayName() != null) {
            b.setDisplayName(serviceAccount.getDisplayName());
        }
        if (details) {
            Directory directory = YamcsServer.getServer().getSecurityStore().getDirectory();
            User createdBy = directory.getUser(serviceAccount.getCreatedBy());
            if (createdBy != null) {
                b.setCreatedBy(toUserInfo(createdBy, false));
            }
            b.setCreationTime(TimeEncoding.toProtobufTimestamp(createdBy.getCreationTime()));
        }
        return b.build();
    }
}
