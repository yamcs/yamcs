package org.yamcs.timeline;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.yamcs.InitException;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.activities.protobuf.ActivityDefinition;
import org.yamcs.http.BadRequestException;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.ItemFilter;
import org.yamcs.protobuf.ItemFilter.FilterCriterion;
import org.yamcs.protobuf.LogEntry;
import org.yamcs.protobuf.TimelineItemLog;
import org.yamcs.protobuf.TimelineSourceCapabilities;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.InvalidRequestException;
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

public class TimelineItemDb implements ItemProvider {
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
    public static final String CNAME_ACTIVITY_DEFINITION = "activity_definition";
    public static final String CNAME_RUNS = "runs";
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
        TIMELINE_DEF.addColumn(CNAME_ACTIVITY_DEFINITION, DataType.protobuf(ActivityDefinition.class));
        TIMELINE_DEF.addColumn(CNAME_RUNS, DataType.array(DataType.UUID));
    }
    final Log log;
    final private ReadWriteLock rwlock = new ReentrantReadWriteLock();
    final static String TABLE_NAME = "timeline";

    final YarchDatabaseInstance ydb;
    final Stream timelineStream;
    final TupleMatcher matcher;
    final TimelineItemLogDb logDb;
    private Set<ItemListener> itemListeners = new CopyOnWriteArraySet<>();

    LoadingCache<UUID, TimelineItem> itemCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(
                    new CacheLoader<UUID, TimelineItem>() {
                        @Override
                        public TimelineItem load(UUID uuid) {
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
                    + ", primary key(start, uuid), index(reltime_id))";
            ydb.execute(query);
        }
        if (ydb.getStream(streamName) == null) {
            ydb.execute("create stream " + streamName + TIMELINE_DEF.getStringDefinition());
        }
        ydb.execute("upsert into " + TABLE_NAME + " select * from " + streamName);
        return ydb.getStream(streamName);
    }

    @Override
    public TimelineItem addItem(TimelineItem item) {
        rwlock.writeLock().lock();
        try {
            if (item.getRelativeItemUuid() != null) {
                TimelineItem relItem = fromCache(item.getRelativeItemUuid());
                if (relItem == null) {
                    throw new InvalidRequestException(
                            "Referenced relative item uuid " + item.getRelativeItemUuid() + " does not exist");
                }
                item.setStart(relItem.getStart() + item.getRelativeStart());
            }
            if (item.getGroupUuid() != null) {
                TimelineItem groupItem = fromCache(item.getGroupUuid());
                if (groupItem == null) {
                    throw new InvalidRequestException(
                            "Referenced group item uuid " + item.getGroupUuid() + " does not exist");
                }
                if (!(groupItem instanceof ActivityGroup || groupItem instanceof ItemGroup)) {
                    throw new InvalidRequestException(
                            "Assigned group " + groupItem.getId() + " is not a real group");
                }
                if (groupItem instanceof ActivityGroup && !(item instanceof TimelineActivity)) {
                    throw new InvalidRequestException(
                            "An activity group " + groupItem.getId() + " can only contain activity items");
                }
            }
            Tuple tuple = item.toTuple();
            log.debug("Adding timeline item to RDB: {}", tuple);
            timelineStream.emitTuple(tuple);
        } finally {
            rwlock.writeLock().unlock();
        }

        itemListeners.forEach(l -> l.onItemCreated(item));
        return item;
    }

    @Override
    public TimelineItem updateItem(TimelineItem item) {
        rwlock.writeLock().lock();
        UUID itemId = UUID.fromString(item.getId());
        try {
            if (item.getRelativeItemUuid() != null) {
                TimelineItem relItem = fromCache(item.getRelativeItemUuid());
                if (relItem == null) {
                    throw new InvalidRequestException(
                            "Referenced relative item uuid " + item.getRelativeItemUuid() + " does not exist");
                }
                verifyRelTimeCircularity(itemId, relItem);
                item.setStart(relItem.getStart() + item.getRelativeStart());
            }

            if (item.getGroupUuid() != null) {
                TimelineItem groupItem = fromCache(item.getGroupUuid());
                if (groupItem == null) {
                    throw new InvalidRequestException(
                            "Referenced group item uuid " + item.getGroupUuid() + " does not exist");
                }
                if (!(groupItem instanceof ActivityGroup || groupItem instanceof ItemGroup)) {
                    throw new InvalidRequestException(
                            "Assigned group " + groupItem.getId() + " is not a real group");
                }
                if (groupItem instanceof ActivityGroup && !(item instanceof TimelineActivity)) {
                    throw new InvalidRequestException(
                            "An activity group " + groupItem.getId() + " can only contain activity items");
                }
                verifyGroupCircularity(itemId, groupItem);
            }
            doDeleteItem(itemId);

            Tuple tuple = item.toTuple();
            log.debug("Updating timeline item in RDB: {}", tuple);
            timelineStream.emitTuple(tuple);

            updateDependentStart(item);
        } finally {
            rwlock.writeLock().unlock();
        }

        itemListeners.forEach(l -> l.onItemUpdated(item));
        return item;
    }

    // update the start time of all items having their time specified as relative to this
    private void updateDependentStart(TimelineItem item) {
        String query = "update " + TABLE_NAME + " set start = " + CNAME_RELTIME_START + " + ? where "
                + CNAME_RELTIME_ID + " = ?";
        StreamSqlResult r = ydb.executeUnchecked(query, item.getStart(), item.getId());
        r.close();
    }

    private void verifyRelTimeCircularity(UUID uuid, TimelineItem relItem) {
        if (uuid.toString().equals(relItem.getId())) {
            throw new InvalidRequestException("Circular relative time reference for " + uuid);
        }

        if (relItem.getRelativeItemUuid() != null) {
            TimelineItem relItem1 = fromCache(relItem.getRelativeItemUuid());
            if (relItem1 == null) {
                throw new DatabaseCorruptionException("timeline item " + relItem.getRelativeItemUuid()
                        + " time referenced by " + relItem.getId() + " does not exist");
            }
            verifyRelTimeCircularity(uuid, relItem1);
        }
    }

    private void verifyGroupCircularity(UUID uuid, TimelineItem groupItem) {
        if (uuid.toString().equals(groupItem.getId())) {
            throw new InvalidRequestException("Circular relative time reference for " + uuid);
        }

        if (groupItem.getGroupUuid() != null) {
            TimelineItem groupItem1 = fromCache(groupItem.getGroupUuid());
            if (groupItem1 == null) {
                throw new DatabaseCorruptionException("timeline item " + groupItem.getGroupUuid()
                        + " group referenced by " + groupItem.getId() + " does not exist");
            }
            verifyGroupCircularity(uuid, groupItem1);
        }
    }

    private TimelineItem doGetItem(UUID uuid) {
        StreamSqlResult r = ydb.executeUnchecked("select * from " + TABLE_NAME + " where uuid = ?", uuid);
        try {
            if (r.hasNext()) {
                Tuple tuple = r.next();
                try {
                    TimelineItem item = TimelineItem.fromTuple(tuple);
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
    public TimelineItem getItem(String id) {
        UUID uuid = UUID.fromString(id);
        rwlock.readLock().lock();
        try {
            return fromCache(uuid);
        } finally {
            rwlock.readLock().unlock();
        }
    }

    @Override
    public TimelineItem deleteItem(UUID uuid) {
        TimelineItem item = null;
        rwlock.writeLock().lock();
        try {
            item = doGetItem(uuid);
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
        } finally {
            rwlock.writeLock().unlock();
        }

        var fItem = item;
        itemListeners.forEach(l -> l.onItemDeleted(fItem));
        return item;
    }

    @Override
    public TimelineItem deleteTimelineGroup(UUID uuid) {
        rwlock.writeLock().lock();
        try {
            TimelineItem item = doGetItem(uuid);
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
    public void getItems(int limit, String token, RetrievalFilter filter, ItemReceiver consumer) {
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
                            consumer.next(TimelineItem.fromTuple(tuple));
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

    public void addItemListener(ItemListener itemListener) {
        itemListeners.add(itemListener);
    }

    public void removeItemListener(ItemListener itemListener) {
        itemListeners.remove(itemListener);
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
    private TimelineItem fromCache(UUID uuid) {
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
        UUID uuid = UUID.fromString(id);
        return logDb.getLog(uuid);
    }

    @Override
    public LogEntry addItemLog(String id, LogEntry entry) {
        UUID uuid = UUID.fromString(id);
        return logDb.addLogEntry(uuid, entry);
    }

    @SuppressWarnings("serial")
    static class NoSuchItemException extends RuntimeException {

    }

}
