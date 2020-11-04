package org.yamcs.http.api;

import com.google.protobuf.util.Durations;
import org.yamcs.YamcsServer;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.TimelineItem;
import org.yamcs.protobuf.*;
import org.yamcs.timeline.*;
import org.yamcs.utils.InvalidRequestException;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimeInterval;

import java.util.List;
import java.util.UUID;


public class TimelineApi extends AbstractTimelineApi<Context> {

    private static final Log log = new Log(TimelineApi.class);
    private int limit;

    @Override
    public void addItem(Context ctx, AddItemRequest request, Observer<TimelineItem> observer) {
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineSource timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : TimelineService.RDB_TIMELINE_SOURCE);


        org.yamcs.timeline.TimelineItem item = req2Item(request);
        try {
            item = timelineSource.addItem(item);
            observer.complete(item.toProtoBuf());
        } catch (InvalidRequestException e) {
            throw new BadRequestException(e.getMessage());
        }
    }


    @Override
    public void getItem(Context ctx, GetItemRequest request, Observer<TimelineItem> observer) {
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineSource timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : TimelineService.RDB_TIMELINE_SOURCE);

        if (!request.hasUuid()) {
            throw new BadRequestException("No uuid specified");
        }
        UUID uuid = parseUuid(request.getUuid());
        org.yamcs.timeline.TimelineItem item = timelineSource.getItem(uuid);
        if (item == null) {
            throw new NotFoundException("Item " + uuid + " not found");
        } else {
            observer.complete(item.toProtoBuf());
        }
    }

    @Override
    public void updateItem(Context ctx, UpdateItemRequest request, Observer<TimelineItem> observer) {
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineSource timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : TimelineService.RDB_TIMELINE_SOURCE);

        if (!request.hasUuid()) {
            throw new BadRequestException("No uuid specified");
        }
        UUID uuid = parseUuid(request.getUuid());

        org.yamcs.timeline.TimelineItem item = timelineSource.getItem(uuid);
        if (item == null) {
            throw new NotFoundException("Item " + uuid + " not found");
        }

        if (request.hasStart()) {
            if (request.hasRelativeTime()) {
                throw new BadRequestException("Cannot specify both start and relative time");
            }
            item.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));

        } else if (request.hasRelativeTime()) {
            RelativeTime relt = request.getRelativeTime();
            if (!relt.hasUuid()) {
                throw new BadRequestException("uuid is required in the relative time");
            }
            if (!relt.hasRelativeStart()) {
                throw new BadRequestException("relative start is required in the relative time");
            }
            item.setRelativeItemUuid(parseUuid(relt.getUuid()));
            item.setRelativeStart(Durations.toMillis(relt.getRelativeStart()));
        } else {
            throw new BadRequestException("One of start or relativeTime has to be specified");
        }
        if (request.hasDuration()) {
            item.setDuration(Durations.toMillis(request.getDuration()));
        }

        item.setGroupUuid(request.hasGroupUuid() ? parseUuid(request.getGroupUuid()) : null);
        item.setTags(request.getTagsList());

        try {
            item = timelineSource.updateItem(item);
            observer.complete(item.toProtoBuf());
        } catch (InvalidRequestException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public void listItems(Context ctx, ListItemsRequest request, Observer<ListItemsResponse> observer) {
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineSource timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : TimelineService.RDB_TIMELINE_SOURCE);

        String next = request.hasNext() ? request.getNext() : null;
        int limit = request.hasLimit() ? request.getLimit() : 500;
        TimeInterval interval = new TimeInterval();
        if(request.hasStart()) {
            interval.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));
        }
        if(request.hasStart()) {
            interval.setEnd(TimeEncoding.fromProtobufTimestamp(request.getStop()));
        }
        ItemFilter filter = new ItemFilter(interval);
        if(request.hasBand()) {
            filter.setTags(request.getBand().getTagsList());
        }


        ListItemsResponse.Builder resp = ListItemsResponse.newBuilder();

        timelineSource.getItems(limit, next, filter, new ItemListener() {

            @Override
            public void next(org.yamcs.timeline.TimelineItem item) {
                resp.addItems(item.toProtoBuf());
            }

            @Override
            public void completeExceptionally(Throwable t) {
                log.warn("Error retrieving timeline items", t);
                observer.completeExceptionally(t);
            }

            @Override
            public void complete(String token) {
                if (token != null) {
                    resp.setContinuationToken(token);
                }
                observer.complete(resp.build());
            }
        });

    }

    @Override
    public void listSources(Context ctx, ListSourcesRequest request, Observer<ListSourcesResponse> observer) {
        TimelineService timelineService = verifyService(request.getInstance());
        ListSourcesResponse.Builder lsrb = ListSourcesResponse.newBuilder().putAllSources(timelineService.getSources());
        observer.complete(lsrb.build());
    }

    @Override
    public void listTags(Context ctx, ListTimelineTagsRequest request, Observer<ListTimelineTagsResponse> observer) {
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineSource timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : TimelineService.RDB_TIMELINE_SOURCE);
        ListTimelineTagsResponse.Builder responseb = ListTimelineTagsResponse.newBuilder()
                .addAllTags(timelineSource.getTags());
        observer.complete(responseb.build());

    }

    @Override
    public void addBand(Context ctx, AddBandRequest request, Observer<TimelineBand> observer) {
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineBandDb timelineBandDb = timelineService.getTimelineBandDb();
        TimelineBand band = req2Band(request,ctx.user.getName());
        try {
            band = timelineBandDb.addBand(band);
            observer.complete(band);
        } catch (InvalidRequestException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public void getBand(Context ctx, GetBandRequest request, Observer<TimelineBand> observer) {
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineBandDb timelineBandDb = timelineService.getTimelineBandDb();
        if (!request.hasUuid()) {
            throw new BadRequestException("No uuid specified");
        }
        UUID uuid = parseUuid(request.getUuid());
        TimelineBand band = timelineBandDb.getBand(uuid);
        if (band == null) {
            throw new NotFoundException("Item " + uuid + " not found");
        } else {
            observer.complete(band);
        }
    }

    @Override
    public void listBands(Context ctx, ListBandsRequest request, Observer<ListBandsResponse> observer) {
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineBandDb timelineBandDb = timelineService.getTimelineBandDb();


        ListBandsResponse.Builder resp = ListBandsResponse.newBuilder();

        timelineBandDb.listBands(ctx.user.getName(), new BandListener() {

            @Override
            public void next(TimelineBand band) {
                resp.addBands(band);
            }

            @Override
            public void completeExceptionally(Throwable t) {
                log.warn("Error retrieving timeline items", t);
                observer.completeExceptionally(t);
            }

            @Override
            public void complete(String token) {
                observer.complete(resp.build());
            }
        });
    }

    @Override
    public void deleteBand(Context ctx, DeleteBandRequest request, Observer<TimelineBand> observer) {
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineBandDb timelineBandDb = timelineService.getTimelineBandDb();

        if (!request.hasUuid()) {
            throw new BadRequestException("No uuid specified");
        }
        UUID uuid = parseUuid(request.getUuid());

        TimelineBand band;
        try {
            band = timelineBandDb.deleteBand(uuid);
        } catch (InvalidRequestException e) {
            throw new BadRequestException(e.getMessage());
        }
        if (band == null) {
            throw new NotFoundException("Band " + uuid + " not found");
        } else {
            observer.complete(band);
        }
    }

    @Override
    public void addView(Context ctx, AddViewRequest request, Observer<TimelineView> observer) {
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineViewDb timelineViewDb = timelineService.getTimelineViewDb();
        TimelineView view = req2View(request);
        try {
            view = timelineViewDb.addView(view);
            observer.complete(view);
        } catch (InvalidRequestException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public void getView(Context ctx, GetViewRequest request, Observer<TimelineView> observer) {
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineViewDb timelineViewDb = timelineService.getTimelineViewDb();
        if (!request.hasUuid()) {
            throw new BadRequestException("No uuid specified");
        }
        UUID uuid = parseUuid(request.getUuid());
        TimelineView view = timelineViewDb.getView(uuid);
        if (view == null) {
            throw new NotFoundException("Item " + uuid + " not found");
        } else {
            observer.complete(view);
        }
    }

    @Override
    public void listViews(Context ctx, ListViewsRequest request, Observer<ListViewsResponse> observer) {
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineViewDb timelineViewDb = timelineService.getTimelineViewDb();

        ListViewsResponse.Builder resp = ListViewsResponse.newBuilder();

        timelineViewDb.listViews(new ViewListener() {

            @Override
            public void next(TimelineView view) {
                resp.addViews(view);
            }

            @Override
            public void completeExceptionally(Throwable t) {
                log.warn("Error retrieving timeline items", t);
                observer.completeExceptionally(t);
            }

            @Override
            public void complete(String token) {
                observer.complete(resp.build());
            }
        });
    }

    @Override
    public void deleteView(Context ctx, DeleteViewRequest request, Observer<TimelineView> observer) {
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineViewDb timelineViewDb = timelineService.getTimelineViewDb();

        if (!request.hasUuid()) {
            throw new BadRequestException("No uuid specified");
        }
        UUID uuid = parseUuid(request.getUuid());

        TimelineView view;
        try {
            view = timelineViewDb.deleteView(uuid);
        } catch (InvalidRequestException e) {
            throw new BadRequestException(e.getMessage());
        }
        if (view == null) {
            throw new NotFoundException("Band " + uuid + " not found");
        } else {
            observer.complete(view);
        }
    }

    @Override
    public void deleteItem(Context ctx, DeleteItemRequest request, Observer<TimelineItem> observer) {
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineSource timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : TimelineService.RDB_TIMELINE_SOURCE);
        if (!request.hasUuid()) {
            throw new BadRequestException("No uuid specified");
        }
        UUID uuid = parseUuid(request.getUuid());

        org.yamcs.timeline.TimelineItem item;
        try {
            item = timelineSource.deleteItem(uuid);
        } catch (InvalidRequestException e) {
            throw new BadRequestException(e.getMessage());
        }
        if (item == null) {
            throw new NotFoundException("Item " + uuid + " not found");
        } else {
            observer.complete(item.toProtoBuf());
        }

    }

    @Override
    public void deleteTimelineGroup(Context ctx, DeleteTimelineGroupRequest request, Observer<TimelineItem> observer) {
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineSource timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : TimelineService.RDB_TIMELINE_SOURCE);
        if (!request.hasUuid()) {
            throw new BadRequestException("No uuid specified");
        }
        UUID uuid = parseUuid(request.getUuid());

        org.yamcs.timeline.TimelineItem item;
        try {
            item = timelineSource.deleteTimelineGroup(uuid);
        } catch (InvalidRequestException e) {
            throw new BadRequestException(e.getMessage());
        }
        if (item == null) {
            throw new NotFoundException("Item " + uuid + " not found");
        } else {
            observer.complete(item.toProtoBuf());
        }

    }

    private TimelineSource verifySource(TimelineService timelineService, String source) {
        TimelineSource ts = timelineService.getSource(source);
        if(ts == null) {
            throw new BadRequestException("Invalid source '" + source + "'");
        }
        return ts;
    }

    private TimelineService verifyService(String yamcsInstance) {
        String instance = ManagementApi.verifyInstance(yamcsInstance);

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

    private org.yamcs.timeline.TimelineItem req2Item(AddItemRequest request) {
        if (!request.hasType()) {
            throw new BadRequestException("Type is mandatory");
        }
        TimelineItemType type = request.getType();
        org.yamcs.timeline.TimelineItem item;

        switch (type) {
            case EVENT:
                TimelineEvent event = new TimelineEvent(UUID.randomUUID());
                item = event;
                break;
            case ITEM_GROUP:
                ItemGroup itemGroup = new ItemGroup(UUID.randomUUID());
                item = itemGroup;
                break;
            case MANUAL_ACTIVITY:
                ManualActivity manualActivity = new ManualActivity(UUID.randomUUID());
                item = manualActivity;
                break;
            case AUTO_ACTIVITY:
                AutomatedActivity autoActivity = new AutomatedActivity(UUID.randomUUID());
                item = autoActivity;
                break;
            case ACTIVITY_GROUP:
                ActivityGroup activityGroup = new ActivityGroup(UUID.randomUUID());
                item = activityGroup;
                break;
            default:
                throw new InternalServerErrorException("Unknown item type " + type);
        }

        if (request.hasStart()) {
            if (request.hasRelativeTime()) {
                throw new BadRequestException("Cannot specify both start and relative time");
            }
            item.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));

        } else if (request.hasRelativeTime()) {
            RelativeTime relt = request.getRelativeTime();
            if (!relt.hasUuid()) {
                throw new BadRequestException("uuid is required in the relative time");
            }
            if (!relt.hasRelativeStart()) {
                throw new BadRequestException("relative start is required in the relative time");
            }
            item.setRelativeItemUuid(parseUuid(relt.getUuid()));
            item.setRelativeStart(Durations.toMillis(relt.getRelativeStart()));
        } else {
            throw new BadRequestException("One of start or relativeTime has to be specified");
        }
        if (!request.hasDuration()) {
            throw new BadRequestException("Duration is mandatory");
        }
        item.setDuration(Durations.toMillis(request.getDuration()));

        if (request.hasGroupUuid()) {
            item.setGroupUuid(parseUuid(request.getGroupUuid()));
        }
        item.setTags(request.getTagsList());
        return item;
    }

    private TimelineBand req2Band(AddBandRequest request, String user) {
        return TimelineBand.newBuilder()
                .setUuid(UUID.randomUUID().toString())
                .setType(request.getType())
                .setName(request.getName())
                .setDescription(request.getDescription())
                .setSource(request.getSource())
                .setShared(request.getShared())
                .setUsername(user)
                .putAllProperties(request.getPropertiesMap())
                .addAllTags(request.getTagsList()).build();
    }

    private TimelineView req2View(AddViewRequest request) {
        return TimelineView.newBuilder()
                .setUuid(UUID.randomUUID().toString())
                .setName(request.getName())
                .setDescription(request.getDescription())
                .addAllBands(request.getBandsList()).build();
    }


    private static UUID parseUuid(String uuid) {
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid uuid '" + uuid + "'");
        }
    }

}
