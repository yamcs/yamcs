package org.yamcs.client.timeline;

import static org.yamcs.client.utils.WellKnownTypes.toTimestamp;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.yamcs.api.MethodHandler;
import org.yamcs.api.Observer;
import org.yamcs.client.Page;
import org.yamcs.client.base.AbstractPage;
import org.yamcs.client.base.ResponseObserver;
import org.yamcs.protobuf.DeleteBandRequest;
import org.yamcs.protobuf.DeleteItemRequest;
import org.yamcs.protobuf.DeleteTimelineGroupRequest;
import org.yamcs.protobuf.GetItemLogRequest;
import org.yamcs.protobuf.GetItemRequest;
import org.yamcs.protobuf.ListBandsRequest;
import org.yamcs.protobuf.ListBandsResponse;
import org.yamcs.protobuf.ListItemsRequest;
import org.yamcs.protobuf.ListItemsResponse;
import org.yamcs.protobuf.ListSourcesRequest;
import org.yamcs.protobuf.ListSourcesResponse;
import org.yamcs.protobuf.ListTimelineTagsRequest;
import org.yamcs.protobuf.ListTimelineTagsResponse;
import org.yamcs.protobuf.SaveBandRequest;
import org.yamcs.protobuf.SaveItemRequest;
import org.yamcs.protobuf.StartActivityRequest;
import org.yamcs.protobuf.TimelineApiClient;
import org.yamcs.protobuf.TimelineBand;
import org.yamcs.protobuf.TimelineItem;
import org.yamcs.protobuf.TimelineItemLog;
import org.yamcs.protobuf.TimelineSourceCapabilities;
import org.yamcs.protobuf.activities.ActivityInfo;

public class TimelineClient {
    static public final String RDB_TIMELINE_SOURCE = "rdb";
    final String instance;
    final TimelineApiClient timelineService;

    public TimelineClient(MethodHandler handler, String instance) {
        this.instance = instance;
        timelineService = new TimelineApiClient(handler);
    }

