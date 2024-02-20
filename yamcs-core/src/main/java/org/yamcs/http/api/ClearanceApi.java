package org.yamcs.http.api;

import java.util.Collections;
import java.util.List;

import org.yamcs.YamcsServer;
import org.yamcs.api.Observer;
import org.yamcs.http.Context;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.audit.AuditLog;
import org.yamcs.protobuf.AbstractClearanceApi;
import org.yamcs.protobuf.ClearanceInfo;
import org.yamcs.protobuf.DeleteClearanceRequest;
import org.yamcs.protobuf.ListClearancesResponse;
import org.yamcs.protobuf.Mdb.SignificanceInfo.SignificanceLevelType;
import org.yamcs.protobuf.UpdateClearanceRequest;
import org.yamcs.security.ClearanceListener;
import org.yamcs.security.Directory;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SecurityStore;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.security.User;
import org.yamcs.security.protobuf.Clearance;
import org.yamcs.utils.TimeEncoding;

import com.google.protobuf.Empty;

public class ClearanceApi extends AbstractClearanceApi<Context> {

    public ClearanceApi(AuditLog auditLog) {
        auditLog.addPrivilegeChecker(getClass().getSimpleName(), user -> {
            return user.hasSystemPrivilege(SystemPrivilege.ControlCommandClearances);
        });
    }

    @Override
    public void listClearances(Context ctx, Empty request, Observer<ListClearancesResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlCommandClearances);

        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        List<User> users = securityStore.getDirectory().getUsers();
        Collections.sort(users, (u1, u2) -> u1.getName().compareToIgnoreCase(u2.getName()));

        ListClearancesResponse.Builder responseb = ListClearancesResponse.newBuilder();
        for (User user : users) {
            responseb.addClearances(toClearanceInfo(user));
        }
        observer.complete(responseb.build());
    }

    @Override
    public void updateClearance(Context ctx, UpdateClearanceRequest request, Observer<ClearanceInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlCommandClearances);

        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        String username = request.getUsername();
        Directory directory = securityStore.getDirectory();
        User user = directory.getUser(username);
        if (user == null) {
            throw new NotFoundException();
        }

        user.setClearance(Clearance.newBuilder()
                .setLevel(request.getLevel().toString())
                .setIssuedBy((int) ctx.user.getId())
                .setIssueTime(TimeEncoding.toProtobufTimestamp(TimeEncoding.getWallclockTime()))
                .build());

        directory.updateUserProperties(user);
        observer.complete(toClearanceInfo(user));
    }

    @Override
    public void deleteClearance(Context ctx, DeleteClearanceRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlCommandClearances);

        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();
        String username = request.getUsername();
        Directory directory = securityStore.getDirectory();
        User user = directory.getUser(username);
        if (user == null) {
            throw new NotFoundException();
        }

        user.setClearance(null);
        directory.updateUserProperties(user);
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void subscribeClearance(Context ctx, Empty request, Observer<ClearanceInfo> observer) {
        ClearanceListener listener = clearance -> observer.next(toClearanceInfo(ctx.user));
        ctx.user.addClearanceListener(listener);
        observer.setCancelHandler(() -> ctx.user.removeClearanceListener(listener));
        observer.next(toClearanceInfo(ctx.user));
    }

    private ClearanceInfo toClearanceInfo(User user) {
        SecurityStore securityStore = YamcsServer.getServer().getSecurityStore();

        ClearanceInfo.Builder clearanceb = ClearanceInfo.newBuilder()
                .setUsername(user.getName())
                .setHasCommandPrivileges(user.isSuperuser()
                        || !user.getObjectPrivileges(ObjectPrivilegeType.Command).isEmpty());

        Clearance clearance = user.getClearance();
        if (clearance != null) {
            clearanceb.setLevel(SignificanceLevelType.valueOf(clearance.getLevel()));
            clearanceb.setIssueTime(clearance.getIssueTime());

            Directory directory = securityStore.getDirectory();
            User issuedBy = directory.getUser(clearance.getIssuedBy());
            if (issuedBy != null) {
                clearanceb.setIssuedBy(issuedBy.getName());
            } else if (clearance.getIssuedBy() == securityStore.getGuestUser().getId()) {
                clearanceb.setIssuedBy(securityStore.getGuestUser().getName());
            } else if (clearance.getIssuedBy() == securityStore.getSystemUser().getId()) {
                clearanceb.setIssuedBy(securityStore.getSystemUser().getName());
            }
        }
        return clearanceb.build();
    }
}
