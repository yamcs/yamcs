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
import org.yamcs.http.NotFoundException;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.timeline.AbstractTimelineApi;
import org.yamcs.protobuf.timeline.AddBandRequest;
import org.yamcs.protobuf.timeline.AddItemLogRequest;
import org.yamcs.protobuf.timeline.AddViewRequest;
import org.yamcs.protobuf.timeline.CreateItemRequest;
import org.yamcs.protobuf.timeline.DeleteBandRequest;
import org.yamcs.protobuf.timeline.DeleteItemRequest;
import org.yamcs.protobuf.timeline.DeleteTimelineGroupRequest;
import org.yamcs.protobuf.timeline.DeleteViewRequest;
import org.yamcs.protobuf.timeline.EndActivityRequest;
import org.yamcs.protobuf.timeline.GetBandRequest;
import org.yamcs.protobuf.timeline.GetItemLogRequest;
import org.yamcs.protobuf.timeline.GetItemRequest;
import org.yamcs.protobuf.timeline.GetViewRequest;
import org.yamcs.protobuf.timeline.ListBandsRequest;
import org.yamcs.protobuf.timeline.ListBandsResponse;
import org.yamcs.protobuf.timeline.ListItemsRequest;
import org.yamcs.protobuf.timeline.ListItemsResponse;
import org.yamcs.protobuf.timeline.ListSourcesRequest;
import org.yamcs.protobuf.timeline.ListSourcesResponse;
import org.yamcs.protobuf.timeline.ListTimelineTagsRequest;
import org.yamcs.protobuf.timeline.ListTimelineTagsResponse;
import org.yamcs.protobuf.timeline.ListViewsRequest;
import org.yamcs.protobuf.timeline.ListViewsResponse;
import org.yamcs.protobuf.timeline.LogEntry;
import org.yamcs.protobuf.timeline.StartActivityRequest;
import org.yamcs.protobuf.timeline.TimelineBand;
import org.yamcs.protobuf.timeline.TimelineItem;
import org.yamcs.protobuf.timeline.TimelineItemLog;
import org.yamcs.protobuf.timeline.TimelineView;
import org.yamcs.protobuf.timeline.UpdateBandRequest;
import org.yamcs.protobuf.timeline.UpdateItemRequest;
import org.yamcs.protobuf.timeline.UpdateViewRequest;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.timeline.BandListener;
import org.yamcs.timeline.RetrievalFilter;
import org.yamcs.timeline.ItemListener;
import org.yamcs.timeline.TimelineSource;
import org.yamcs.timeline.TimelineBandDb;
import org.yamcs.timeline.TimelineService;
import org.yamcs.timeline.TimelineViewDb;
import org.yamcs.timeline.ViewListener;
import org.yamcs.timeline.db.TimelineItemDb;
import org.yamcs.utils.InvalidRequestException;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimeInterval;

public class TimelineApi extends AbstractTimelineApi<Context> {
    static final String MSG_NO_ID = "No id specified";

    private static final Log log = new Log(TimelineApi.class);

    @Override
    public void createItem(Context ctx, CreateItemRequest request, Observer<TimelineItem> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineSource timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : TimelineService.RDB_TIMELINE_SOURCE);

        try {
            var item = timelineSource.createItem(ctx.user.getName(), request);
            observer.complete(item.toProtoBuf(true));
        } catch (InvalidRequestException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public void getItem(Context ctx, GetItemRequest request, Observer<TimelineItem> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineSource timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : TimelineService.RDB_TIMELINE_SOURCE);

        if (!request.hasId()) {
            throw new BadRequestException(MSG_NO_ID);
        }
        String id = request.getId();
        var item = timelineSource.getItem(id);
        if (item == null) {
            throw new NotFoundException(MSG_ITEM_NOT_FOUND(id));
        } else {
            observer.complete(item.toProtoBuf(true));
        }
    }

    @Override
    public void updateItem(Context ctx, UpdateItemRequest request, Observer<TimelineItem> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineSource timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : TimelineService.RDB_TIMELINE_SOURCE);

        if (!request.hasId()) {
            throw new BadRequestException(MSG_NO_ID);
        }

        try {
            var item = timelineSource.updateItem(ctx.user.getName(), request);
            observer.complete(item.toProtoBuf(true));
        } catch (InvalidRequestException e) {
            throw new BadRequestException(e.getMessage());
        }
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

        TimelineSource timelineSource;
        RetrievalFilter filter;
        String source;