    public CompletableFuture<Page<TimelineItem>> getItems(Instant start, Instant stop, String band) {
        ListItemsRequest.Builder requestb = ListItemsRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(toTimestamp(start));
        }
        if (stop != null) {
            requestb.setStop(toTimestamp(stop));
        }
        if (band != null) {
            requestb.setBand(band);
        }
        return new TimelineItemPage(requestb.build()).future();

    }

    public CompletableFuture<TimelineItem> saveItem(String source, TimelineItem item) {
        if (!item.hasType()) {
            throw new IllegalArgumentException("type is mandatory");
        }
        SaveItemRequest.Builder requestb = SaveItemRequest.newBuilder()
                .setType(item.getType())
                .setInstance(instance)
                .setSource(source);
        if (item.hasId()) {
            requestb.setId(item.getId());
        }
        if (item.hasName()) {
            requestb.setName(item.getName());
        }

        if (item.hasStart()) {
            requestb.setStart(item.getStart());
        }
        if (item.hasRelativeTime()) {
            requestb.setRelativeTime(item.getRelativeTime());
        }
        if (item.hasDuration()) {
            requestb.setDuration(item.getDuration());
        }
        requestb.addAllTags(item.getTagsList());
        if (item.hasGroupId()) {
            requestb.setGroupId(item.getGroupId());
        }
        if (item.hasDescription()) {
            requestb.setDescription(item.getDescription());
        }

        if (item.getPredecessorsCount() > 0) {
            requestb.addAllPredecessors(item.getPredecessorsList());
        }
        if (item.hasAutoStart()) {
            requestb.setAutoStart(item.getAutoStart());
        }

        CompletableFuture<TimelineItem> f = new CompletableFuture<>();
        timelineService.saveItem(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<TimelineItem> saveItem(TimelineItem item) {
        return saveItem(RDB_TIMELINE_SOURCE, item);
    }

    public CompletableFuture<TimelineItem> getItem(String source, String id) {
        CompletableFuture<TimelineItem> f = new CompletableFuture<>();
        GetItemRequest.Builder requestb = GetItemRequest.newBuilder()
                .setInstance(instance)
                .setSource(source)
                .setId(id);
        timelineService.getItem(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<TimelineItem> deleteItem(String id) {
        return deleteItem(RDB_TIMELINE_SOURCE, id);
    }

    public CompletableFuture<TimelineItem> deleteItem(String source, String id) {
        CompletableFuture<TimelineItem> f = new CompletableFuture<>();
        DeleteItemRequest.Builder requestb = DeleteItemRequest.newBuilder()
                .setInstance(instance)
                .setSource(source)
                .setId(id);
        timelineService.deleteItem(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<TimelineItemLog> getItemLog(String id) {
        CompletableFuture<TimelineItemLog> f = new CompletableFuture<>();
        GetItemLogRequest.Builder requestb = GetItemLogRequest.newBuilder()
                .setInstance(instance)
                .setSource(RDB_TIMELINE_SOURCE)
                .setId(id);
        timelineService.getItemLog(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<TimelineItem> deleteTimelineGroup(String id) {
        return deleteTimelineGroup(RDB_TIMELINE_SOURCE, id);
    }

    public CompletableFuture<TimelineItem> deleteTimelineGroup(String source, String id) {
        CompletableFuture<TimelineItem> f = new CompletableFuture<>();
        DeleteTimelineGroupRequest.Builder requestb = DeleteTimelineGroupRequest.newBuilder()
                .setInstance(instance)
                .setSource(source)
                .setId(id);
        timelineService.deleteTimelineGroup(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<TimelineItem> getItem(String id) {
        return getItem(RDB_TIMELINE_SOURCE, id);
    }

    public CompletableFuture<Map<String, TimelineSourceCapabilities>> getSources() {
        ListSourcesRequest.Builder requestb = ListSourcesRequest.newBuilder().setInstance(instance);
        CompletableFuture<ListSourcesResponse> f = new CompletableFuture<>();
        timelineService.listSources(null, requestb.build(), new ResponseObserver<>(f));
        return f.thenApply(ListSourcesResponse::getSourcesMap);
    }

    private class TimelineItemPage extends AbstractPage<ListItemsRequest, ListItemsResponse, TimelineItem>
            implements Page<TimelineItem> {

        public TimelineItemPage(ListItemsRequest request) {
            super(request, "items");
        }

        @Override
        protected void fetch(ListItemsRequest request, Observer<ListItemsResponse> observer) {
            timelineService.listItems(null, request, observer);
        }
    }

    public CompletableFuture<List<String>> getTags() {
        ListTimelineTagsRequest.Builder requestb = ListTimelineTagsRequest.newBuilder().setInstance(instance);
        CompletableFuture<ListTimelineTagsResponse> f = new CompletableFuture<>();
        timelineService.listTags(null, requestb.build(), new ResponseObserver<>(f));
        return f.thenApply(ListTimelineTagsResponse::getTagsList);
    }

    public CompletableFuture<List<TimelineBand>> getBands() {
        ListBandsRequest.Builder request = ListBandsRequest.newBuilder().setInstance(instance);
        CompletableFuture<ListBandsResponse> f = new CompletableFuture<>();
        timelineService.listBands(null, request.build(), new ResponseObserver<>(f));
        return f.thenApply(ListBandsResponse::getBandsList);
    }

    public CompletableFuture<TimelineBand> deleteBand(String id) {
        CompletableFuture<TimelineBand> f = new CompletableFuture<>();
        DeleteBandRequest.Builder requestb = DeleteBandRequest.newBuilder()
                .setInstance(instance)
                .setId(id);
        timelineService.deleteBand(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<TimelineBand> addBand(TimelineBand band) {
        SaveBandRequest.Builder requestb = SaveBandRequest.newBuilder()
                .setType(band.getType())
                .setInstance(instance);
        requestb.putAllProperties(band.getPropertiesMap());
        if (band.hasId()) {
            requestb.setId(band.getId());
        }
        if (band.hasName()) {
            requestb.setName(band.getName());
        }
        if (band.hasDescription()) {
            requestb.setDescription(band.getDescription());
        }
        if (band.hasFilter()) {
            requestb.setFilter(band.getFilter());
        }
        if (band.hasShared()) {
            requestb.setShared(band.getShared());
        }
        if (band.hasSource()) {
            requestb.setSource(band.getSource());
        }

        CompletableFuture<TimelineBand> f = new CompletableFuture<>();
        timelineService.saveBand(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<ActivityInfo> startActivity(String id) {
        StartActivityRequest req = StartActivityRequest.newBuilder().setInstance(instance).setId(id).build();

        CompletableFuture<ActivityInfo> f = new CompletableFuture<>();
        timelineService.startActivity(null, req, new ResponseObserver<>(f));
        return f;
    }
}
