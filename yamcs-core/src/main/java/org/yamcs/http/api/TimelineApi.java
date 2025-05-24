package org.yamcs.http.api;

import static org.yamcs.timeline.TimelineService.RDB_TIMELINE_SOURCE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.yamcs.YamcsServer;
import org.yamcs.activities.ActivityService;
import org.yamcs.activities.protobuf.ActivityDefinition;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.AbstractTimelineApi;
import org.yamcs.protobuf.AddItemLogRequest;
import org.yamcs.protobuf.BatchDeleteItemsRequest;
import org.yamcs.protobuf.CancelActivityRequest;
import org.yamcs.protobuf.DeleteBandRequest;
import org.yamcs.protobuf.DeleteItemRequest;
import org.yamcs.protobuf.DeleteTimelineGroupRequest;
import org.yamcs.protobuf.DeleteViewRequest;
import org.yamcs.protobuf.ExecutionStatus;
import org.yamcs.protobuf.GetBandRequest;
import org.yamcs.protobuf.GetItemLogRequest;
import org.yamcs.protobuf.GetItemRequest;
import org.yamcs.protobuf.GetViewRequest;
import org.yamcs.protobuf.ListBandsRequest;
import org.yamcs.protobuf.ListBandsResponse;
import org.yamcs.protobuf.ListItemsRequest;
import org.yamcs.protobuf.ListItemsResponse;
import org.yamcs.protobuf.ListSourcesRequest;
import org.yamcs.protobuf.ListSourcesResponse;
import org.yamcs.protobuf.ListTimelineTagsRequest;
import org.yamcs.protobuf.ListTimelineTagsResponse;
import org.yamcs.protobuf.ListViewsRequest;
import org.yamcs.protobuf.ListViewsResponse;
import org.yamcs.protobuf.LogEntry;
import org.yamcs.protobuf.PredecessorInfo;
import org.yamcs.protobuf.RelativeTime;
import org.yamcs.protobuf.SaveBandRequest;
import org.yamcs.protobuf.SaveItemRequest;
import org.yamcs.protobuf.SaveViewRequest;
import org.yamcs.protobuf.StartActivityRequest;
import org.yamcs.protobuf.SubscribeItemChangesRequest;
import org.yamcs.protobuf.TimelineBand;
import org.yamcs.protobuf.TimelineItem;
import org.yamcs.protobuf.TimelineItemLog;
import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.protobuf.TimelineView;
import org.yamcs.protobuf.activities.ActivityInfo;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.timeline.ActivityGroup;
import org.yamcs.timeline.BandListener;
import org.yamcs.timeline.BatchObserver;
import org.yamcs.timeline.Criteria;
import org.yamcs.timeline.DeleteItemsSummary;
import org.yamcs.timeline.ItemGroup;
import org.yamcs.timeline.ItemListener;
import org.yamcs.timeline.ItemProvider;
import org.yamcs.timeline.ItemReceiver;
import org.yamcs.timeline.Predecessor;
import org.yamcs.timeline.TimelineActivity;
import org.yamcs.timeline.TimelineBandDb;
import org.yamcs.timeline.TimelineEvent;
import org.yamcs.timeline.TimelineItemDb;
import org.yamcs.timeline.TimelineService;
import org.yamcs.timeline.TimelineViewDb;
import org.yamcs.timeline.ViewListener;
import org.yamcs.utils.InvalidRequestException;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimeInterval;

import com.google.protobuf.Empty;
import com.google.protobuf.util.Durations;

public class TimelineApi extends AbstractTimelineApi<Context> {
    static final String MSG_NO_ID = "No id specified";

    private static final Log log = new Log(TimelineApi.class);

    @Override
    public void saveItem(Context ctx, SaveItemRequest request, Observer<TimelineItem> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        ActivityService activityService = ActivitiesApi.verifyService(request.getInstance());
        TimelineService timelineService = verifyService(request.getInstance());
        ItemProvider timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : RDB_TIMELINE_SOURCE);

