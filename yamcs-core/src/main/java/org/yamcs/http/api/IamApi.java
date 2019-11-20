package org.yamcs.http.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.yamcs.YamcsServer;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.protobuf.AbstractIamApi;
import org.yamcs.protobuf.CreateGroupRequest;
import org.yamcs.protobuf.CreateServiceAccountRequest;
import org.yamcs.protobuf.CreateServiceAccountResponse;
import org.yamcs.protobuf.CreateUserRequest;
import org.yamcs.protobuf.DeleteGroupRequest;
import org.yamcs.protobuf.DeleteIdentityRequest;
import org.yamcs.protobuf.DeleteRoleAssignmentRequest;
import org.yamcs.protobuf.DeleteServiceAccountRequest;
import org.yamcs.protobuf.ExternalIdentityInfo;
import org.yamcs.protobuf.GetGroupRequest;
import org.yamcs.protobuf.GetRoleRequest;
import org.yamcs.protobuf.GetServiceAccountRequest;
import org.yamcs.protobuf.GetUserRequest;
import org.yamcs.protobuf.GroupInfo;
import org.yamcs.protobuf.ListGroupsResponse;
import org.yamcs.protobuf.ListPrivilegesResponse;
import org.yamcs.protobuf.ListRolesResponse;
import org.yamcs.protobuf.ListServiceAccountsResponse;
import org.yamcs.protobuf.ListUsersResponse;
import org.yamcs.protobuf.ObjectPrivilegeInfo;
import org.yamcs.protobuf.RoleInfo;
import org.yamcs.protobuf.ServiceAccountInfo;
import org.yamcs.protobuf.UpdateGroupRequest;
import org.yamcs.protobuf.UpdateUserRequest;
import org.yamcs.protobuf.UserInfo;
import org.yamcs.security.ApplicationCredentials;
import org.yamcs.security.Directory;
import org.yamcs.security.Group;
import org.yamcs.security.ObjectPrivilege;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.Role;
import org.yamcs.security.SecurityStore;
import org.yamcs.security.ServiceAccount;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.security.User;
import org.yamcs.utils.TimeEncoding;

import com.google.protobuf.Empty;

public class IamApi extends AbstractIamApi<Context> {

    @Override
    public void listRoles(Context ctx, Empty request, Observer<ListRolesResponse> observer) {
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        List<Role> roles = securityStore.getDirectory().getRoles();
        Collections.sort(roles, (p1, p2) -> p1.getName().compareTo(p2.getName()));

        ListRolesResponse.Builder responseb = ListRolesResponse.newBuilder();
        for (Role role : roles) {
            RoleInfo roleInfo = toRoleInfo(role);
            responseb.addRoles(roleInfo);
        }
        observer.complete(responseb.build());
    }

    @Override
    public void getRole(Context ctx, GetRoleRequest request, Observer<RoleInfo> observer) {
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        Role role = securityStore.getDirectory().getRole(request.getName());
        if (role == null) {
            throw new NotFoundException();
        }
        observer.complete(toRoleInfo(role));
    }

