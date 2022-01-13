package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.yamcs.YamcsServer;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.AbstractTimelineApi;
import org.yamcs.protobuf.AddBandRequest;
import org.yamcs.protobuf.AddViewRequest;
import org.yamcs.protobuf.CreateItemRequest;
import org.yamcs.protobuf.DeleteBandRequest;
import org.yamcs.protobuf.DeleteItemRequest;
import org.yamcs.protobuf.DeleteTimelineGroupRequest;
import org.yamcs.protobuf.DeleteViewRequest;
import org.yamcs.protobuf.GetBandRequest;
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
import org.yamcs.protobuf.RelativeTime;
import org.yamcs.protobuf.TimelineBand;
import org.yamcs.protobuf.TimelineItem;
import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.protobuf.TimelineView;
import org.yamcs.protobuf.UpdateBandRequest;
import org.yamcs.protobuf.UpdateItemRequest;
import org.yamcs.protobuf.UpdateViewRequest;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.timeline.ActivityGroup;
import org.yamcs.timeline.AutomatedActivity;
import org.yamcs.timeline.BandListener;
import org.yamcs.timeline.ItemFilter;
import org.yamcs.timeline.ItemGroup;
import org.yamcs.timeline.ItemListener;
import org.yamcs.timeline.ItemProvider;
import org.yamcs.timeline.ManualActivity;
import org.yamcs.timeline.TimelineBandDb;
import org.yamcs.timeline.TimelineEvent;
import org.yamcs.timeline.TimelineService;
import org.yamcs.timeline.TimelineViewDb;
import org.yamcs.timeline.ViewListener;
import org.yamcs.utils.InvalidRequestException;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimeInterval;

import com.google.protobuf.util.Durations;

public class TimelineApi extends AbstractTimelineApi<Context> {
    static final String MSG_NO_ID = "No id specified";


    private static final Log log = new Log(TimelineApi.class);

