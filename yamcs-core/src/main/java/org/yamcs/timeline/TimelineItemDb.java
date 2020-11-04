package org.yamcs.timeline;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.yamcs.InitException;
import org.yamcs.http.api.SqlBuilder;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.TimelineSourceCapabilities;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.InvalidRequestException;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.*;
import org.yamcs.yarch.streamsql.ResultListener;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;
import org.yamcs.yarch.streamsql.StreamSqlStatement;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class TimelineItemDb implements TimelineSource {
    static final Random random = new Random();
    public static final TupleDefinition TIMELINE_DEF = new TupleDefinition();
    public static final String CNAME_START = "start";
    public static final String CNAME_DURATION = "duration";
    public static final String CNAME_ID = "uuid";
    public static final String CNAME_TYPE = "type";
    public static final String CNAME_TAGS = "tags";
    public static final String CNAME_GROUP_ID = "group_id";
    public static final String CNAME_RELTIME_ID = "reltime_id";
    public static final String CNAME_RELTIME_START = "reltime_start";

    static {
        TIMELINE_DEF.addColumn(CNAME_START, DataType.TIMESTAMP);
        TIMELINE_DEF.addColumn(CNAME_DURATION, DataType.LONG);
        TIMELINE_DEF.addColumn(CNAME_ID, DataType.UUID);
        TIMELINE_DEF.addColumn(CNAME_TYPE, DataType.ENUM);
        TIMELINE_DEF.addColumn(CNAME_TAGS, DataType.array(DataType.ENUM));
        TIMELINE_DEF.addColumn(CNAME_GROUP_ID, DataType.UUID);
        TIMELINE_DEF.addColumn(CNAME_RELTIME_ID, DataType.UUID);
        TIMELINE_DEF.addColumn(CNAME_RELTIME_START, DataType.LONG);
    }
    final Log log;
    final private ReadWriteLock rwlock = new ReentrantReadWriteLock();
    final static String TIMELINE_TABLE_NAME = "timeline";

    final YarchDatabaseInstance ydb;
    final Stream timelineStream;

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

    }

    private Stream setupTimelineRecording() throws StreamSqlException, ParseException {
        String streamName = TIMELINE_TABLE_NAME + "_in";
        if (ydb.getTable(TIMELINE_TABLE_NAME) == null) {
            String query = "create table " + TIMELINE_TABLE_NAME + "(" + TIMELINE_DEF.getStringDefinition1()
                    + ", primary key(start, uuid), index(reltime_id))";
            ydb.execute(query);
        }
        if (ydb.getStream(streamName) == null) {
            ydb.execute("create stream " + streamName + TIMELINE_DEF.getStringDefinition());
        }
        ydb.execute("upsert into " + TIMELINE_TABLE_NAME + " select * from " + streamName);
        return ydb.getStream(streamName);
    }

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
                if(!(groupItem instanceof ActivityGroup || groupItem instanceof ItemGroup)) {
                    throw new InvalidRequestException(
                            "Assigned group " + groupItem.getId() + " is not a real group");
                }
                if(groupItem instanceof ActivityGroup && !((item instanceof ManualActivity) || (item instanceof AutomatedActivity))) {
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
    public TimelineItem updateItem(TimelineItem item) {
        rwlock.writeLock().lock();
        try {
            if (item.getRelativeItemUuid() != null) {
                TimelineItem relItem = fromCache(item.getRelativeItemUuid());
                if (relItem == null) {
                    throw new InvalidRequestException(
                            "Referenced relative item uuid " + item.getRelativeItemUuid() + " does not exist");
                }
                verifyRelTimeCircularity(item.getId(), relItem);
                item.setStart(relItem.getStart() + item.getRelativeStart());
            }

            if (item.getGroupUuid() != null) {
                TimelineItem groupItem = fromCache(item.getGroupUuid());
                if (groupItem == null) {
                    throw new InvalidRequestException(
                            "Referenced group item uuid " + item.getGroupUuid() + " does not exist");
                }
                if(!(groupItem instanceof ActivityGroup || groupItem instanceof ItemGroup)) {
                    throw new InvalidRequestException(
                            "Assigned group " + groupItem.getId() + " is not a real group");
                }
                if(groupItem instanceof ActivityGroup && !((item instanceof ManualActivity) || (item instanceof AutomatedActivity))) {
                    throw new InvalidRequestException(
                            "An activity group " + groupItem.getId() + " can only contain activity items");
                }
                verifyGroupCircularity(item.getId(), groupItem);
            }
            doDeleteItem(item.getId());

            Tuple tuple = item.toTuple();
            log.debug("Updating timeline item in RDB: {}", tuple);
            System.out.println("emitting " + tuple);
            timelineStream.emitTuple(tuple);

            updateDependentStart(item);
            return item;
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    // update the start time of all items having their time specified as relative to this
    private void updateDependentStart(TimelineItem item) {
        String query = "update " + TIMELINE_TABLE_NAME + " set start = " + CNAME_RELTIME_START + " + ? where "
                + CNAME_RELTIME_ID + " = ?";
        System.out.println("query: " + query);
        StreamSqlResult r = ydb.executeUnchecked(query, item.getStart(), item.getId());
        r.close();
    }

    private void verifyRelTimeCircularity(UUID uuid, TimelineItem relItem) {
        if (uuid.equals(relItem.getId())) {
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
        if (uuid.equals(groupItem.getId())) {
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
        StreamSqlResult r = ydb.executeUnchecked("select * from " + TIMELINE_TABLE_NAME + " where uuid = ?", uuid);
        try {
            if (r.hasNext()) {
                Tuple tuple = r.next();
                try {
                    TimelineItem item = TimelineItem.fromTuple(tuple);
                    System.out.println("read tuple: " + tuple);
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
    public TimelineItem getItem(UUID uuid) {
        rwlock.readLock().lock();
        try {
            return fromCache(uuid);
        } finally {
            rwlock.readLock().unlock();
        }
    }

    @Override
    public TimelineItem deleteItem(UUID uuid) {
        rwlock.writeLock().lock();
        try {
            TimelineItem item = doGetItem(uuid);
            if (item == null) {
                return null;
            }

            StreamSqlResult r = ydb.executeUnchecked(
                    "select uuid from " + TIMELINE_TABLE_NAME + " where " + CNAME_GROUP_ID + " = ?", uuid);
            if (r.hasNext()) {
                UUID id = r.next().getColumn(CNAME_ID);
                throw new InvalidRequestException(
                        "Cannot delete " + uuid + " because it is considered as a group by item " + id);
            }
            r.close();

            r = ydb.executeUnchecked(
                    "select uuid from " + TIMELINE_TABLE_NAME + " where " + CNAME_RELTIME_ID + " = ?", uuid);
            if (r.hasNext()) {
                UUID id = r.next().getColumn(CNAME_ID);
                throw new InvalidRequestException(
                        "Cannot delete " + uuid + " because item " + id + " time depends on it");
            }
            r.close();
            doDeleteItem(uuid);

            return item;
        } finally {
            rwlock.writeLock().unlock();
        }
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
                    "select uuid from " + TIMELINE_TABLE_NAME + " where " + CNAME_GROUP_ID + " = ?", uuid);
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
        StreamSqlResult r = ydb.executeUnchecked("delete from " + TIMELINE_TABLE_NAME + " where uuid = ?", uuid);
        r.close();
    }


    @Override
    public void getItems(int limit, String token, ItemFilter filter, ItemListener consumer) {
        rwlock.readLock().lock();
        try {
            SqlBuilder sqlBuilder = new SqlBuilder(TIMELINE_TABLE_NAME);
            sqlBuilder.select("*");
            sqlBuilder.where("start < ?", filter.getTimeInterval().getEnd());
            sqlBuilder.where("start+duration > ?", filter.getTimeInterval().getStart());
            if(filter.getTags()!=null && !filter.getTags().isEmpty())
                sqlBuilder.where(" tags && ?", filter.getTags());
            sqlBuilder.limit(limit+1);

//            System.out.println("statement=" + sqlBuilder.toString() + " with arguments " + sqlBuilder.getQueryArguments().toArray());
            StreamSqlStatement stmt = ydb.createStatement(sqlBuilder.toString(),sqlBuilder.getQueryArguments().toArray());
            ydb.execute(stmt, new ResultListener() {
                int count = 0;

                @Override
                public void next(Tuple tuple) {
                    if (count < limit) {
                        consumer.next(TimelineItem.fromTuple(tuple));
                    }
                    count++;
                }

                @Override
                public void completeExceptionally(Throwable t) {
                    consumer.completeExceptionally(t);
                }

                @Override
                public void complete() {
                    if (count == limit) {
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

    private static String getRandomToken() {
        byte[] b = new byte[16];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    @Override
    public Collection<String> getTags() {
        rwlock.readLock().lock();
        try {
            TableColumnDefinition tcd = ydb.getTable(TIMELINE_TABLE_NAME).getColumnDefinition(CNAME_TAGS);
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

    static class NoSuchItemException extends RuntimeException {

    }

    @Override
    public TimelineSourceCapabilities getCapabilities() {
        return TimelineSourceCapabilities.newBuilder().setReadOnly(false).setHasActivityGroups(true)
                .setHasEventGroups(true).setHasManualActivities(true).setHasAutomatedActivities(true).build();
    }
}