    @Override
    public void deleteRoleAssignment(Context ctx, DeleteRoleAssignmentRequest request, Observer<Empty> observer) {
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        if (!ctx.user.isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }
        Directory directory = securityStore.getDirectory();
        String username = request.getName();
        User user = directory.getUser(username);
        if (user == null) {
            throw new NotFoundException();
        }
        user.deleteRole(request.getRole());
        try {
            directory.updateUserProperties(user);
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void listPrivileges(Context ctx, Empty request, Observer<ListPrivilegesResponse> observer) {
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        List<SystemPrivilege> privileges = new ArrayList<>(securityStore.getSystemPrivileges());
        Collections.sort(privileges, (p1, p2) -> p1.getName().compareTo(p2.getName()));

        ListPrivilegesResponse.Builder responseb = ListPrivilegesResponse.newBuilder();
        for (SystemPrivilege privilege : privileges) {
            responseb.addSystemPrivileges(privilege.getName());
        }
        observer.complete(responseb.build());
    }

    @Override
    public void listUsers(Context ctx, Empty request, Observer<ListUsersResponse> observer) {
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        List<User> users = securityStore.getDirectory().getUsers();
        Collections.sort(users, (u1, u2) -> u1.getName().compareToIgnoreCase(u2.getName()));

        ListUsersResponse.Builder responseb = ListUsersResponse.newBuilder();
        for (User user : users) {
            UserInfo userb = toUserInfo(user, ctx.user.isSuperuser());
            responseb.addUsers(userb);
        }
        observer.complete(responseb.build());
    }

    @Override
    public void createUser(Context ctx, CreateUserRequest request, Observer<UserInfo> observer) {
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        if (!ctx.user.isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }
        Directory directory = securityStore.getDirectory();

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

        User user = new User(name, ctx.user);
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

        observer.complete(toUserInfo(user, ctx.user.isSuperuser()));
    }

    @Override
    public void getUser(Context ctx, GetUserRequest request, Observer<UserInfo> observer) {
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        String username = request.getName();
        User user = securityStore.getDirectory().getUser(username);
        if (user == null) {
            throw new NotFoundException();
        }
        observer.complete(toUserInfo(user, ctx.user.isSuperuser()));
    }

    @Override
    public void updateUser(Context ctx, UpdateUserRequest request, Observer<UserInfo> observer) {
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        if (!ctx.user.isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }
        String username = request.getName();
        Directory directory = securityStore.getDirectory();
        User user = directory.getUser(username);
        if (user == null) {
            throw new NotFoundException();
        }

        if (request.hasPassword() && user.isExternallyManaged()) {
            throw new BadRequestException("Cannot set the password of an externally managed user");
        }
        if (user.equals(ctx.user)) {
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
        if (request.hasRoleAssignment()) {
            user.setRoles(request.getRoleAssignment().getRolesList());
        }
        try {
            directory.updateUserProperties(user);

            if (request.hasPassword()) {
                directory.changePassword(user, request.getPassword().toCharArray());
            }
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }
        observer.complete(toUserInfo(user, ctx.user.isSuperuser()));
    }

    @Override
    public void deleteIdentity(Context ctx, DeleteIdentityRequest request, Observer<Empty> observer) {
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        if (!ctx.user.isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }
        Directory directory = securityStore.getDirectory();
        String username = request.getName();
        User user = directory.getUser(username);
        if (user == null) {
            throw new NotFoundException();
        }
        user.deleteIdentity(request.getProvider());
        try {
            directory.updateUserProperties(user);
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void getOwnUser(Context ctx, Empty request, Observer<UserInfo> observer) {
        User user = ctx.user;
        observer.complete(toUserInfo(user, true));
    }

    @Override
    public void listServiceAccounts(Context ctx, Empty request, Observer<ListServiceAccountsResponse> observer) {
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        if (!ctx.user.isSuperuser()) {
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
        observer.complete(responseb.build());
    }

    @Override
    public void getServiceAccount(Context ctx, GetServiceAccountRequest request,
            Observer<ServiceAccountInfo> observer) {
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        if (!ctx.user.isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }
        Directory directory = securityStore.getDirectory();
        String name = request.getName();
        ServiceAccount serviceAccount = directory.getServiceAccount(name);
        if (serviceAccount == null) {
            throw new NotFoundException();
        }
        observer.complete(toServiceAccountInfo(serviceAccount, true));
    }

    @Override
    public void deleteServiceAccount(Context ctx, DeleteServiceAccountRequest request,
            Observer<Empty> observer) {
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        if (!ctx.user.isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }
        Directory directory = securityStore.getDirectory();
        directory.deleteServiceAccount(request.getName());
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void createServiceAccount(Context ctx, CreateServiceAccountRequest request,
            Observer<CreateServiceAccountResponse> observer) {
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        if (!ctx.user.isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }

        if (!request.hasName()) {
            throw new BadRequestException("No name was specified");
        }
        Directory directory = securityStore.getDirectory();
        if (directory.getServiceAccount(request.getName()) != null) {
            throw new BadRequestException("An account named '" + request.getName() + "' already exists");
        }

        ServiceAccount serviceAccount = new ServiceAccount(request.getName(), ctx.user);
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
        observer.complete(responseb.build());
    }

    @Override
    public void listGroups(Context ctx, Empty request, Observer<ListGroupsResponse> observer) {
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        Directory directory = securityStore.getDirectory();
        List<Group> groups = directory.getGroups();
        Collections.sort(groups, (r1, r2) -> r1.getName().compareToIgnoreCase(r2.getName()));

        ListGroupsResponse.Builder responseb = ListGroupsResponse.newBuilder();
        for (Group group : groups) {
            GroupInfo groupInfo = toGroupInfo(group, true);
            responseb.addGroups(groupInfo);
        }
        observer.complete(responseb.build());
    }

    @Override
    public void getGroup(Context ctx, GetGroupRequest request, Observer<GroupInfo> observer) {
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        Directory directory = securityStore.getDirectory();
        String name = request.getName();
        Group group = directory.getGroup(name);
        if (group == null) {
            throw new NotFoundException();
        }
        observer.complete(toGroupInfo(group, true));
    }

    @Override
    public void createGroup(Context ctx, CreateGroupRequest request, Observer<GroupInfo> observer) {
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        if (!ctx.user.isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }
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
        observer.complete(toGroupInfo(group, true));
    }

    @Override
    public void updateGroup(Context ctx, UpdateGroupRequest request, Observer<GroupInfo> observer) {
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        if (!ctx.user.isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }
        String name = request.getName();
        Directory directory = securityStore.getDirectory();
        Group group = directory.getGroup(name);
        if (group == null) {
            throw new NotFoundException();
        }

        try {
            if (request.hasNewName() && !request.getNewName().equals(group.getName())) {
                String newName = request.getNewName().trim();
                if (newName.isEmpty()) {
                    throw new BadRequestException("Name must not be empty");
                } else if (directory.getGroup(newName) != null) {
                    throw new BadRequestException("Group '" + newName + "' already exists");
                }
                directory.renameGroup(group.getName(), request.getNewName());
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
        observer.complete(toGroupInfo(group, true));
    }

    @Override
    public void deleteGroup(Context ctx, DeleteGroupRequest request, Observer<GroupInfo> observer) {
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        if (!ctx.user.isSuperuser()) {
            throw new ForbiddenException("Insufficient privileges");
        }
        String name = request.getName();
        Directory directory = securityStore.getDirectory();
        Group group = directory.getGroup(name);
        if (group == null) {
            throw new NotFoundException();
        }

        try {
            directory.deleteGroup(group);
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }

        observer.complete(toGroupInfo(group, true));
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

            List<String> unsortedRoles = new ArrayList<>();
            unsortedRoles.addAll(user.getRoles());
            Collections.sort(unsortedRoles);
            for (String roleName : unsortedRoles) {
                Role role = directory.getRole(roleName);
                if (role != null) {
                    RoleInfo.Builder roleb = RoleInfo.newBuilder();
                    roleb.setName(roleName);
                    if (role.getDescription() != null) {
                        roleb.setDescription(role.getDescription());
                    }
                    userb.addRoles(roleb);
                }
            }

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

    private static RoleInfo toRoleInfo(Role role) {
        RoleInfo.Builder b = RoleInfo.newBuilder();
        b.setName(role.getName());
        if (role.getDescription() != null) {
            b.setDescription(role.getDescription());
        }

        List<SystemPrivilege> systemPrivileges = new ArrayList<>(role.getSystemPrivileges());
        Collections.sort(systemPrivileges, (p1, p2) -> p1.getName().compareTo(p2.getName()));
        for (SystemPrivilege privilege : systemPrivileges) {
            b.addSystemPrivileges(privilege.getName());
        }

        List<ObjectPrivilege> objectPrivileges = new ArrayList<>(role.getObjectPrivileges());
        Collections.sort(objectPrivileges, (p1, p2) -> {
            int rc = p1.getType().toString().compareTo(p2.getType().toString());
            return rc != 0 ? rc : p1.getObject().compareTo(p2.getObject());
        });
        for (ObjectPrivilege privilege : objectPrivileges) {
            b.addObjectPrivileges(ObjectPrivilegeInfo.newBuilder()
                    .setType(privilege.getType().toString())
                    .addObject(privilege.getObject()));
        }
        return b.build();
    }
}
