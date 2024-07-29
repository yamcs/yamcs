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
import org.yamcs.http.Context;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.audit.AuditLog;
import org.yamcs.http.auth.TokenStore;
import org.yamcs.protobuf.AbstractIamApi;
import org.yamcs.protobuf.CreateGroupRequest;
import org.yamcs.protobuf.CreateServiceAccountRequest;
import org.yamcs.protobuf.CreateServiceAccountResponse;
import org.yamcs.protobuf.CreateUserRequest;
import org.yamcs.protobuf.DeleteGroupRequest;
import org.yamcs.protobuf.DeleteIdentityRequest;
import org.yamcs.protobuf.DeleteRoleAssignmentRequest;
import org.yamcs.protobuf.DeleteServiceAccountRequest;
import org.yamcs.protobuf.DeleteUserRequest;
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
import org.yamcs.protobuf.Mdb.SignificanceInfo.SignificanceLevelType;
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

    private TokenStore tokenStore;

    public IamApi(AuditLog auditLog, TokenStore tokenStore) {
        this.tokenStore = tokenStore;
        auditLog.addPrivilegeChecker(getClass().getSimpleName(), user -> {
            return user.hasSystemPrivilege(SystemPrivilege.ControlAccess);
        });
    }

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
        ctx.checkSystemPrivilege(SystemPrivilege.ControlAccess);
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        Directory directory = securityStore.getDirectory();
        String username = request.getName();
        User user = directory.getUser(username);
        if (user == null) {
            throw new NotFoundException();
        }
        user.deleteRole(request.getRole());
        directory.updateUserProperties(user);
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
        Directory directory = securityStore.getDirectory();
        List<User> users = directory.getUsers();
        Collections.sort(users, (u1, u2) -> u1.getName().compareToIgnoreCase(u2.getName()));

        var sensitiveDetails = ctx.user.hasSystemPrivilege(SystemPrivilege.ControlAccess);
        ListUsersResponse.Builder responseb = ListUsersResponse.newBuilder();
        for (User user : users) {
            UserInfo userb = toUserInfo(user, sensitiveDetails, directory);
            responseb.addUsers(userb);
        }
        observer.complete(responseb.build());
    }

    @Override
    public void createUser(Context ctx, CreateUserRequest request, Observer<UserInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlAccess);
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
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

        observer.complete(toUserInfo(user, true, directory));
    }

    @Override
    public void getUser(Context ctx, GetUserRequest request, Observer<UserInfo> observer) {
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        Directory directory = securityStore.getDirectory();
        String username = request.getName();
        User user = directory.getUser(username);
        if (user == null) {
            throw new NotFoundException();
        }
        var sensitiveDetails = ctx.user.hasSystemPrivilege(SystemPrivilege.ControlAccess);
        observer.complete(toUserInfo(user, sensitiveDetails, directory));
    }

    @Override
    public void updateUser(Context ctx, UpdateUserRequest request, Observer<UserInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlAccess);
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();

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
        directory.updateUserProperties(user);

        if (request.hasPassword()) {
            directory.changePassword(user, request.getPassword().toCharArray());
        }

        var sensitiveDetails = ctx.user.hasSystemPrivilege(SystemPrivilege.ControlAccess);
        observer.complete(toUserInfo(user, sensitiveDetails, directory));
    }

    @Override
    public void deleteIdentity(Context ctx, DeleteIdentityRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlAccess);
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        Directory directory = securityStore.getDirectory();
        String username = request.getName();
        User user = directory.getUser(username);
        if (user == null) {
            throw new NotFoundException();
        }
        user.deleteIdentity(request.getProvider());
        directory.updateUserProperties(user);
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void getOwnUser(Context ctx, Empty request, Observer<UserInfo> observer) {
        User user = ctx.user;
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        Directory directory = securityStore.getDirectory();
        observer.complete(toUserInfo(user, true, directory));
    }

    @Override
    public void listServiceAccounts(Context ctx, Empty request, Observer<ListServiceAccountsResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlAccess);
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        Directory directory = securityStore.getDirectory();
        List<ServiceAccount> serviceAccounts = directory.getServiceAccounts();
        Collections.sort(serviceAccounts, (r1, r2) -> r1.getName().compareToIgnoreCase(r2.getName()));

        ListServiceAccountsResponse.Builder responseb = ListServiceAccountsResponse.newBuilder();
        for (ServiceAccount serviceAccount : serviceAccounts) {
            ServiceAccountInfo serviceAccountInfo = toServiceAccountInfo(serviceAccount, false, directory);
            responseb.addServiceAccounts(serviceAccountInfo);
        }
        observer.complete(responseb.build());
    }

    @Override
    public void getServiceAccount(Context ctx, GetServiceAccountRequest request,
            Observer<ServiceAccountInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlAccess);
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        Directory directory = securityStore.getDirectory();
        String name = request.getName();
        ServiceAccount serviceAccount = directory.getServiceAccount(name);
        if (serviceAccount == null) {
            throw new NotFoundException();
        }
        observer.complete(toServiceAccountInfo(serviceAccount, true, directory));
    }

    @Override
    public void deleteServiceAccount(Context ctx, DeleteServiceAccountRequest request,
            Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlAccess);
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        Directory directory = securityStore.getDirectory();
        ServiceAccount serviceAccount = directory.getServiceAccount(request.getName());
        if (serviceAccount == null) {
            throw new NotFoundException();
        }
        directory.deleteServiceAccount(serviceAccount);
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void deleteUser(Context ctx, DeleteUserRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlAccess);
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        Directory directory = securityStore.getDirectory();
        String username = request.getName();
        User user = directory.getUser(username);
        if (user == null) {
            throw new NotFoundException();
        }
        try {
            tokenStore.forgetUser(user.getName());
            directory.deleteUser(user);
            observer.complete(Empty.getDefaultInstance());
        } catch (IOException e) {
            observer.completeExceptionally(e);
        }
    }

    @Override
    public void createServiceAccount(Context ctx, CreateServiceAccountRequest request,
            Observer<CreateServiceAccountResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlAccess);
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();

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
            GroupInfo groupInfo = toGroupInfo(group, true, directory);
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
        observer.complete(toGroupInfo(group, true, directory));
    }

    @Override
    public void createGroup(Context ctx, CreateGroupRequest request, Observer<GroupInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlAccess);
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();

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
            long memberId = directory.getUser(username).getId();
            group.addMember(memberId);
        }
        for (String serviceAccountName : request.getServiceAccountsList()) {
            long memberId = directory.getServiceAccount(serviceAccountName).getId();
            group.addMember(memberId);
        }

        directory.addGroup(group);
        observer.complete(toGroupInfo(group, true, directory));
    }

    @Override
    public void updateGroup(Context ctx, UpdateGroupRequest request, Observer<GroupInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlAccess);
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();

        String name = request.getName();
        Directory directory = securityStore.getDirectory();
        Group group = directory.getGroup(name);
        if (group == null) {
            throw new NotFoundException();
        }

        if (request.hasNewName() && !request.getNewName().equals(group.getName())) {
            String newName = request.getNewName().trim();
            if (newName.isEmpty()) {
                throw new BadRequestException("Name must not be empty");
            } else if (directory.getGroup(newName) != null) {
                throw new BadRequestException("Group '" + newName + "' already exists");
            }
            directory.renameGroup(group.getName(), newName);
            group.setName(newName);
        }
        if (request.hasDescription()) {
            group.setDescription(request.getDescription());
        }
        if (request.hasMemberInfo()) {
            Set<Long> memberIds = new HashSet<>();
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

        observer.complete(toGroupInfo(group, true, directory));
    }

    @Override
    public void deleteGroup(Context ctx, DeleteGroupRequest request, Observer<GroupInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlAccess);
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        String name = request.getName();
        Directory directory = securityStore.getDirectory();
        Group group = directory.getGroup(name);
        if (group == null) {
            throw new NotFoundException();
        }
        directory.deleteGroup(group);
        observer.complete(toGroupInfo(group, true, directory));
    }

    public static UserInfo toUserInfo(User user, boolean sensitiveDetails, Directory directory) {
        UserInfo.Builder userb;
        userb = UserInfo.newBuilder();
        userb.setName(user.getName());
        userb.setActive(user.isActive());
        userb.setSuperuser(user.isSuperuser());
        if (user.getDisplayName() != null) {
            userb.setDisplayName(user.getDisplayName());
        }
        if (user.getEmail() != null) {
            userb.setEmail(user.getEmail());
        }
        if (sensitiveDetails) {
            User createdBy = directory.getUser(user.getCreatedBy());
            if (createdBy != null) {
                userb.setCreatedBy(toUserInfo(createdBy, false, directory));
            }
            userb.setCreationTime(TimeEncoding.toProtobufTimestamp(user.getCreationTime()));
            if (user.getConfirmationTime() != TimeEncoding.INVALID_INSTANT) {
                userb.setConfirmationTime(TimeEncoding.toProtobufTimestamp(user.getConfirmationTime()));
            }
            if (user.getLastLoginTime() != TimeEncoding.INVALID_INSTANT) {
                userb.setLastLoginTime(TimeEncoding.toProtobufTimestamp(user.getLastLoginTime()));
            }

            if (user.getClearance() != null) {
                SignificanceLevelType level = SignificanceLevelType.valueOf(user.getClearance().getLevel());
                userb.setClearance(level);
            }

            List<String> unsortedSystemPrivileges = new ArrayList<>();
            for (SystemPrivilege privilege : user.getSystemPrivileges()) {
                unsortedSystemPrivileges.add(privilege.getName());
            }
            Collections.sort(unsortedSystemPrivileges);
            userb.addAllSystemPrivileges(unsortedSystemPrivileges);

            List<ObjectPrivilegeInfo> unsortedObjectPrivileges = new ArrayList<>();
            for (Entry<ObjectPrivilegeType, Set<ObjectPrivilege>> privilege : user.getObjectPrivileges().entrySet()) {
                ObjectPrivilegeInfo.Builder infob = ObjectPrivilegeInfo.newBuilder();
                infob.setType(privilege.getKey().toString());
                for (ObjectPrivilege objectPrivilege : privilege.getValue()) {
                    infob.addObjects(objectPrivilege.getObject());
                }
                unsortedObjectPrivileges.add(infob.build());
            }
            Collections.sort(unsortedObjectPrivileges, (p1, p2) -> p1.getType().compareTo(p2.getType()));
            userb.addAllObjectPrivileges(unsortedObjectPrivileges);

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
                GroupInfo groupInfo = toGroupInfo(group, false, directory);
                userb.addGroups(groupInfo);
            }
        }

        return userb.build();
    }

    private static GroupInfo toGroupInfo(Group group, boolean addMembers, Directory directory) {
        GroupInfo.Builder groupInfob = GroupInfo.newBuilder();
        groupInfob.setName(group.getName());
        if (group.getDescription() != null) {
            groupInfob.setDescription(group.getDescription());
        }
        if (addMembers) {
            for (long memberId : group.getMembers()) {
                User user = directory.getUser(memberId);
                UserInfo userInfo = toUserInfo(user, false, directory);
                groupInfob.addUsers(userInfo);
            }
        }
        return groupInfob.build();
    }

    private static ServiceAccountInfo toServiceAccountInfo(ServiceAccount serviceAccount, boolean details,
            Directory directory) {
        ServiceAccountInfo.Builder b = ServiceAccountInfo.newBuilder();
        b.setName(serviceAccount.getName());
        b.setActive(serviceAccount.isActive());
        if (serviceAccount.getDisplayName() != null) {
            b.setDisplayName(serviceAccount.getDisplayName());
        }
        if (details) {
            User createdBy = directory.getUser(serviceAccount.getCreatedBy());
            if (createdBy != null) {
                b.setCreatedBy(toUserInfo(createdBy, false, directory));
                b.setCreationTime(TimeEncoding.toProtobufTimestamp(createdBy.getCreationTime()));
            }
        }
        return b.build();
    }

    private static RoleInfo toRoleInfo(Role role) {
        RoleInfo.Builder b = RoleInfo.newBuilder();
        b.setName(role.getName());
        b.setDefault(role.isDefaultRole());
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
                    .addObjects(privilege.getObject()));
        }
        return b.build();
    }
}
