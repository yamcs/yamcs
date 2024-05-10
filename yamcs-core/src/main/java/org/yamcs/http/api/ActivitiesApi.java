package org.yamcs.http.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.yamcs.YamcsServer;
import org.yamcs.activities.Activity;
import org.yamcs.activities.ActivityDb;
import org.yamcs.activities.ActivityListener;
import org.yamcs.activities.ActivityLog;
import org.yamcs.activities.ActivityLogListener;
import org.yamcs.activities.ActivityService;
import org.yamcs.activities.ScriptExecutor;
import org.yamcs.api.Observer;
import org.yamcs.client.utils.WellKnownTypes;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.NotFoundException;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.activities.AbstractActivitiesApi;
import org.yamcs.protobuf.activities.ActivityInfo;
import org.yamcs.protobuf.activities.ActivityLogInfo;
import org.yamcs.protobuf.activities.CancelActivityRequest;
import org.yamcs.protobuf.activities.CompleteManualActivityRequest;
import org.yamcs.protobuf.activities.ExecutorInfo;
import org.yamcs.protobuf.activities.GetActivityLogRequest;
import org.yamcs.protobuf.activities.GetActivityLogResponse;
import org.yamcs.protobuf.activities.GetActivityRequest;
import org.yamcs.protobuf.activities.GlobalActivityStatus;
import org.yamcs.protobuf.activities.ListActivitiesRequest;
import org.yamcs.protobuf.activities.ListActivitiesResponse;
import org.yamcs.protobuf.activities.ListExecutorsRequest;
import org.yamcs.protobuf.activities.ListExecutorsResponse;
import org.yamcs.protobuf.activities.ListScriptsRequest;
import org.yamcs.protobuf.activities.ListScriptsResponse;
import org.yamcs.protobuf.activities.StartActivityRequest;
import org.yamcs.protobuf.activities.SubscribeActivitiesRequest;
import org.yamcs.protobuf.activities.SubscribeActivityLogRequest;
import org.yamcs.protobuf.activities.SubscribeGlobalStatusRequest;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.timeline.TimelineService;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.SqlBuilder;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;

import com.google.gson.Gson;
import com.google.protobuf.Struct;

public class ActivitiesApi extends AbstractActivitiesApi<Context> {

    private Log log = new Log(ActivitiesApi.class);

    @Override
    public void listExecutors(Context ctx, ListExecutorsRequest request, Observer<ListExecutorsResponse> observer) {
        ctx.checkAnyOfSystemPrivileges(SystemPrivilege.ReadActivities, SystemPrivilege.ControlActivities);
        var activityService = verifyService(request.getInstance());

        var responseb = ListExecutorsResponse.newBuilder();

        var sortedExecutors = new ArrayList<>(activityService.getExecutors());
        Collections.sort(sortedExecutors, (a, b) -> a.getActivityType().compareTo(b.getActivityType()));

        for (var executor : sortedExecutors) {
            var executorb = ExecutorInfo.newBuilder()
                    .setType(executor.getActivityType())
                    .setDisplayName(executor.getDisplayName());
            if (executor.getIcon() != null) {
                executorb.setIcon(executor.getIcon());
            }
            if (executor.getDescription() != null) {
                executorb.setDescription(executor.getDescription());
            }
            responseb.addExecutors(executorb);
        }
        observer.complete(responseb.build());
    }

    @Override
    public void listActivities(Context ctx, ListActivitiesRequest request, Observer<ListActivitiesResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadActivities);
        var instance = InstancesApi.verifyInstance(request.getInstance());

        var limit = request.hasLimit() ? request.getLimit() : 200;
        boolean desc = !request.getOrder().equals("asc");

        ActivityPageToken nextToken = null;
        if (request.hasNext()) {
            var next = request.getNext();
            nextToken = ActivityPageToken.decode(next);
        }

        var sqlb = new SqlBuilder(ActivityDb.TABLE_NAME);

        if (request.hasStart()) {
            sqlb.whereColAfterOrEqual("start", request.getStart());
        }
        if (request.hasStop()) {
            sqlb.whereColBefore("start", request.getStop());
        }
        if (request.getStatusCount() > 0) {
            sqlb.whereColIn("status", request.getStatusList());
        }
        if (request.getTypeCount() > 0) {
            sqlb.whereColIn("type", request.getTypeList());
        }
        if (request.hasQ()) {
            sqlb.where("detail like ?", "%" + request.getQ() + "%");
        }
        if (nextToken != null) {
            if (desc) {
                sqlb.where("(start < ? or (start = ? and seq < ?))",
                        nextToken.start, nextToken.start, nextToken.seq);
            } else {
                sqlb.where("(start > ? or (start = ? and seq > ?))",
                        nextToken.start, nextToken.start, nextToken.seq);
            }
        }
        sqlb.descend(desc);

