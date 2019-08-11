package org.yamcs.http.api;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.yamcs.YamcsServer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.protobuf.CreateGroupRequest;
import org.yamcs.protobuf.GroupInfo;
import org.yamcs.protobuf.ListGroupsResponse;
import org.yamcs.protobuf.UserInfo;
import org.yamcs.security.Directory;
import org.yamcs.security.Group;
import org.yamcs.security.User;

/**
 * Handles incoming requests related to groups
 */
public class GroupRestHandler extends RestHandler {

    @Route(rpc = "IAM.ListGroups")
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

    @Route(rpc = "IAM.GetGroup")
    public void getGroup(RestRequest req) throws HttpException {
        Directory directory = securityStore.getDirectory();
        String name = req.getRouteParam("name");
        Group group = directory.getGroup(name);
        if (group == null) {
            throw new NotFoundException(req);
        }
        completeOK(req, toGroupInfo(group, true));
    }

    @Route(rpc = "IAM.CreateGroup")
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
        for (String username : request.getMembersList()) {
            int memberId = directory.getUser(username).getId();
            group.addMember(memberId);
        }

        try {
            directory.addGroup(group);
        } catch (IOException e) {
            throw new InternalServerErrorException(e);
        }
        completeOK(req, toGroupInfo(group, true));
    }

    public static GroupInfo toGroupInfo(Group group, boolean addMembers) {
        Directory directory = YamcsServer.getServer().getSecurityStore().getDirectory();

        GroupInfo.Builder groupInfob = GroupInfo.newBuilder();
        groupInfob.setName(group.getName());
        if (group.getDescription() != null) {
            groupInfob.setDescription(group.getDescription());
        }
        if (addMembers) {
            for (int memberId : group.getMembers()) {
                User member = directory.getUser(memberId);
                UserInfo memberInfo = UserRestHandler.toUserInfo(member, false);
                groupInfob.addMembers(memberInfo);
            }
        }
        return groupInfob.build();
    }
}