        if (!request.hasType()) {
            throw new BadRequestException("Type is mandatory");
        }
        if (!request.hasName()) {
            throw new BadRequestException("Name is mandatory");
        }

        TimelineItemType type = request.getType();
        org.yamcs.timeline.TimelineItem item;

        var id = request.hasId() ? parseUuid(request.getId()) : UUID.randomUUID();

        switch (type) {
        case EVENT:
            TimelineEvent event = new TimelineEvent(id.toString());
            item = event;
            break;
        case ITEM_GROUP:
            ItemGroup itemGroup = new ItemGroup(id);
            item = itemGroup;
            break;
        case ACTIVITY:
            var activity = new TimelineActivity(id);
            item = activity;
            if (request.hasActivityDefinition()) {
                var defInfo = request.getActivityDefinition();
                var activityb = ActivityDefinition.newBuilder()
                        .setType(defInfo.getType())
                        .setArgs(defInfo.getArgs());

                // Derive a non-user defined technical description
                // (for example the script name, or the stack name)
                var executor = activityService.getExecutor(defInfo.getType());

                if (executor != null) {
                    var args = GpbWellKnownHelper.toJava(defInfo.getArgs());
                    var description = executor.describeActivity(args);
                    activityb.setDescription(description);
                }

                activity.setActivityDefinition(activityb.build());
                activity.setAutoStart(true); // may be changed below if the autoStart was specified in the request
            } // else it's a 'manual' activity
            break;
        case ACTIVITY_GROUP:
            ActivityGroup activityGroup = new ActivityGroup(id);
            item = activityGroup;
            break;
        default:
            throw new InternalServerErrorException("Unknown item type " + type);
        }

        if (request.hasName()) {
            item.setName(request.getName());
        }

