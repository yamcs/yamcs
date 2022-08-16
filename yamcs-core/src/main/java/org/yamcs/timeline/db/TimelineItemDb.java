package org.yamcs.timeline.db;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.yamcs.InitException;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.api.TimelineApi;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.timeline.CreateItemRequest;
import org.yamcs.protobuf.timeline.ItemFilter;
import org.yamcs.protobuf.timeline.LogEntry;
import org.yamcs.protobuf.timeline.RelativeTime;
import org.yamcs.protobuf.timeline.TimelineSourceCapabilities;
import org.yamcs.protobuf.timeline.UpdateItemRequest;
import org.yamcs.protobuf.timeline.ItemFilter.FilterCriterion;
import org.yamcs.timeline.FilterMatcher;
import org.yamcs.timeline.ItemListener;
import org.yamcs.timeline.RetrievalFilter;
import org.yamcs.timeline.TimelineSource;
import org.yamcs.protobuf.timeline.TimelineItemLog;
import org.yamcs.protobuf.timeline.TimelineItemType;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.InvalidRequestException;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.TimeInterval;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.SqlBuilder;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableColumnDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.ResultListener;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;
import org.yamcs.yarch.streamsql.StreamSqlStatement;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.protobuf.util.Durations;

public class TimelineItemDb implements TimelineSource {
    static final Random random = new Random();
    public static final TupleDefinition TIMELINE_DEF = new TupleDefinition();
    public static final String CNAME_START = "start";
    public static final String CNAME_DURATION = "duration";
    public static final String CNAME_ID = "uuid";
    public static final String CNAME_NAME = "name";
    public static final String CNAME_TYPE = "type";
    public static final String CNAME_STATUS = "status";
    public static final String CNAME_TAGS = "tags";
    public static final String CNAME_GROUP_ID = "group_id";
    public static final String CNAME_RELTIME_ID = "reltime_id";
    public static final String CNAME_RELTIME_START = "reltime_start";
    public static final String CNAME_DESCRIPTION = "description";
    public static final String CNAME_FAILURE_REASON = "failure_reason";
    public static final String CNAME_ACTUAL_START = "actual_start";
    public static final String CNAME_ACTUAL_END = "actual_end";
    public static final String CNAME_ALLOW_EARLY_START = "allow_early_start";
    public static final String CNAME_AUTO_RUN = "auto_run";
    public static final String CNAME_ACTIVITY_RUN_ID = "activity_run_id";

    // used to build a secondary index over all items ids one item may depend on
    // that includes the relative start id and the activity dependencies
    public static final String CNAME_DEPS = "deps";

    public static final String CRIT_KEY_TAG = "tag";

    static {
        TIMELINE_DEF.addColumn(CNAME_START, DataType.TIMESTAMP);
        TIMELINE_DEF.addColumn(CNAME_DURATION, DataType.LONG);
        TIMELINE_DEF.addColumn(CNAME_ID, DataType.UUID);
        TIMELINE_DEF.addColumn(CNAME_NAME, DataType.STRING);
        TIMELINE_DEF.addColumn(CNAME_TYPE, DataType.ENUM);
        TIMELINE_DEF.addColumn(CNAME_TAGS, DataType.array(DataType.ENUM));
        TIMELINE_DEF.addColumn(CNAME_GROUP_ID, DataType.UUID);
        TIMELINE_DEF.addColumn(CNAME_RELTIME_ID, DataType.UUID);
        TIMELINE_DEF.addColumn(CNAME_RELTIME_START, DataType.LONG);
        TIMELINE_DEF.addColumn(CNAME_DEPS, DataType.array(DataType.UUID));

    }
    final Log log;
    final private ReadWriteLock rwlock = new ReentrantReadWriteLock();
    final static String TABLE_NAME = "timeline";

    final YarchDatabaseInstance ydb;
    final Stream timelineStream;
    final TupleMatcher matcher;
    final TimelineItemLogDb logDb;

    LoadingCache<UUID, AbstractItem> itemCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(
                    new CacheLoader<UUID, AbstractItem>() {
                        @Override
                        public AbstractItem load(UUID uuid) {
                            return doGetItem(uuid);
                        }
                    });

    public TimelineItemDb(String yamcsInstance) throws InitException {
        log = new Log(getClass(), yamcsInstance);

        ydb = YarchDatabase.getInstance(yamcsInstance);
        try {
            timelineStream = setupTimelineRecording();
        } catch (ParseException | StreamSqlException e) {
            throw new InitException(e);
        }

        logDb = new TimelineItemLogDb(yamcsInstance);
        matcher = new TupleMatcher();
    }

