package org.yamcs.http.api;

import java.util.UUID;
import java.util.function.Consumer;

import org.yamcs.YamcsServer;
import org.yamcs.activities.triggers.TriggerFiring;
import org.yamcs.activities.triggers.TriggerService;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.NotFoundException;
import org.yamcs.protobuf.activities.AbstractActivityTriggersApi;
import org.yamcs.protobuf.activities.ConfirmTriggerFiringRequest;
import org.yamcs.protobuf.activities.CreateTriggerRequest;
import org.yamcs.protobuf.activities.DeleteTriggerRequest;
import org.yamcs.protobuf.activities.DisableAllTriggersRequest;
import org.yamcs.protobuf.activities.DisableTriggerRequest;
import org.yamcs.protobuf.activities.EnableAllTriggersRequest;
import org.yamcs.protobuf.activities.EnableTriggerRequest;
import org.yamcs.protobuf.activities.GetTriggerRequest;
import org.yamcs.protobuf.activities.GetTriggerServiceStatusRequest;
import org.yamcs.protobuf.activities.ListTriggerFiringsRequest;
import org.yamcs.protobuf.activities.ListTriggerFiringsResponse;
import org.yamcs.protobuf.activities.ListTriggersRequest;
import org.yamcs.protobuf.activities.ListTriggersResponse;
import org.yamcs.protobuf.activities.RejectTriggerFiringRequest;
import org.yamcs.protobuf.activities.SubscribeTriggerFiringsRequest;
import org.yamcs.protobuf.activities.TestTriggerRequest;
import org.yamcs.protobuf.activities.TestTriggerResponse;
import org.yamcs.protobuf.activities.TriggerFiringInfo;
import org.yamcs.protobuf.activities.TriggerInfo;
import org.yamcs.protobuf.activities.TriggerServiceStatus;
import org.yamcs.protobuf.activities.UpdateTriggerRequest;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.utils.TimeEncoding;

import com.google.protobuf.Empty;

public class ActivityTriggersApi extends AbstractActivityTriggersApi<Context> {

    @Override
    public void listTriggers(Context ctx, ListTriggersRequest request, Observer<ListTriggersResponse> observer) {
        ctx.checkAnyOfSystemPrivileges(SystemPrivilege.ReadActivityTriggers, SystemPrivilege.ControlActivityTriggers);
        var service = verifyService(request.getInstance());
        var responseb = ListTriggersResponse.newBuilder()
                .addAllTriggers(service.listTriggers());
        observer.complete(responseb.build());
    }

    @Override
    public void getTrigger(Context ctx, GetTriggerRequest request, Observer<TriggerInfo> observer) {
        ctx.checkAnyOfSystemPrivileges(SystemPrivilege.ReadActivityTriggers, SystemPrivilege.ControlActivityTriggers);
        var service = verifyService(request.getInstance());
        observer.complete(verifyTrigger(service, request.getTrigger()));
    }

    @Override
    public void createTrigger(Context ctx, CreateTriggerRequest request, Observer<TriggerInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlActivityTriggers);
        ctx.checkSystemPrivilege(SystemPrivilege.ControlActivities);
        var service = verifyService(request.getInstance());
        if (!request.hasTrigger()) {
            throw new BadRequestException("No trigger definition provided");
        }
        try {
            observer.complete(service.createTrigger(request.getTrigger(), ctx.user));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public void updateTrigger(Context ctx, UpdateTriggerRequest request, Observer<TriggerInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlActivityTriggers);
        ctx.checkSystemPrivilege(SystemPrivilege.ControlActivities);
        var service = verifyService(request.getInstance());
        if (!request.hasTrigger()) {
            throw new BadRequestException("No trigger definition provided");
        }
        TriggerInfo trigger;
        try {
            trigger = service.updateTrigger(request.getId(), request.getTrigger(), ctx.user);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        if (trigger == null) {
            throw new NotFoundException("Trigger '" + request.getId() + "' not found");
        }
        observer.complete(trigger);
    }

    @Override
    public void deleteTrigger(Context ctx, DeleteTriggerRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlActivityTriggers);
        var service = verifyService(request.getInstance());
        boolean deleted;
        try {
            deleted = service.deleteTrigger(request.getTrigger(), ctx.user);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        if (!deleted) {
            throw new NotFoundException("Trigger '" + request.getTrigger() + "' not found");
        }
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void enableTrigger(Context ctx, EnableTriggerRequest request, Observer<TriggerInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlActivityTriggers);
        var service = verifyService(request.getInstance());
        var trigger = service.setTriggerEnabled(request.getTrigger(), true, ctx.user);
        if (trigger == null) {
            throw new NotFoundException("Trigger '" + request.getTrigger() + "' not found");
        }
        observer.complete(trigger);
    }

    @Override
    public void disableTrigger(Context ctx, DisableTriggerRequest request, Observer<TriggerInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlActivityTriggers);
        var service = verifyService(request.getInstance());
        var trigger = service.setTriggerEnabled(request.getTrigger(), false, ctx.user);
        if (trigger == null) {
            throw new NotFoundException("Trigger '" + request.getTrigger() + "' not found");
        }
        observer.complete(trigger);
    }