    @Override
    public void createItem(Context ctx, CreateItemRequest request, Observer<TimelineItem> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        ItemProvider timelineSource = verifySource(timelineService,
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
        ctx.checkSystemPrivilege(SystemPrivilege.ReadTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        ItemProvider timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : TimelineService.RDB_TIMELINE_SOURCE);

        if (!request.hasId()) {
            throw new BadRequestException(MSG_NO_ID);
        }
        String id = request.getId();
        org.yamcs.timeline.TimelineItem item = timelineSource.getItem(id);
        if (item == null) {
            throw new NotFoundException(MSG_ITEM_NOT_FOUND(id));
        } else {
            observer.complete(item.toProtoBuf());
        }
    }

    @Override
    public void updateItem(Context ctx, UpdateItemRequest request, Observer<TimelineItem> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        ItemProvider timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : TimelineService.RDB_TIMELINE_SOURCE);

        if (!request.hasId()) {
            throw new BadRequestException(MSG_NO_ID);
        }

        String id = request.getId();
        org.yamcs.timeline.TimelineItem item = timelineSource.getItem(id);
        if (item == null) {
            throw new NotFoundException(MSG_ITEM_NOT_FOUND(id));
        }

        if (request.hasName()) {
            item.setName(request.getName());
        }

        if (request.hasStart()) {
            if (request.hasRelativeTime()) {
                throw new BadRequestException("Cannot specify both start and relative time");
            }
            item.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));

        } else if (request.hasRelativeTime()) {
            RelativeTime relt = request.getRelativeTime();
            if (!relt.hasRelto()) {
                throw new BadRequestException("relto item is required with relative time");
            }
            if (!relt.hasRelativeStart()) {
                throw new BadRequestException("relative start is required in the relative time");
            }
            item.setRelativeItemUuid(parseUuid(relt.getRelto()));
            item.setRelativeStart(Durations.toMillis(relt.getRelativeStart()));
        } else {
            throw new BadRequestException("One of start or relativeTime has to be specified");
        }
        if (request.hasDuration()) {
            item.setDuration(Durations.toMillis(request.getDuration()));
        }

        item.setGroupUuid(request.hasGroupId() ? parseUuid(request.getGroupId()) : null);
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
        ctx.checkSystemPrivilege(SystemPrivilege.ReadTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        ItemProvider timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : TimelineService.RDB_TIMELINE_SOURCE);

        String next = request.hasNext() ? request.getNext() : null;
        int limit = request.hasLimit() ? request.getLimit() : 500;
        TimeInterval interval = new TimeInterval();
        if (request.hasStart()) {
            interval.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));
        }
        if (request.hasStop()) {
            interval.setEnd(TimeEncoding.fromProtobufTimestamp(request.getStop()));
        }
        ItemFilter filter = new ItemFilter(interval);
        if (request.hasBand()) {
            UUID bandId = UUID.fromString(request.getBand());
            org.yamcs.timeline.TimelineBand band = timelineService.getTimelineBandDb().getBand(bandId);
            filter.setTags(band.getTags());
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
        ctx.checkSystemPrivilege(SystemPrivilege.ReadTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        ListSourcesResponse.Builder lsrb = ListSourcesResponse.newBuilder().putAllSources(timelineService.getSources());
        observer.complete(lsrb.build());
    }

    @Override
    public void listTags(Context ctx, ListTimelineTagsRequest request, Observer<ListTimelineTagsResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        ItemProvider timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : TimelineService.RDB_TIMELINE_SOURCE);
        ListTimelineTagsResponse.Builder responseb = ListTimelineTagsResponse.newBuilder()
                .addAllTags(timelineSource.getTags());
        observer.complete(responseb.build());

    }

    @Override
    public void addBand(Context ctx, AddBandRequest request, Observer<TimelineBand> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineBandDb timelineBandDb = timelineService.getTimelineBandDb();
        org.yamcs.timeline.TimelineBand band = req2Band(request, ctx.user.getName());
        try {
            band = timelineBandDb.addBand(band);
            observer.complete(band.toProtobuf());
        } catch (InvalidRequestException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public void getBand(Context ctx, GetBandRequest request, Observer<TimelineBand> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineBandDb timelineBandDb = timelineService.getTimelineBandDb();
        if (!request.hasId()) {
            throw new BadRequestException(MSG_NO_ID);
        }
        UUID uuid = parseUuid(request.getId());
        org.yamcs.timeline.TimelineBand band = timelineBandDb.getBand(uuid);
        if (band == null) {
            throw new NotFoundException(MSG_ITEM_NOT_FOUND(request.getId()));
        } else {
            observer.complete(band.toProtobuf());
        }
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
    public void updateBand(Context ctx, UpdateBandRequest request, Observer<TimelineBand> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineBandDb timelineBandDb = timelineService.getTimelineBandDb();

        if (!request.hasId()) {
            throw new BadRequestException(MSG_NO_ID);
        }
        UUID uuid = parseUuid(request.getId());

        org.yamcs.timeline.TimelineBand band = timelineBandDb.getBand(uuid);
        if (band == null) {
            throw new NotFoundException(MSG_BAND_NOT_FOUND(request.getId()));
        }

        if (request.hasName()) {
            band.setName(request.getName());
        }
        if (request.hasDescription()) {
            band.setDescription(request.getDescription());
        }
        band.setTags(request.getTagsList());
        band.setProperties(request.getPropertiesMap());

        try {
            band = timelineBandDb.updateBand(band);
            observer.complete(band.toProtobuf());
        } catch (InvalidRequestException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public void deleteBand(Context ctx, DeleteBandRequest request, Observer<TimelineBand> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineBandDb timelineBandDb = timelineService.getTimelineBandDb();

        if (!request.hasId()) {
            throw new BadRequestException(MSG_NO_ID);
        }
        UUID uuid = parseUuid(request.getId());

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
    public void addView(Context ctx, AddViewRequest request, Observer<TimelineView> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineViewDb timelineViewDb = timelineService.getTimelineViewDb();
        org.yamcs.timeline.TimelineView view = req2View(request);
        try {
            view = timelineViewDb.addView(view);
            observer.complete(enrichView(timelineService, view));
        } catch (InvalidRequestException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public void getView(Context ctx, GetViewRequest request, Observer<TimelineView> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineViewDb timelineViewDb = timelineService.getTimelineViewDb();
        if (!request.hasId()) {
            throw new BadRequestException(MSG_NO_ID);
        }
        UUID uuid = parseUuid(request.getId());
        org.yamcs.timeline.TimelineView view = timelineViewDb.getView(uuid);
        if (view == null) {
            throw new NotFoundException(MSG_ITEM_NOT_FOUND(request.getId()));
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
                log.warn("Error retrieving timeline items", t);
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
    public void updateView(Context ctx, UpdateViewRequest request, Observer<TimelineView> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineViewDb timelineViewDb = timelineService.getTimelineViewDb();

        if (!request.hasId()) {
            throw new BadRequestException(MSG_NO_ID);
        }
        UUID uuid = parseUuid(request.getId());

        org.yamcs.timeline.TimelineView view = timelineViewDb.getView(uuid);
        if (view == null) {
            throw new NotFoundException(MSG_VIEW_NOT_FOUND(request.getId()));
        }

        if (request.hasName()) {
            view.setName(request.getName());
        }
        if (request.hasDescription()) {
            view.setDescription(request.getDescription());
        }
        view.setBands(request.getBandsList().stream()
                .map(id -> UUID.fromString(id))
                .collect(Collectors.toList()));

        try {
            view = timelineViewDb.updateView(view);
            observer.complete(enrichView(timelineService, view));
        } catch (InvalidRequestException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public void deleteView(Context ctx, DeleteViewRequest request, Observer<TimelineView> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineViewDb timelineViewDb = timelineService.getTimelineViewDb();

        if (!request.hasId()) {
            throw new BadRequestException(MSG_NO_ID);
        }
        UUID uuid = parseUuid(request.getId());

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
                request.hasSource() ? request.getSource() : TimelineService.RDB_TIMELINE_SOURCE);
        if (!request.hasId()) {
            throw new BadRequestException(MSG_NO_ID);
        }
        UUID uuid = parseUuid(request.getId());

        org.yamcs.timeline.TimelineItem item;
        try {
            item = timelineSource.deleteItem(uuid);
        } catch (InvalidRequestException e) {
            throw new BadRequestException(e.getMessage());
        }
        if (item == null) {
            throw new NotFoundException(MSG_ITEM_NOT_FOUND(request.getId()));
        } else {
            observer.complete(item.toProtoBuf());
        }

    }

    @Override
    public void deleteTimelineGroup(Context ctx, DeleteTimelineGroupRequest request, Observer<TimelineItem> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        ItemProvider timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : TimelineService.RDB_TIMELINE_SOURCE);
        if (!request.hasId()) {
            throw new BadRequestException("No uuid specified");
        }
        UUID uuid = parseUuid(request.getId());

        org.yamcs.timeline.TimelineItem item;
        try {
            item = timelineSource.deleteTimelineGroup(uuid);
        } catch (InvalidRequestException e) {
            throw new BadRequestException(e.getMessage());
        }
        if (item == null) {
            throw new NotFoundException(MSG_ITEM_NOT_FOUND(request.getId()));
        } else {
            observer.complete(item.toProtoBuf());
        }

    }

    private ItemProvider verifySource(TimelineService timelineService, String source) {
        ItemProvider ts = timelineService.getSource(source);
        if (ts == null) {
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

    private org.yamcs.timeline.TimelineItem req2Item(CreateItemRequest request) {
        if (!request.hasType()) {
            throw new BadRequestException("Type is mandatory");
        }
        TimelineItemType type = request.getType();
        org.yamcs.timeline.TimelineItem item;

        UUID newId = UUID.randomUUID();
        switch (type) {
        case EVENT:
            TimelineEvent event = new TimelineEvent(newId.toString());
            item = event;
            break;
        case ITEM_GROUP:
            ItemGroup itemGroup = new ItemGroup(newId);
            item = itemGroup;
            break;
        case MANUAL_ACTIVITY:
            ManualActivity manualActivity = new ManualActivity(newId);
            item = manualActivity;
            break;
        case AUTO_ACTIVITY:
            AutomatedActivity autoActivity = new AutomatedActivity(newId);
            item = autoActivity;
            break;
        case ACTIVITY_GROUP:
            ActivityGroup activityGroup = new ActivityGroup(newId);
            item = activityGroup;
            break;
        default:
            throw new InternalServerErrorException("Unknown item type " + type);
        }

        if (request.hasName()) {
            item.setName(request.getName());
        }

        if (request.hasStart()) {
            if (request.hasRelativeTime()) {
                throw new BadRequestException("Cannot specify both start and relative time");
            }
            item.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));

        } else if (request.hasRelativeTime()) {
            RelativeTime relt = request.getRelativeTime();
            if (!relt.hasRelto()) {
                throw new BadRequestException("relto item is required when using relative time");
            }
            if (!relt.hasRelativeStart()) {
                throw new BadRequestException("relative start is required when using relative time");
            }
            item.setRelativeItemUuid(parseUuid(relt.getRelto()));
            item.setRelativeStart(Durations.toMillis(relt.getRelativeStart()));
        } else {
            throw new BadRequestException("One of start or relativeTime has to be specified");
        }
        if (!request.hasDuration()) {
            throw new BadRequestException("Duration is mandatory");
        }
        item.setDuration(Durations.toMillis(request.getDuration()));

        if (request.hasGroupId()) {
            item.setGroupUuid(parseUuid(request.getGroupId()));
        }
        item.setTags(request.getTagsList());
        return item;
    }

    private org.yamcs.timeline.TimelineBand req2Band(AddBandRequest request, String user) {
        org.yamcs.timeline.TimelineBand band = new org.yamcs.timeline.TimelineBand(UUID.randomUUID());

        if (!request.hasType()) {
            throw new BadRequestException("Type is mandatory");
        }
        band.setType(request.getType());

        if (request.hasName()) {
            band.setName(request.getName());
        }
        if (request.hasDescription()) {
            band.setDescription(request.getDescription());
        }
        band.setShared(request.getShared());
        band.setUsername(user);
        band.setTags(request.getTagsList());
        band.setProperties(request.getPropertiesMap());

        return band;
    }

    private org.yamcs.timeline.TimelineView req2View(AddViewRequest request) {
        List<UUID> bands = request.getBandsList().stream()
                .map(id -> UUID.fromString(id))
                .collect(Collectors.toList());

        org.yamcs.timeline.TimelineView view = new org.yamcs.timeline.TimelineView(UUID.randomUUID());
        view.setName(request.getName());
        if (view.toProtobuf().hasDescription()) {
            view.setDescription(request.getDescription());
        }
        view.setBands(bands);
        return view;
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
