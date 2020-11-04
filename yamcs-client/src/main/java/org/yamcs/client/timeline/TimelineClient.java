package org.yamcs.client.timeline;

import org.yamcs.api.MethodHandler;
import org.yamcs.api.Observer;
import org.yamcs.client.Page;
import org.yamcs.client.base.AbstractPage;
import org.yamcs.client.base.ResponseObserver;
import org.yamcs.protobuf.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.yamcs.client.utils.TimeUtils.toTimestamp;

public class TimelineClient {
    static public final String RDB_TIMELINE_SOURCE = "rdb";
    final String instance;
    final TimelineApiClient timelineService;

    public TimelineClient(MethodHandler handler, String instance) {
        this.instance = instance;
        timelineService = new TimelineApiClient(handler);
    }

    public CompletableFuture<Page<TimelineItem>> getItems(Instant start, Instant stop, TimelineBand band) {
        ListItemsRequest.Builder requestb = ListItemsRequest.newBuilder()
                .setInstance(instance)
                .setStart(toTimestamp(start))
                .setStop(toTimestamp(stop));
        if(band!=null) {
            requestb.setBand(band);
        }
        return new TimelineItemPage(requestb.build()).future();

    }

    public CompletableFuture<TimelineItem> addItem(String source, TimelineItem item) {
        if (!item.hasType()) {
            throw new IllegalArgumentException("type is mandatory");
        }
        AddItemRequest.Builder requestb = AddItemRequest.newBuilder()
                .setType(item.getType())
                .setInstance(instance)
                .setSource(source);
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
        if (item.hasGroupUuid()) {
            requestb.setGroupUuid(item.getGroupUuid());
        }
        CompletableFuture<TimelineItem> f = new CompletableFuture<>();
        timelineService.addItem(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<TimelineItem> addItem(TimelineItem item) {
        return addItem(RDB_TIMELINE_SOURCE, item);
    }
    public CompletableFuture<TimelineItem> getItem(String source, String uuid) {

        CompletableFuture<TimelineItem> f = new CompletableFuture<>();
        GetItemRequest.Builder requestb = GetItemRequest.newBuilder()
                .setInstance(instance)
                .setSource(source)
                .setUuid(uuid);
        timelineService.getItem(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<TimelineItem> deleteItem(String uuid) {
        return deleteItem(RDB_TIMELINE_SOURCE, uuid);
    }

    public CompletableFuture<TimelineItem> deleteItem(String source, String uuid) {

        CompletableFuture<TimelineItem> f = new CompletableFuture<>();
        DeleteItemRequest.Builder requestb = DeleteItemRequest.newBuilder()
                .setInstance(instance)
                .setSource(source)
                .setUuid(uuid);
        timelineService.deleteItem(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<TimelineItem> deleteTimelineGroup(String uuid) {
        return deleteTimelineGroup(RDB_TIMELINE_SOURCE, uuid);
    }
    public CompletableFuture<TimelineItem> deleteTimelineGroup(String source, String uuid) {

        CompletableFuture<TimelineItem> f = new CompletableFuture<>();
        DeleteTimelineGroupRequest.Builder requestb = DeleteTimelineGroupRequest.newBuilder()
                .setInstance(instance)
                .setSource(source)
                .setUuid(uuid);
        timelineService.deleteTimelineGroup(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }

    public CompletableFuture<TimelineItem> getItem(String uuid) {
        return getItem(RDB_TIMELINE_SOURCE, uuid);
    }

    public CompletableFuture<Map<String, TimelineSourceCapabilities>> getSources() {
        ListSourcesRequest.Builder requestb = ListSourcesRequest.newBuilder().setInstance(instance);
        CompletableFuture<ListSourcesResponse> f = new CompletableFuture<>();
        timelineService.listSources(null, requestb.build(), new ResponseObserver<>(f));
        return f.thenApply(r -> r.getSourcesMap());
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
        return f.thenApply(r -> r.getTagsList());
    }

    public CompletableFuture<TimelineItem> updateItem(TimelineItem item) {
        return updateItem(RDB_TIMELINE_SOURCE, item);
    }

    public CompletableFuture<TimelineItem> updateItem(String source, TimelineItem item) {
        if(!item.hasUuid()) {
            throw new IllegalArgumentException("the intem needs an UUID");
        }

        UpdateItemRequest.Builder requestb = UpdateItemRequest.newBuilder()
                .setInstance(instance)
                .setSource(source)
                .setUuid(item.getUuid());

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
        if (item.hasGroupUuid()) {
            requestb.setGroupUuid(item.getGroupUuid());
        }
        CompletableFuture<TimelineItem> f = new CompletableFuture<>();
        timelineService.updateItem(null, requestb.build(), new ResponseObserver<>(f));
        return f;
    }


    public CompletableFuture<List<TimelineBand>> getBands() {
        ListBandsRequest.Builder request = ListBandsRequest.newBuilder().setInstance(instance);
        CompletableFuture<ListBandsResponse> f = new CompletableFuture<>();
        timelineService.listBands(null, request.build(), new ResponseObserver<>(f));
        return f.thenApply(r -> r.getBandsList());
    }

    public CompletableFuture<TimelineBand> addBand(TimelineBand band) {
        AddBandRequest.Builder requestb = AddBandRequest.newBuilder()
                .setType(band.getType())
                .setInstance(instance);
        requestb.addAllTags(band.getTagsList());
        requestb.putAllProperties(band.getPropertiesMap());
        if (band.hasName()) {
            requestb.setName(band.getName());
        }
        if (band.hasDescription()) {
            requestb.setDescription(band.getDescription());
        }
        if (band.hasShared()) {
            requestb.setShared(band.getShared());
        }
        CompletableFuture<TimelineBand> f = new CompletableFuture<>();
        timelineService.addBand(null, requestb.build(), new ResponseObserver(f));
        return f;
    }
}