    private Stream setupTimelineRecording() throws StreamSqlException, ParseException {
        String streamName = TABLE_NAME + "_in";
        if (ydb.getTable(TABLE_NAME) == null) {
            String query = "create table " + TABLE_NAME + "(" + TIMELINE_DEF.getStringDefinition1()
                    + ", primary key(start, uuid), index(deps))";
            ydb.execute(query);
        }
        if (ydb.getStream(streamName) == null) {
            ydb.execute("create stream " + streamName + TIMELINE_DEF.getStringDefinition());
        }
        ydb.execute("upsert into " + TABLE_NAME + " select * from " + streamName);
        return ydb.getStream(streamName);
    }

    @Override
    public AbstractItem createItem(String username, CreateItemRequest request) {
        AbstractItem item = req2Item(request);

        rwlock.writeLock().lock();
        try {
            if (item.getRelativeItemUuid() != null) {
                AbstractItem relItem = fromCache(item.getRelativeItemUuid());
                if (relItem == null) {
                    throw new InvalidRequestException(
                            "Referenced relative item uuid " + item.getRelativeItemUuid() + " does not exist");
                }
                item.setStart(relItem.getStart() + item.getRelativeStart());
            }
            if (item.getGroupUuid() != null) {
                AbstractItem groupItem = fromCache(item.getGroupUuid());
                if (groupItem == null) {
                    throw new InvalidRequestException(
                            "Referenced group item uuid " + item.getGroupUuid() + " does not exist");
                }
                if (!(groupItem instanceof ActivityGroup || groupItem instanceof ItemGroup)) {
                    throw new InvalidRequestException(
                            "Assigned group " + groupItem.getId() + " is not a real group");
                }
                if (groupItem instanceof ActivityGroup
                        && !((item instanceof ManualActivity) || (item instanceof AutomatedActivity))) {
                    throw new InvalidRequestException(
                            "An activity group " + groupItem.getId() + " can only contain activity items");
                }
            }
            Tuple tuple = item.toTuple();
            log.debug("Adding timeline item to RDB: {}", tuple);
            timelineStream.emitTuple(tuple);
            return item;
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    @Override
    public AbstractItem updateItem(String username, UpdateItemRequest request) {
        List<String> changeList = new ArrayList<>();
        AbstractItem item;

        rwlock.writeLock().lock();
        try {
            item = verifyItem(request.getId());
            updatetem(item, request, changeList);

            if (item.getRelativeItemUuid() != null) {
                AbstractItem relItem = fromCache(item.getRelativeItemUuid());
                if (relItem == null) {
                    throw new InvalidRequestException(
                            "Referenced relative item uuid " + item.getRelativeItemUuid() + " does not exist");
                }
                verifyRelTimeCircularity(item.getUuid(), relItem);
                item.setStart(relItem.getStart() + item.getRelativeStart());
            }

            if (item.getGroupUuid() != null) {
                AbstractItem groupItem = fromCache(item.getGroupUuid());
                if (groupItem == null) {
                    throw new InvalidRequestException(
                            "Referenced group item uuid " + item.getGroupUuid() + " does not exist");
                }
                if (!(groupItem instanceof ActivityGroup || groupItem instanceof ItemGroup)) {
                    throw new InvalidRequestException(
                            "Assigned group " + groupItem.getId() + " is not a real group");
                }
                if (groupItem instanceof ActivityGroup
                        && !((item instanceof ManualActivity) || (item instanceof AutomatedActivity))) {
                    throw new InvalidRequestException(
                            "An activity group " + groupItem.getId() + " can only contain activity items");
                }
                verifyGroupCircularity(item.getUuid(), groupItem);
            }
            doDeleteItem(item.getUuid());

            Tuple tuple = item.toTuple();
            log.debug("Updating timeline item in RDB: {}", tuple);
            timelineStream.emitTuple(tuple);

            updateDependentStart(item);
        } finally {
            rwlock.writeLock().unlock();
        }

        logDb.addLogEntry(item.getUuid(), LogEntry.newBuilder().setUser(username).setType("update")
                .setMsg(changeList.toString()).build());

        return item;
    }

    // update the start time of all items having their time specified as relative to this
    private void updateDependentStart(AbstractItem item) {
        String query = "select * from " + TABLE_NAME + " where " + CNAME_DEPS + " &&  ? ";
        StreamSqlResult r = ydb.executeUnchecked(query, Collections.singletonList(item.getUuid()));
        while (r.hasNext()) {
            AbstractItem item1 = AbstractItem.fromTuple(r.next());
            if (item.getUuid().equals(item1.getRelativeItemUuid())) {
                item1.setStart(item.getStart() + item1.getRelativeStart());
                doDeleteItem(item1.getUuid());
                Tuple tuple = item1.toTuple();
                log.debug("Updating timeline item start in RDB: {}", tuple);
                timelineStream.emitTuple(tuple);
            }
        }
        r.close();
    }

    private void verifyRelTimeCircularity(UUID uuid, AbstractItem relItem) {
        if (uuid.toString().equals(relItem.getId())) {
            throw new InvalidRequestException("Circular relative time reference for " + uuid);
        }

        if (relItem.getRelativeItemUuid() != null) {
            AbstractItem relItem1 = fromCache(relItem.getRelativeItemUuid());
            if (relItem1 == null) {
                throw new DatabaseCorruptionException("timeline item " + relItem.getRelativeItemUuid()
                        + " time referenced by " + relItem.getId() + " does not exist");
            }
            verifyRelTimeCircularity(uuid, relItem1);
        }
    }

    private void verifyGroupCircularity(UUID uuid, AbstractItem groupItem) {
        if (uuid.toString().equals(groupItem.getId())) {
            throw new InvalidRequestException("Circular relative time reference for " + uuid);
        }

        if (groupItem.getGroupUuid() != null) {
            AbstractItem groupItem1 = fromCache(groupItem.getGroupUuid());
            if (groupItem1 == null) {
                throw new DatabaseCorruptionException("timeline item " + groupItem.getGroupUuid()
                        + " group referenced by " + groupItem.getId() + " does not exist");
            }
            verifyGroupCircularity(uuid, groupItem1);
        }
    }

    private AbstractItem doGetItem(UUID uuid) {
        StreamSqlResult r = ydb.executeUnchecked("select * from " + TABLE_NAME + " where uuid = ?", uuid);
        try {
            if (r.hasNext()) {
                Tuple tuple = r.next();
                try {
                    AbstractItem item = AbstractItem.fromTuple(tuple);
                    log.trace("Read item from db {}", item);
                    return item;
                } catch (Exception e) {
                    log.error("Cannot decode tuple {} intro timeline item", tuple);
                }
            }
        } finally {
            r.close();
        }

        throw new NoSuchItemException();
    }

    @Override
    public AbstractItem getItem(String id) {
        UUID uuid = verifyUuid(id);

        rwlock.readLock().lock();
        try {
            return fromCache(uuid);
        } finally {
            rwlock.readLock().unlock();
        }
    }

    @Override
    public AbstractItem deleteItem(UUID uuid) {
        rwlock.writeLock().lock();
        try {
            AbstractItem item = doGetItem(uuid);
            if (item == null) {
                return null;
            }

            StreamSqlResult r = ydb.executeUnchecked(
                    "select uuid from " + TABLE_NAME + " where " + CNAME_GROUP_ID + " = ?", uuid);
            try {
                if (r.hasNext()) {
                    UUID id = r.next().getColumn(CNAME_ID);
                    throw new InvalidRequestException(
                            "Cannot delete " + uuid + " because it is considered as a group by item " + id);
                }
            } finally {
                r.close();
            }

            r = ydb.executeUnchecked(
                    "select uuid from " + TABLE_NAME + " where " + CNAME_RELTIME_ID + " = ?", uuid);
            try {
                if (r.hasNext()) {
                    UUID id = r.next().getColumn(CNAME_ID);
                    r.close();
                    throw new InvalidRequestException(
                            "Cannot delete " + uuid + " because item " + id + " time depends on it");
                }
            } finally {
                r.close();
            }
            doDeleteItem(uuid);

            return item;
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    @Override
    public AbstractItem deleteTimelineGroup(UUID uuid) {
        rwlock.writeLock().lock();
        try {
            AbstractItem item = doGetItem(uuid);
            if (item == null) {
                return null;
            }

            // delete all events from the group
            StreamSqlResult r = ydb.executeUnchecked(
                    "select uuid from " + TABLE_NAME + " where " + CNAME_GROUP_ID + " = ?", uuid);
            while (r.hasNext()) {
                UUID id = r.next().getColumn(CNAME_ID);
                deleteItem(id);
            }
            r.close();

            // delete the group
            deleteItem(uuid);
            return item;
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    private void doDeleteItem(UUID uuid) {
        itemCache.invalidate(uuid);
        StreamSqlResult r = ydb.executeUnchecked("delete from " + TABLE_NAME + " where uuid = ?", uuid);
        r.close();
    }

    @Override
    public void getItems(int limit, String token, RetrievalFilter filter, ItemListener consumer) {
        rwlock.readLock().lock();
        try {
            SqlBuilder sqlBuilder = new SqlBuilder(TABLE_NAME);
            sqlBuilder.select("*");

            TimeInterval interval = filter.getTimeInterval();
            if (interval.hasEnd()) {
                sqlBuilder.where("start < ?", interval.getEnd());
            }
            if (interval.hasStart()) {
                sqlBuilder.where("start+duration > ?", interval.getStart());
            }
            List<String> tags = getTags(filter);

            if (!tags.isEmpty()) {
                sqlBuilder.where(" tags && ?", tags);
            }
            sqlBuilder.limit(limit + 1);

            StreamSqlStatement stmt = ydb.createStatement(sqlBuilder.toString(),
                    sqlBuilder.getQueryArguments().toArray());
            ydb.execute(stmt, new ResultListener() {
                int count = 0;

                @Override
                public void next(Tuple tuple) {
                    if (matcher.matches(filter, tuple)) {
                        if (count < limit) {
                            consumer.next(AbstractItem.fromTuple(tuple));
                        }
                        count++;
                    }
                }

                @Override
                public void completeExceptionally(Throwable t) {
                    consumer.completeExceptionally(t);
                }

                @Override
                public void complete() {
                    if (count == limit + 1) {
                        consumer.complete(getRandomToken());
                    } else {
                        consumer.complete(null);
                    }
                }
            });

        } catch (StreamSqlException | ParseException e) {
            log.error("Exception when executing query", e);
        } finally {
            rwlock.readLock().unlock();
        }

    }

    private List<String> getTags(RetrievalFilter filter) {
        List<String> r = new ArrayList<>();
        if (filter.getTags() != null) {
            r.addAll(filter.getTags());
        }
        if (filter.getItemFilters() != null) {
            for (ItemFilter f : filter.getItemFilters()) {
                for (var c : f.getCriteriaList()) {
                    if (CRIT_KEY_TAG.equals(c.getKey())) {
                        r.add(c.getValue());
                    }
                }
            }
        }
        return r;
    }

    private static String getRandomToken() {
        byte[] b = new byte[16];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public Collection<String> getTags() {
        rwlock.readLock().lock();
        try {
            TableColumnDefinition tcd = ydb.getTable(TABLE_NAME).getColumnDefinition(CNAME_TAGS);
            return Collections.unmodifiableSet(tcd.getEnumValues().keySet());
        } finally {
            rwlock.readLock().unlock();
        }
    }

    // returns null if uuid does not exist
    private AbstractItem fromCache(UUID uuid) {
        try {
            return itemCache.getUnchecked(uuid);
        } catch (UncheckedExecutionException e) {
            if (e.getCause() instanceof NoSuchItemException) {
                return null;
            } else {
                throw e;
            }
        }
    }

    @Override
    public TimelineSourceCapabilities getCapabilities() {
        return TimelineSourceCapabilities.newBuilder()
                .setReadOnly(false)
                .setHasActivityGroups(true)
                .setHasEventGroups(true)
                .setHasManualActivities(true)
                .setHasAutomatedActivities(true)
                .build();
    }

    @Override
    public void validateFilters(List<ItemFilter> filters) throws BadRequestException {
        for (var filter : filters) {
            for (var c : filter.getCriteriaList()) {
                if (!CRIT_KEY_TAG.equals(c.getKey())) {
                    throw new BadRequestException(
                            "Unknonw criteria key " + c.getKey() + ". Supported key: " + CRIT_KEY_TAG);
                }
            }
        }
    }

    private static class TupleMatcher extends FilterMatcher<Tuple> {
        @Override
        protected boolean criterionMatch(FilterCriterion c, Tuple tuple) {
            String cmdName = tuple.getColumn(StandardTupleDefinitions.CMDHIST_TUPLE_COL_CMDNAME);
            if (cmdName == null) {
                return false;
            }
            if (CRIT_KEY_TAG.equals(c.getKey())) {
                return cmdName.matches(c.getValue());
            } else {
                return false;
            }
        }
    }

    @Override
    public TimelineItemLog getItemLog(String id) {
        var item = verifyItem(id);
        return logDb.getLog(item.getUuid());
    }

    @Override
    public LogEntry addItemLog(String id, LogEntry entry) {
        var item = verifyItem(id);
        return logDb.addLogEntry(item.getUuid(), entry);
    }

    private AbstractItem verifyItem(String id) {
        UUID uuid = verifyUuid(id);

        var item = fromCache(uuid);
        if (item == null) {
            throw new BadRequestException("Item " + id + " does not exist");
        }
        return item;
    }

    private UUID verifyUuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid " + id + "; must be an UUID");
        }
    }

    private org.yamcs.timeline.db.AbstractItem req2Item(CreateItemRequest request) {
        if (!request.hasType()) {
            throw new BadRequestException("Type is mandatory");
        }
        TimelineItemType type = request.getType();
        org.yamcs.timeline.db.AbstractItem item;

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
            item.setRelativeItemUuid(TimelineApi.parseUuid(relt.getRelto()));
            item.setRelativeStart(Durations.toMillis(relt.getRelativeStart()));
        } else {
            throw new BadRequestException("One of start or relativeTime has to be specified");
        }
        if (!request.hasDuration()) {
            throw new BadRequestException("Duration is mandatory");
        }
        item.setDuration(Durations.toMillis(request.getDuration()));

        if (request.hasGroupId()) {
            item.setGroupUuid(TimelineApi.parseUuid(request.getGroupId()));
        }
        if (request.hasDescription()) {
            item.setDescription(request.getDescription());
        }

        item.setTags(request.getTagsList());
        return item;
    }

    private void updatetem(AbstractItem item, UpdateItemRequest request, List<String> changeList) {
        if (request.hasName() && !request.getName().equals(item.getName())) {
            item.setName(request.getName());
            changeList.add("name");
        }

        if (request.hasStart()) {
            if (request.hasRelativeStart()) {
                throw new BadRequestException("Cannot specify both start and relativeStart");
            }
            long newStart = TimeEncoding.fromProtobufTimestamp(request.getStart());
            if (item.getStart() != newStart) {
                changeList.add("start");
                item.setStart(newStart);
            }
            item.setRelativeItemUuid(null);

        } else if (request.hasRelativeStart()) {
            RelativeTime relt = request.getRelativeStart();
            if (!relt.hasRelto()) {
                throw new BadRequestException("relto item is required with relative time");
            }
            if (!relt.hasRelativeStart()) {
                throw new BadRequestException("relative start is required in the relative time");
            }
            var relto = TimelineApi.parseUuid(relt.getRelto());
            if (!relto.equals(item.getRelativeItemUuid())) {
                item.setRelativeItemUuid(relto);
                changeList.add("relativeItemUuid");
            }

            long relstart = Durations.toMillis(relt.getRelativeStart());
            if (relstart != item.getRelativeStart()) {
                item.setRelativeStart(relstart);
                changeList.add("relativeStart");
            }
        }

        if (request.hasDuration()) {
            long duration = Durations.toMillis(request.getDuration());
            if (item.getDuration() != duration) {
                item.setDuration(duration);
                changeList.add("duration");
            }
        }
        if (item instanceof Activity) {
            Activity activity = (Activity) item;
            if (request.hasStatus() && activity.getStatus() != request.getStatus()) {
                activity.setStatus(request.getStatus());
                changeList.add("status");
            }
            if (request.hasFailureReason() && !request.getFailureReason().equals(activity.getFailureReason())) {
                activity.setFailureReason(request.getFailureReason());
                changeList.add("failureReason");
            }
        }

        if (request.hasGroupId()) {
            UUID gid = request.getGroupId().isBlank() ? null : TimelineApi.parseUuid(request.getGroupId());
            if (!Objects.equals(gid, item.getGroupUuid())) {
                item.setGroupUuid(gid);
                changeList.add("groupUuid");
            }
        }
        if (request.getTagsCount() > 0) {
            List<String> tags = new ArrayList<>(request.getTagsList());
            Collections.sort(tags);
            if (!tags.equals(item.getTags())) {
                item.setTags(tags);
                changeList.add("tags");
            }
        } else if (request.hasClearTags() && request.getClearTags()) {
            if (item.getTags() != null) {
                item.setTags(null);
                changeList.add("tags");
            }
        }

    }

    @SuppressWarnings("serial")
    static class NoSuchItemException extends RuntimeException {

    }

}