    @Override
    public void enableAllTriggers(Context ctx, EnableAllTriggersRequest request,
            Observer<TriggerServiceStatus> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlActivityTriggers);
        var service = verifyService(request.getInstance());
        service.setGlobalEnabled(true, ctx.user);
        observer.complete(toStatus(service));
    }

    @Override
    public void disableAllTriggers(Context ctx, DisableAllTriggersRequest request,
            Observer<TriggerServiceStatus> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlActivityTriggers);
        var service = verifyService(request.getInstance());
        service.setGlobalEnabled(false, ctx.user);
        observer.complete(toStatus(service));
    }

    @Override
    public void getTriggerServiceStatus(Context ctx, GetTriggerServiceStatusRequest request,
            Observer<TriggerServiceStatus> observer) {
        ctx.checkAnyOfSystemPrivileges(SystemPrivilege.ReadActivityTriggers, SystemPrivilege.ControlActivityTriggers);
        var service = verifyService(request.getInstance());
        observer.complete(toStatus(service));
    }

    @Override
    public void testTrigger(Context ctx, TestTriggerRequest request, Observer<TestTriggerResponse> observer) {
        ctx.checkAnyOfSystemPrivileges(SystemPrivilege.ReadActivityTriggers, SystemPrivilege.ControlActivityTriggers);
        var service = verifyService(request.getInstance());
        var sampleContext = request.hasContext() ? GpbWellKnownHelper.toJava(request.getContext()) : null;

        var responseb = TestTriggerResponse.newBuilder();
        try {
            var activity = service.testTrigger(request.getTrigger(), sampleContext);
            if (activity == null) {
                throw new NotFoundException("Trigger '" + request.getTrigger() + "' not found");
            }
            responseb.setActivity(activity);
        } catch (IllegalArgumentException e) {
            responseb.setError(e.getMessage());
        }
        observer.complete(responseb.build());
    }

    @Override
    public void listTriggerFirings(Context ctx, ListTriggerFiringsRequest request,
            Observer<ListTriggerFiringsResponse> observer) {
        ctx.checkAnyOfSystemPrivileges(SystemPrivilege.ReadActivityTriggers, SystemPrivilege.ControlActivityTriggers);
        var service = verifyService(request.getInstance());
        var trigger = request.hasTrigger() ? request.getTrigger() : null;
        var start = request.hasStart() ? TimeEncoding.fromProtobufTimestamp(request.getStart()) : null;
        var stop = request.hasStop() ? TimeEncoding.fromProtobufTimestamp(request.getStop()) : null;
        var limit = request.hasLimit() ? request.getLimit() : 100;

        var responseb = ListTriggerFiringsResponse.newBuilder();
        for (var firing : service.listFirings(trigger, request.getOutcomeList(), start, stop, limit)) {
            responseb.addFirings(firing.toProto());
        }
        observer.complete(responseb.build());
    }

    @Override
    public void confirmTriggerFiring(Context ctx, ConfirmTriggerFiringRequest request,
            Observer<TriggerFiringInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlActivities);
        var service = verifyService(request.getInstance());
        var firing = service.confirmFiring(verifyFiringId(request.getFiring()), ctx.user);
        if (firing == null) {
            throw new NotFoundException("No pending firing '" + request.getFiring() + "'");
        }
        observer.complete(firing.toProto());
    }

    @Override
    public void rejectTriggerFiring(Context ctx, RejectTriggerFiringRequest request,
            Observer<TriggerFiringInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlActivities);
        var service = verifyService(request.getInstance());
        var firing = service.rejectFiring(verifyFiringId(request.getFiring()), ctx.user);
        if (firing == null) {
            throw new NotFoundException("No pending firing '" + request.getFiring() + "'");
        }
        observer.complete(firing.toProto());
    }

    @Override
    public void subscribeTriggerFirings(Context ctx, SubscribeTriggerFiringsRequest request,
            Observer<TriggerFiringInfo> observer) {
        ctx.checkAnyOfSystemPrivileges(SystemPrivilege.ReadActivityTriggers, SystemPrivilege.ControlActivityTriggers);
        var service = verifyService(request.getInstance());
        Consumer<TriggerFiring> listener = firing -> observer.next(firing.toProto());
        observer.setCancelHandler(() -> service.removeFiringListener(listener));
        service.addFiringListener(listener);
    }

    private static TriggerServiceStatus toStatus(TriggerService service) {
        return TriggerServiceStatus.newBuilder()
                .setEnabled(service.isGlobalEnabled())
                .setPendingConfirmations(service.getPendingConfirmationCount())
                .build();
    }

    private static TriggerInfo verifyTrigger(TriggerService service, String idOrName) {
        var trigger = service.getTrigger(idOrName);
        if (trigger == null) {
            throw new NotFoundException("Trigger '" + idOrName + "' not found");
        }
        return trigger;
    }

    private static UUID verifyFiringId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid firing identifier '" + id + "'");
        }
    }

    private static TriggerService verifyService(String yamcsInstance) {
        var instance = InstancesApi.verifyInstance(yamcsInstance);
        var services = YamcsServer.getServer().getInstance(instance).getServices(TriggerService.class);
        if (services.isEmpty()) {
            throw new NotFoundException("No trigger service configured for instance '" + instance + "'");
        }
        return services.get(0);
    }
}
