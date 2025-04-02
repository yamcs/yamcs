package org.yamcs.client.activity;

import static org.yamcs.client.utils.WellKnownTypes.toTimestamp;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import org.yamcs.api.MethodHandler;
import org.yamcs.api.Observer;
import org.yamcs.client.Page;
import org.yamcs.client.base.AbstractPage;
import org.yamcs.client.base.ResponseObserver;
import org.yamcs.protobuf.TimelineItem;
import org.yamcs.protobuf.activities.ActivitiesApiClient;
import org.yamcs.protobuf.activities.ActivityInfo;
import org.yamcs.protobuf.activities.CompleteManualActivityRequest;
import org.yamcs.protobuf.activities.ListActivitiesRequest;
import org.yamcs.protobuf.activities.ListActivitiesResponse;

public class ActivityClient {
    final ActivitiesApiClient activityService;
    final String instance;

    public ActivityClient(MethodHandler handler, String instance) {
        this.instance = instance;
        activityService = new ActivitiesApiClient(handler);
    }

    public CompletableFuture<Page<ActivityInfo>> getActivities(Instant start, Instant stop, String type) {
        ListActivitiesRequest.Builder requestb = ListActivitiesRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(toTimestamp(start));
        }
        if (stop != null) {
            requestb.setStop(toTimestamp(stop));
        }
        if (type != null) {
            requestb.addType(type);
        }
        return new ActivityPage(requestb.build()).future();

    }

    private class ActivityPage extends AbstractPage<ListActivitiesRequest, ListActivitiesResponse, ActivityInfo>
            implements Page<ActivityInfo> {

        public ActivityPage(ListActivitiesRequest request) {
            super(request, "activities");
        }

        @Override
        protected void fetch(ListActivitiesRequest request, Observer<ListActivitiesResponse> observer) {
            activityService.listActivities(null, request, observer);
        }
    }


    public CompletableFuture<ActivityInfo> complete(String activityId, String failureReason) {
        CompleteManualActivityRequest.Builder requestb = CompleteManualActivityRequest.newBuilder()
                .setInstance(instance)
                .setActivity(activityId);
        if (failureReason != null) {
            requestb.setFailureReason(failureReason);
        }

        CompletableFuture<ActivityInfo> f = new CompletableFuture<>();
        activityService.completeManualActivity(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }
}