        var responseb = ListActivitiesResponse.newBuilder();

        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {
            Activity last;
            int count;

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                var activity = new Activity(tuple);
                count++;
                if (count <= limit) {
                    responseb.addActivities(toActivityInfo(activity));
                    last = activity;
                } else {
                    stream.close();
                }
            }

            @Override
            public void streamClosed(Stream stream) {
                if (count > limit) {
                    var token = new ActivityPageToken(last.getStart(), last.getSeq());
                    responseb.setContinuationToken(token.encodeAsString());
                }
                observer.complete(responseb.build());
            }
        });
    }

    @Override
    public void getActivity(Context ctx, GetActivityRequest request, Observer<ActivityInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadActivities);
        var activityService = verifyService(request.getInstance());
        var activityId = verifyActivityId(request.getActivity());
        var activity = activityService.getActivity(activityId);
        if (activity == null) {
            throw new BadRequestException("Unknown activity");
        } else {
            observer.next(toActivityInfo(activity));
        }
    }

    @Override
    public void getActivityLog(Context ctx, GetActivityLogRequest request, Observer<GetActivityLogResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadActivities);
        var activityService = verifyService(request.getInstance());
        var activityId = verifyActivityId(request.getActivity());
        var logEntries = activityService.getActivityLogDb().getLogEntries(activityId);

        var responseb = GetActivityLogResponse.newBuilder();
        logEntries.forEach(log -> responseb.addLogs(toActivityLogInfo(log)));
        observer.next(responseb.build());
    }

    @Override
    public void startActivity(Context ctx, StartActivityRequest request, Observer<ActivityInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlActivities);
        var activityService = verifyService(request.getInstance());

        var def = request.getActivityDefinition();
        var type = def.getType();
        var args = GpbWellKnownHelper.toJava(def.getArgs());
        var comment = def.hasComment() ? def.getComment() : null;

        var activity = activityService.prepareActivity(type, args, ctx.user, comment);
        activityService.startActivity(activity, ctx.user);
        observer.next(toActivityInfo(activity));
    }

    @Override
    public void cancelActivity(Context ctx, CancelActivityRequest request, Observer<ActivityInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlActivities);
        var activityService = verifyService(request.getInstance());
        var activityId = verifyActivityId(request.getActivity());

        var activity = activityService.cancelActivity(activityId, ctx.user);
        if (activity == null) {
            throw new BadRequestException("Unknown activity '" + activityId + "'");
        } else {
            observer.next(toActivityInfo(activity));
        }
    }

    @Override
    public void completeManualActivity(Context ctx, CompleteManualActivityRequest request,
            Observer<ActivityInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlActivities);
        var activityService = verifyService(request.getInstance());
        var activityId = verifyActivityId(request.getActivity());
        var failureReason = request.hasFailureReason() ? request.getFailureReason() : null;

        Activity activity;
        try {
            activity = activityService.completeManualActivity(activityId, failureReason, ctx.user);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        if (activity == null) {
            throw new BadRequestException("Unknown activity '" + activityId + "'");
        } else {
            observer.next(toActivityInfo(activity));
        }
    }

    @Override
    public void subscribeGlobalStatus(Context ctx, SubscribeGlobalStatusRequest request,
            Observer<GlobalActivityStatus> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadActivities);
        var activityService = verifyService(request.getInstance());

        var oldStatusRef = new AtomicReference<GlobalActivityStatus>();
        var future = YamcsServer.getServer().getThreadPoolExecutor().scheduleAtFixedRate(() -> {
            var ongoingCount = activityService.getOngoingActivities().size();

            var status = GlobalActivityStatus.newBuilder()
                    .setOngoingCount(ongoingCount)
                    .build();

            var oldStatus = oldStatusRef.get();
            if (!status.equals(oldStatus)) {
                observer.next(status);
                oldStatusRef.set(status);
            }
        }, 0, 1, TimeUnit.SECONDS);
        observer.setCancelHandler(() -> future.cancel(false));
    }

    @Override
    public void subscribeActivities(Context ctx, SubscribeActivitiesRequest request,
            Observer<ActivityInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadActivities);
        var activityService = verifyService(request.getInstance());
        var activityListener = (ActivityListener) activity -> observer.next(toActivityInfo(activity));
        observer.setCancelHandler(() -> activityService.removeActivityListener(activityListener));
        activityService.addActivityListener(activityListener);
    }

    @Override
    public void subscribeActivityLog(Context ctx, SubscribeActivityLogRequest request,
            Observer<ActivityLogInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadActivities);
        var activityService = verifyService(request.getInstance());
        var logListener = (ActivityLogListener) (activity, log) -> {
            if (!request.hasActivity() || request.getActivity().equals(log.getActivityId().toString())) {
                observer.next(toActivityLogInfo(log));
            }
        };
        observer.setCancelHandler(() -> activityService.removeActivityLogListener(logListener));
        activityService.addActivityLogListener(logListener);
    }

    @Override
    public void listScripts(Context ctx, ListScriptsRequest request, Observer<ListScriptsResponse> observer) {
        ctx.checkAnyOfSystemPrivileges(SystemPrivilege.ReadActivities, SystemPrivilege.ControlActivities);
        var activityService = verifyService(request.getInstance());
        var scriptExecutor = (ScriptExecutor) activityService.getExecutor("SCRIPT");
        try {
            var responseb = ListScriptsResponse.newBuilder()
                    .addAllScripts(scriptExecutor.getScripts());
            observer.next(responseb.build());
        } catch (IOException e) {
            observer.completeExceptionally(e);
        }
    }

    private static ActivityInfo toActivityInfo(Activity activity) {
        var activityb = ActivityInfo.newBuilder()
                .setStart(TimeEncoding.toProtobufTimestamp(activity.getStart()))
                .setSeq(activity.getSeq())
                .setId(activity.getId().toString())
                .setType(activity.getType())
                .setStartedBy(activity.getStartedBy());

        var args = activity.getArgs();
        if (args != null) {
            activityb.setArgs(WellKnownTypes.toStruct(args));
        } else {
            activityb.setArgs(Struct.getDefaultInstance());
        }

        activityb.setStatus(org.yamcs.protobuf.activities.ActivityStatus.valueOf(
                activity.getStatus().name()));

        if (activity.getDetail() != null) {
            activityb.setDetail(activity.getDetail());
        }
        if (activity.getStop() != TimeEncoding.INVALID_INSTANT) {
            activityb.setStop(TimeEncoding.toProtobufTimestamp(activity.getStop()));
        }
        if (activity.getFailureReason() != null) {
            activityb.setFailureReason(activity.getFailureReason());
        }
        if (activity.getStoppedBy() != null) {
            activityb.setStoppedBy(activity.getStoppedBy());
        }

        return activityb.build();
    }

    private static ActivityLogInfo toActivityLogInfo(ActivityLog log) {
        var logb = ActivityLogInfo.newBuilder()
                .setTime(TimeEncoding.toProtobufTimestamp(log.getTime()))
                .setSource(log.getSource())
                .setLevel(org.yamcs.protobuf.activities.ActivityLogLevel.valueOf(
                        log.getLevel().name()))
                .setMessage(log.getMessage());
        return logb.build();
    }

    private ActivityService verifyService(String yamcsInstance) {
        String instance = InstancesApi.verifyInstance(yamcsInstance);

        var services = YamcsServer.getServer().getInstance(instance)
                .getServices(TimelineService.class);
        if (services.isEmpty()) {
            throw new NotFoundException("No activity service found");
        } else {
            if (services.size() > 1) {
                log.warn("Multiple activity services found but only one supported");
            }
            return services.get(0).getActivityService();
        }
    }

    private static UUID verifyActivityId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid activity identifier '" + id + "'");
        }
    }

    /**
     * Stateless continuation token for paged requests on the activities table
     */
    private static class ActivityPageToken {

        long start;
        int seq;

        ActivityPageToken(long start, int seq) {
            this.start = start;
            this.seq = seq;
        }

        static ActivityPageToken decode(String encoded) {
            String decoded = new String(Base64.getUrlDecoder().decode(encoded));
            return new Gson().fromJson(decoded, ActivityPageToken.class);
        }

        String encodeAsString() {
            String json = new Gson().toJson(this);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
        }
    }
}