        if (request.hasStart()) {
            if (request.hasRelativeTime() || request.getPredecessorsCount() > 0) {
                throw new BadRequestException(
                        "Cannot specify start when relative time or predecessors are also specified");
            }
            item.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));
        } else if (request.hasRelativeTime()) {
            if (request.getPredecessorsCount() > 0) {
                throw new BadRequestException("Cannot specify both relative time and predecessors");
            }
            RelativeTime relt = request.getRelativeTime();
            if (!relt.hasRelto()) {
                throw new BadRequestException("relto item is required when using relative time");
            }
            if (!relt.hasRelativeStart()) {
                throw new BadRequestException("relative start is required when using relative time");
            }
            item.setRelativeItemUuid(parseUuid(relt.getRelto()));
            item.setRelativeStart(Durations.toMillis(relt.getRelativeStart()));
        } else if (request.getPredecessorsCount() > 0) {
            if (item instanceof TimelineActivity ta) {
                for (var predecessorInfo : request.getPredecessorsList()) {
                    var predecessorId = UUID.fromString(predecessorInfo.getItemId());
                    ta.addPredecessor(new Predecessor(predecessorId, predecessorInfo.getStartCondition()));
                }
            } else {
                throw new BadRequestException("Predecessors can only be specified for activities");
            }
        } else {
            throw new BadRequestException("One of start, relativeTime or predecessors has to be specified");
        }
        if (!request.hasDuration()) {
            throw new BadRequestException("Duration is mandatory");
        }
        item.setDuration(Durations.toMillis(request.getDuration()));

        if (request.hasGroupId()) {
            item.setGroupUuid(parseUuid(request.getGroupId()));
        }
        if (request.hasDescription()) {
            item.setDescription(request.getDescription());
        }

        if (request.hasAutoStart() && item instanceof TimelineActivity ta) {
            ta.setAutoStart(request.getAutoStart());
        }

        var tags = new ArrayList<>(request.getTagsList());
        Collections.sort(tags);
        item.setTags(tags);

        item.setProperties(request.getPropertiesMap());
        item.setExtra(request.getExtraMap());

        var itemExists = timelineSource.getItem(id.toString()) != null;
        try {
            if (itemExists) {
                item = timelineSource.updateItem(item);
            } else {
                item = timelineSource.addItem(item);
            }
            observer.complete(item.toProtoBuf(true));
        } catch (InvalidRequestException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public void getItem(Context ctx, GetItemRequest request, Observer<TimelineItem> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        ItemProvider timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : RDB_TIMELINE_SOURCE);

        if (!request.hasId()) {
            throw new BadRequestException(MSG_NO_ID);
        }
        String id = request.getId();
        org.yamcs.timeline.TimelineItem item = timelineSource.getItem(id);
        if (item == null) {
            throw new NotFoundException(MSG_ITEM_NOT_FOUND(id));
        } else {
            observer.complete(item.toProtoBuf(true));
        }
    }

    @Override
    public void batchDeleteItems(Context ctx, BatchDeleteItemsRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        ItemProvider timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : RDB_TIMELINE_SOURCE);

        var interval = new TimeInterval();
        if (request.hasStart()) {
            interval.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));
        }
        if (request.hasStop()) {
            interval.setEnd(TimeEncoding.fromProtobufTimestamp(request.getStop()));
        }
        var backendCriteria = new Criteria(interval);
        if (request.hasFilter()) {
            backendCriteria.addFilterQuery(request.getFilter());
        }

        var uuids = request.getIdList().stream().map(UUID::fromString).toList();

        timelineSource.deleteItems(uuids, backendCriteria, new BatchObserver<DeleteItemsSummary>() {
            @Override
            public void complete(DeleteItemsSummary summary) {
                log.info("Deleted " + summary.count() + " items");
                observer.complete(Empty.getDefaultInstance());
            }

            @Override
            public void completeExceptionally(Throwable t) {
                observer.completeExceptionally(t);
            }
        });
    }

    @Override
    public void listItems(Context ctx, ListItemsRequest request, Observer<ListItemsResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadTimeline);
        TimelineService timelineService = verifyService(request.getInstance());

        String next = request.hasNext() ? request.getNext() : null;
        int limit = request.hasLimit() ? request.getLimit() : 500;

        TimeInterval interval = new TimeInterval();
        if (request.hasStart()) {
            interval.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));
        }
        if (request.hasStop()) {
            interval.setEnd(TimeEncoding.fromProtobufTimestamp(request.getStop()));
        }
        boolean details = request.hasDetails() && request.getDetails();

        ItemProvider timelineSource;
        List<String> tags;
        String filter;
        String source;

        if (request.hasBand()) {
            try {
                UUID bandId = UUID.fromString(request.getBand());
                var band = timelineService.getTimelineBandDb().getBand(bandId);
                if (band == null) {
                    throw new NotFoundException("No such band");
                }
                source = band.getSource() != null ? band.getSource()
                        : request.hasSource() ? request.getSource() : RDB_TIMELINE_SOURCE;

                tags = band.getTags();
                filter = band.getFilterQuery();
            } catch (IllegalArgumentException e) { // TEMP
                var tag = request.getBand();
                source = request.hasSource() ? request.getSource() : RDB_TIMELINE_SOURCE;
                tags = Arrays.asList(tag);
                filter = null;
            }
        } else {
            source = request.hasSource() ? request.getSource() : RDB_TIMELINE_SOURCE;
            tags = null;
            filter = null;
        }

        timelineSource = verifySource(timelineService, source);
        var activityService = YamcsServer.getServer().getInstance(request.getInstance()).getActivityService();

        var backendCriteria = new Criteria(interval);
        if (tags != null) {
            backendCriteria.addTags(tags);
        }
        if (filter != null) {
            backendCriteria.addFilterQuery(filter);
            if (request.hasFilter()) {
                throw new BadRequestException("Cannot specify both filter and band");
            }
        } else if (request.hasFilter()) {
            backendCriteria.addFilterQuery(request.getFilter());
        }

        var responseb = ListItemsResponse.newBuilder().setSource(source);

        timelineSource.getItems(limit, next, backendCriteria, new ItemReceiver() {
            @Override
            public void next(org.yamcs.timeline.TimelineItem item) {
                var proto = item.toProtoBuf(details);

                // Fill additional output fields from related entities
                if (details) {
                    var protob = proto.toBuilder();
                    for (var activityId : proto.getRunsList()) {
                        var uuid = UUID.fromString(activityId);
                        var activity = activityService.getActivity(uuid);
                        if (activity != null) {
                            var activityProto = ActivitiesApi.toActivityInfo(activity);
                            protob.addActivityRuns(activityProto);
                        }
                    }
                    if (protob.getPredecessorsCount() > 0) {
                        var enrichedPredecessors = new ArrayList<PredecessorInfo>();
                        for (var predecessor : protob.getPredecessorsList()) {
                            var copy = predecessor.toBuilder();

                            var itemId = predecessor.getItemId();
                            var predecessorItem = timelineService.getTimelineItemDb().getItem(itemId);
                            if (predecessorItem != null) {
                                var name = predecessorItem.getName();
                                if (name != null) {
                                    copy.setName(name);
                                }
                            }
                            enrichedPredecessors.add(copy.build());
                        }
                        protob.clearPredecessors();
                        protob.addAllPredecessors(enrichedPredecessors);
                    }
                    proto = protob.build();
                }
                responseb.addItems(proto);
            }

            @Override
            public void completeExceptionally(Throwable t) {
                log.warn("Error retrieving timeline items", t);
                observer.completeExceptionally(t);
            }

            @Override
            public void complete(String token) {
                if (token != null) {
                    responseb.setContinuationToken(token);
                }
                observer.complete(responseb.build());
            }
        });
    }

    @Override
    public void getItemLog(Context ctx, GetItemLogRequest request, Observer<TimelineItemLog> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        ItemProvider timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : RDB_TIMELINE_SOURCE);

        if (!request.hasId()) {
            throw new BadRequestException(MSG_NO_ID);
        }
        String id = request.getId();
        TimelineItemLog log = timelineSource.getItemLog(id);
        if (log == null) {
            throw new NotFoundException(MSG_ITEM_NOT_FOUND(id));
        } else {
            observer.complete(log);
        }
    }

    @Override
    public void addItemLog(Context ctx, AddItemLogRequest request, Observer<LogEntry> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        ItemProvider timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : RDB_TIMELINE_SOURCE);

        if (!request.hasId()) {
            throw new BadRequestException(MSG_NO_ID);
        }
        if (!request.hasEntry()) {
            throw new BadRequestException("log entry is mandatory");
        }
        String id = request.getId();
        LogEntry log = timelineSource.addItemLog(id, request.getEntry());
        if (log == null) {
            throw new NotFoundException(MSG_ITEM_NOT_FOUND(id));
        } else {
            observer.complete(log);
        }

    }

    @Override
    public void listSources(Context ctx, ListSourcesRequest request, Observer<ListSourcesResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        ListSourcesResponse.Builder lsrb = ListSourcesResponse.newBuilder().putAllSources(timelineService.getSources());
        observer.complete(lsrb.build());
    }

    @Override
    public void listTags(Context ctx, ListTimelineTagsRequest request, Observer<ListTimelineTagsResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineItemDb itemdb = timelineService.getTimelineItemDb();
        ListTimelineTagsResponse.Builder responseb = ListTimelineTagsResponse.newBuilder()
                .addAllTags(itemdb.getTags());
        observer.complete(responseb.build());

    }

    @Override
    public void saveBand(Context ctx, SaveBandRequest request, Observer<TimelineBand> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        var timelineService = verifyService(request.getInstance());
        var timelineBandDb = timelineService.getTimelineBandDb();

        var id = request.hasId() ? parseUuid(request.getId()) : UUID.randomUUID();

        var band = new org.yamcs.timeline.TimelineBand(id);

        if (!request.hasType()) {
            throw new BadRequestException("Type is mandatory");
        }
        band.setType(request.getType());

        if (request.hasSource()) {
            verifySource(timelineService, request.getSource());
            band.setSource(request.getSource());
        }

        if (request.hasName()) {
            band.setName(request.getName());
        }
        if (request.hasDescription()) {
            band.setDescription(request.getDescription());
        }
        if (request.hasFilter()) {
            band.setFilterQuery(request.getFilter());
        }
        band.setShared(request.getShared());
        band.setUsername(ctx.user.getName());
        band.setTags(request.getTagsList());
        band.setProperties(request.getPropertiesMap());
        band.setExtra(request.getExtraMap());

        var bandExists = timelineBandDb.getBand(id) != null;
        try {
            if (bandExists) {
                band = timelineBandDb.updateBand(band);
            } else {
                band = timelineBandDb.addBand(band);
            }
            observer.complete(band.toProtobuf());
        } catch (InvalidRequestException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public void getBand(Context ctx, GetBandRequest request, Observer<TimelineBand> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        UUID uuid = verifyUuid(request.hasId(), request.getId());
        observer.complete(verifyBand(timelineService, uuid).toProtobuf());
    }

    @Override
    public void listBands(Context ctx, ListBandsRequest request, Observer<ListBandsResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineBandDb timelineBandDb = timelineService.getTimelineBandDb();

        List<TimelineBand> bands = new ArrayList<>();
        timelineBandDb.listBands(ctx.user.getName(), new BandListener() {

            @Override
            public void next(org.yamcs.timeline.TimelineBand band) {
                bands.add(band.toProtobuf());
            }

            @Override
            public void completeExceptionally(Throwable t) {
                log.warn("Error retrieving timeline bands", t);
                observer.completeExceptionally(t);
            }

            @Override
            public void complete(String token) {
                Collections.sort(bands, (b1, b2) -> b1.getName().compareToIgnoreCase(b2.getName()));
                ListBandsResponse.Builder resp = ListBandsResponse.newBuilder()
                        .addAllBands(bands);
                observer.complete(resp.build());
            }
        });
    }

    @Override
    public void deleteBand(Context ctx, DeleteBandRequest request, Observer<TimelineBand> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineBandDb timelineBandDb = timelineService.getTimelineBandDb();

        UUID uuid = verifyUuid(request.hasId(), request.getId());

        org.yamcs.timeline.TimelineBand band;
        try {
            band = timelineBandDb.deleteBand(uuid);
        } catch (InvalidRequestException e) {
            throw new BadRequestException(e.getMessage());
        }
        if (band == null) {
            throw new NotFoundException(MSG_BAND_NOT_FOUND(request.getId()));
        } else {
            observer.complete(band.toProtobuf());
        }
    }

    @Override
    public void saveView(Context ctx, SaveViewRequest request, Observer<TimelineView> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        var timelineService = verifyService(request.getInstance());
        var timelineViewDb = timelineService.getTimelineViewDb();

        var bands = request.getBandsList().stream()
                .map(id -> UUID.fromString(id))
                .collect(Collectors.toList());

        var id = request.hasId() ? parseUuid(request.getId()) : UUID.randomUUID();

        var view = new org.yamcs.timeline.TimelineView(id);
        view.setName(request.getName());
        if (view.toProtobuf().hasDescription()) {
            view.setDescription(request.getDescription());
        }
        view.setBands(bands);

        var viewExists = timelineViewDb.getView(id) != null;
        try {
            if (viewExists) {
                view = timelineViewDb.updateView(view);
            } else {
                view = timelineViewDb.addView(view);
            }
            observer.complete(enrichView(timelineService, view));
        } catch (InvalidRequestException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public void getView(Context ctx, GetViewRequest request, Observer<TimelineView> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineViewDb timelineViewDb = timelineService.getTimelineViewDb();
        UUID uuid = verifyUuid(request.hasId(), request.getId());
        org.yamcs.timeline.TimelineView view = timelineViewDb.getView(uuid);
        if (view == null) {
            throw new NotFoundException(MSG_VIEW_NOT_FOUND(request.getId()));
        } else {
            observer.complete(enrichView(timelineService, view));
        }
    }

    @Override
    public void listViews(Context ctx, ListViewsRequest request, Observer<ListViewsResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineViewDb timelineViewDb = timelineService.getTimelineViewDb();

        List<TimelineView> views = new ArrayList<>();
        timelineViewDb.listViews(new ViewListener() {

            @Override
            public void next(org.yamcs.timeline.TimelineView view) {
                views.add(enrichView(timelineService, view));
            }

            @Override
            public void completeExceptionally(Throwable t) {
                log.warn("Error retrieving timeline views", t);
                observer.completeExceptionally(t);
            }

            @Override
            public void complete(String token) {
                Collections.sort(views, (v1, v2) -> v1.getName().compareToIgnoreCase(v2.getName()));
                ListViewsResponse.Builder resp = ListViewsResponse.newBuilder()
                        .addAllViews(views);
                observer.complete(resp.build());
            }
        });
    }

    @Override
    public void deleteView(Context ctx, DeleteViewRequest request, Observer<TimelineView> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineViewDb timelineViewDb = timelineService.getTimelineViewDb();

        UUID uuid = verifyUuid(request.hasId(), request.getId());

        org.yamcs.timeline.TimelineView view;
        try {
            view = timelineViewDb.deleteView(uuid);
        } catch (InvalidRequestException e) {
            throw new BadRequestException(e.getMessage());
        }
        if (view == null) {
            throw new NotFoundException(MSG_BAND_NOT_FOUND(request.getId()));
        } else {
            observer.complete(view.toProtobuf());
        }
    }

    private TimelineView enrichView(TimelineService service, org.yamcs.timeline.TimelineView view) {
        TimelineBandDb bandDb = service.getTimelineBandDb();
        TimelineView.Builder b = TimelineView.newBuilder(view.toProtobuf());
        b.clearBands();
        for (UUID bandId : view.getBands()) {
            org.yamcs.timeline.TimelineBand enrichedBand = bandDb.getBand(bandId);
            if (enrichedBand != null) {
                b.addBands(enrichedBand.toProtobuf());
            }
        }
        return b.build();
    }

    @Override
    public void deleteItem(Context ctx, DeleteItemRequest request, Observer<TimelineItem> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        ItemProvider timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : RDB_TIMELINE_SOURCE);
        UUID uuid = verifyUuid(request.hasId(), request.getId());

        org.yamcs.timeline.TimelineItem item;
        try {
            item = timelineSource.deleteItem(uuid);
        } catch (InvalidRequestException e) {
            throw new BadRequestException(e.getMessage());
        }
        if (item == null) {
            throw new NotFoundException(MSG_ITEM_NOT_FOUND(request.getId()));
        } else {
            observer.complete(item.toProtoBuf(true));
        }

    }

    @Override
    public void deleteTimelineGroup(Context ctx, DeleteTimelineGroupRequest request, Observer<TimelineItem> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        ItemProvider timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : RDB_TIMELINE_SOURCE);
        UUID uuid = verifyUuid(request.hasId(), request.getId());

        org.yamcs.timeline.TimelineItem item;
        try {
            item = timelineSource.deleteTimelineGroup(uuid);
        } catch (InvalidRequestException e) {
            throw new BadRequestException(e.getMessage());
        }
        if (item == null) {
            throw new NotFoundException(MSG_ITEM_NOT_FOUND(request.getId()));
        } else {
            observer.complete(item.toProtoBuf(true));
        }
    }

    @Override
    public void startActivity(Context ctx, StartActivityRequest request, Observer<ActivityInfo> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        ctx.checkSystemPrivilege(SystemPrivilege.ControlActivities);

        TimelineService timelineService = verifyService(request.getInstance());
        UUID uuid = verifyUuid(request.hasId(), request.getId());

        var scheduler = timelineService.getActivityScheduler();
        scheduler.startActivity(uuid.toString()).whenComplete((activity, err) -> {
            if (err != null) {
                Throwable cause = (err instanceof CompletionException) ? err.getCause() : err;
                if (cause instanceof IllegalArgumentException || cause instanceof IllegalStateException) {
                    observer.completeExceptionally(new BadRequestException(cause.getMessage()));
                } else {
                    observer.completeExceptionally(new InternalServerErrorException(cause));
                }
            } else {
                observer.complete(ActivitiesApi.toActivityInfo(activity));
            }
        });
    }

    @Override
    public void cancelActivity(Context ctx, CancelActivityRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        var timelineService = verifyService(request.getInstance());
        var timelineSource = verifySource(timelineService, RDB_TIMELINE_SOURCE);

        var itemId = request.getItem();
        var item = timelineSource.getItem(itemId);
        if (item == null) {
            throw new NotFoundException(MSG_ITEM_NOT_FOUND(itemId));
        }

        if (item instanceof TimelineActivity activity) {
            var status = activity.getStatus();
            if (status == ExecutionStatus.PLANNED
                    || status == ExecutionStatus.READY) {
                activity.setStatus(ExecutionStatus.CANCELED);
            } else {
                throw new BadRequestException("Item cannot transition from "
                        + activity.getStatus() + " to " + ExecutionStatus.PLANNED);
            }

            timelineSource.updateItem(activity);
            observer.complete(Empty.getDefaultInstance());
        } else {
            throw new BadRequestException("Not an activity item");
        }
    }

    @Override
    public void subscribeItemChanges(Context ctx, SubscribeItemChangesRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadTimeline);

        var timelineService = verifyService(request.getInstance());
        var timelineItemDb = timelineService.getTimelineItemDb();

        var dirty = new AtomicBoolean();
        var itemListener = new ItemListener() {

            @Override
            public void onItemCreated(org.yamcs.timeline.TimelineItem item) {
                dirty.set(true);
            }

            @Override
            public void onItemUpdated(org.yamcs.timeline.TimelineItem item) {
                dirty.set(true);
            }

            @Override
            public void onItemDeleted(org.yamcs.timeline.TimelineItem item) {
                dirty.set(true);
            }
        };

        var exec = YamcsServer.getServer().getThreadPoolExecutor();
        var future = exec.scheduleAtFixedRate(() -> {
            if (dirty.compareAndSet(true, false)) {
                observer.next(Empty.getDefaultInstance());
            }
        }, 2, 2, TimeUnit.SECONDS);

        observer.setCancelHandler(() -> {
            timelineItemDb.removeItemListener(itemListener);
            future.cancel(false);
        });
        timelineItemDb.addItemListener(itemListener);
    }

    // NOTE: the checkSource is to warn users to set a source for the band because in older versions of Yamcs this was
    // not done. The check should disappear in the future.
    private org.yamcs.timeline.TimelineBand verifyBand(TimelineService timelineService, UUID bandId) {
        var band = timelineService.getTimelineBandDb().getBand(bandId);
        if (band == null) {
            throw new NotFoundException(MSG_BAND_NOT_FOUND(bandId.toString()));
        }
        return band;
    }

    private ItemProvider verifySource(TimelineService timelineService, String source) {
        ItemProvider ts = timelineService.getSource(source);
        if (ts == null) {
            throw new BadRequestException("Invalid source '" + source + "'");
        }
        return ts;
    }

    private TimelineService verifyService(String yamcsInstance) {
        String instance = InstancesApi.verifyInstance(yamcsInstance);

        List<TimelineService> cl = YamcsServer.getServer().getInstance(instance)
                .getServices(TimelineService.class);
        if (cl.isEmpty()) {
            throw new NotFoundException("No timeline service found");
        } else {
            if (cl.size() > 1) {
                log.warn("multiple timeline services found but only one supported");
            }
            return cl.get(0);
        }
    }

    private static UUID verifyUuid(boolean hasId, String uuid) {
        if (!hasId) {
            throw new BadRequestException(MSG_NO_ID);
        }
        return parseUuid(uuid);

    }

    private static UUID parseUuid(String uuid) {
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid uuid '" + uuid + "'");
        }
    }

    static final String MSG_ITEM_NOT_FOUND(String id) {
        return "Item " + id + " not found";
    }

    static final String MSG_BAND_NOT_FOUND(String id) {
        return "Band " + id + " not found";
    }

    static final String MSG_VIEW_NOT_FOUND(String id) {
        return "View " + id + " not found";
    }

}