        if (request.hasBand()) {
            UUID bandId = UUID.fromString(request.getBand());
            var band = timelineService.getTimelineBandDb().getBand(bandId);
            source = band.getSource() != null ? band.getSource()
                    : request.hasSource() ? request.getSource() : TimelineService.RDB_TIMELINE_SOURCE;

            filter = new RetrievalFilter(interval, band.getItemFilters());
            filter.setTags(band.getTags());
        } else {
            source = request.hasSource() ? request.getSource() : TimelineService.RDB_TIMELINE_SOURCE;
            filter = new RetrievalFilter(interval, request.getFiltersList());
        }
        timelineSource = verifySource(timelineService, source);

        ListItemsResponse.Builder resp = ListItemsResponse.newBuilder().setSource(source);

        timelineSource.getItems(limit, next, filter, new ItemListener() {
            @Override
            public void next(org.yamcs.timeline.TimelineItem item) {
                resp.addItems(item.toProtoBuf(details));
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
    public void getItemLog(Context ctx, GetItemLogRequest request, Observer<TimelineItemLog> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineSource timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : TimelineService.RDB_TIMELINE_SOURCE);

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
        TimelineSource timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : TimelineService.RDB_TIMELINE_SOURCE);

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
    public void updateBand(Context ctx, UpdateBandRequest request, Observer<TimelineBand> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineBandDb timelineBandDb = timelineService.getTimelineBandDb();

        UUID uuid = verifyUuid(request.hasId(), request.getId());

        org.yamcs.timeline.TimelineBand band = verifyBand(timelineService, uuid);

        if (request.hasName()) {
            band.setName(request.getName());
        }
        if (request.hasDescription()) {
            band.setDescription(request.getDescription());
        }
        if (request.hasSource()) {
            verifySource(timelineService, request.getSource());
            band.setSource(request.getSource());
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
        UUID uuid = verifyUuid(request.hasId(), request.getId());
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
    public void updateView(Context ctx, UpdateViewRequest request, Observer<TimelineView> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineViewDb timelineViewDb = timelineService.getTimelineViewDb();

        UUID uuid = verifyUuid(request.hasId(), request.getId());

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
        TimelineSource timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : TimelineService.RDB_TIMELINE_SOURCE);
        UUID uuid = verifyUuid(request.hasId(), request.getId());

        org.yamcs.timeline.db.AbstractItem item;
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
    public void startActivity(Context ctx, StartActivityRequest request, Observer<TimelineItem> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineSource timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : TimelineService.RDB_TIMELINE_SOURCE);

        var item = timelineSource.getItem(request.getId());
    }

    @Override
    public void endActivity(Context ctx, EndActivityRequest request, Observer<TimelineItem> observer) {
        // TODO Auto-generated method stub

    }

    @Override
    public void deleteTimelineGroup(Context ctx, DeleteTimelineGroupRequest request, Observer<TimelineItem> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeline);
        TimelineService timelineService = verifyService(request.getInstance());
        TimelineSource timelineSource = verifySource(timelineService,
                request.hasSource() ? request.getSource() : TimelineService.RDB_TIMELINE_SOURCE);
        UUID uuid = verifyUuid(request.hasId(), request.getId());

        org.yamcs.timeline.db.AbstractItem item;
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

    // NOTE: the checkSource is to warn users to set a source for the band because in older versions of Yamcs this was
    // not done. The check should disappear in the future.
    private org.yamcs.timeline.TimelineBand verifyBand(TimelineService timelineService, UUID bandId) {
        var band = timelineService.getTimelineBandDb().getBand(bandId);
        if (band == null) {
            throw new NotFoundException(MSG_BAND_NOT_FOUND(bandId.toString()));
        }
        return band;
    }

    private TimelineSource verifySource(TimelineService timelineService, String source) {
        TimelineSource ts = timelineService.getSource(source);
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

    private org.yamcs.timeline.TimelineBand req2Band(AddBandRequest request, String user) {
        org.yamcs.timeline.TimelineBand band = new org.yamcs.timeline.TimelineBand(UUID.randomUUID());

        if (!request.hasType()) {
            throw new BadRequestException("Type is mandatory");
        }
        band.setType(request.getType());

        if (request.hasSource()) {
            band.setSource(request.getSource());
        }

        if (request.hasName()) {
            band.setName(request.getName());
        }
        if (request.hasDescription()) {
            band.setDescription(request.getDescription());
        }
        band.setShared(request.getShared());
        band.setUsername(user);
        band.setTags(request.getTagsList());

        band.setItemFilters(request.getFiltersList());
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

    private static UUID verifyUuid(boolean hasId, String uuid) {
        if (!hasId) {
            throw new BadRequestException(MSG_NO_ID);
        }
        return parseUuid(uuid);

    }

    public static UUID parseUuid(String uuid) {
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
