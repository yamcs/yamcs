package org.yamcs.timeline;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.yamcs.InitException;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.TimelineBand;
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

public class TimelineBandDb {
    static final Random random = new Random();
    public static final TupleDefinition TIMELINE_DEF = new TupleDefinition();
    public static final String CNAME_ID = "uuid";
    public static final String CNAME_NAME = "name";
    public static final String CNAME_DESCRIPTION = "description";
    public static final String CNAME_TYPE = "type";
    public static final String CNAME_SHARED = "shared";
    public static final String CNAME_USERNAME = "username";
    public static final String CNAME_TAGS = "tags";

    protected static final String PROP_PREFIX = "PROP";

    static {
        TIMELINE_DEF.addColumn(CNAME_ID, DataType.UUID);
        TIMELINE_DEF.addColumn(CNAME_NAME, DataType.STRING);
        TIMELINE_DEF.addColumn(CNAME_DESCRIPTION, DataType.STRING);
        TIMELINE_DEF.addColumn(CNAME_SHARED, DataType.BOOLEAN);
        TIMELINE_DEF.addColumn(CNAME_USERNAME, DataType.STRING);
        TIMELINE_DEF.addColumn(CNAME_TYPE, DataType.ENUM);
        TIMELINE_DEF.addColumn(CNAME_TAGS, DataType.array(DataType.ENUM));

    }
    final Log log;
    final private ReadWriteLock rwlock = new ReentrantReadWriteLock();
    final static String TIMELINE_TABLE_NAME = "timeline_band";

    final YarchDatabaseInstance ydb;
    final Stream timelineStream;


    LoadingCache<UUID, TimelineBand> bandCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(
                    new CacheLoader<UUID, TimelineBand>() {
                        @Override
                        public TimelineBand load(UUID uuid) {
                            return doGetBand(uuid);
                        }
                    });

    public TimelineBandDb(String yamcsInstance) throws InitException {
        log = new Log(getClass(), yamcsInstance);

        ydb = YarchDatabase.getInstance(yamcsInstance);
        try {
            timelineStream = setupTimelineRecording();
        } catch (ParseException | StreamSqlException e) {
            throw new InitException(e);
        }

    }

    public TimelineBand deleteBand(UUID uuid) {
        rwlock.writeLock().lock();
        try {
            TimelineBand band = doGetBand(uuid);
            if (band == null) {
                return null;
            }

            doDeleteBand(uuid);

            return band;
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    private Stream setupTimelineRecording() throws StreamSqlException, ParseException {
        String streamName = TIMELINE_TABLE_NAME + "_in";
        if (ydb.getTable(TIMELINE_TABLE_NAME) == null) {
            String query = "create table " + TIMELINE_TABLE_NAME + "(" + TIMELINE_DEF.getStringDefinition1()
                    + ", primary key(uuid))";
            ydb.execute(query);
        }
        if (ydb.getStream(streamName) == null) {
            ydb.execute("create stream " + streamName + TIMELINE_DEF.getStringDefinition());
        }
        ydb.execute("upsert into " + TIMELINE_TABLE_NAME + " select * from " + streamName);
        return ydb.getStream(streamName);
    }

    private TimelineBand doGetBand(UUID uuid) {
        StreamSqlResult r = ydb.executeUnchecked("select * from " + TIMELINE_TABLE_NAME + " where uuid = ?", uuid);
        try {
            if (r.hasNext()) {
                Tuple tuple = r.next();
                try {
                    TimelineBand band = fromTuple(tuple);
                    log.trace("Read band from db {}", band);
                    return band;
                } catch (Exception e) {
                    log.error("Cannot decode tuple {} intro timeline band", tuple);
                }
            }
        } finally {
            r.close();
        }
        throw new NoSuchItemException();
    }

    private void doDeleteBand(UUID uuid) {
        bandCache.invalidate(uuid);
        StreamSqlResult r = ydb.executeUnchecked("delete from " + TIMELINE_TABLE_NAME + " where uuid = ?", uuid);
        r.close();
    }


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
    private TimelineBand bandFromCache(UUID uuid) {
        try {
            return bandCache.getUnchecked(uuid);
        } catch (UncheckedExecutionException e) {
            if (e.getCause() instanceof NoSuchItemException) {
                return null;
            } else {
                throw e;
            }
        }
    }

    public static TimelineBand fromTuple(Tuple tuple) {
        Map<String,String> properties = new HashMap<String,String>();
        for(int i=0; i<tuple.size(); i++) {
            ColumnDefinition column = tuple.getColumnDefinition(i);
            if (column.getName().startsWith(PROP_PREFIX)) {
                String columnName = column.getName().substring(PROP_PREFIX.length());
                properties.put(columnName, tuple.getColumn(column.getName()));
            }
        }
        TimelineBand.Builder builder = TimelineBand.newBuilder()
                .setUuid(tuple.getColumn(CNAME_ID).toString())
                .setName(tuple.getColumn(CNAME_NAME))
                .setDescription(tuple.getColumn(CNAME_DESCRIPTION))
                .setShared(tuple.getColumn(CNAME_SHARED))
                .setUsername(tuple.getColumn(CNAME_USERNAME))
                .putAllProperties(properties);
        if (tuple.getColumn(CNAME_TAGS)!=null) {
           builder.addAllTags(tuple.getColumn(CNAME_TAGS));
        }
        return builder.build();

    }

    private Tuple toTuple(TimelineBand band) {
        Tuple tuple = new Tuple();
        tuple.addColumn(CNAME_ID, DataType.UUID, UUID.fromString(band.getUuid()));
        tuple.addColumn(CNAME_NAME, band.getName());
        tuple.addColumn(CNAME_DESCRIPTION, band.getDescription());
        tuple.addColumn(CNAME_TYPE, band.getType().toString());
        tuple.addColumn(CNAME_SHARED, band.getShared());
        tuple.addColumn(CNAME_USERNAME, band.getUsername());
        for(Map.Entry<String,String> entry : band.getPropertiesMap().entrySet()) {
            tuple.addColumn(PROP_PREFIX +entry.getKey(), entry.getValue());
        }
        if (!band.getTagsList().isEmpty()) {
            tuple.addColumn(CNAME_TAGS, DataType.array(DataType.ENUM), band.getTagsList());
        }

        return tuple;
    }

    static class NoSuchItemException extends RuntimeException {

    }

    public TimelineBand addBand(TimelineBand band) {
        rwlock.writeLock().lock();
        try {
            Tuple tuple = toTuple(band);
            log.debug("Adding timeline band to RDB: {}", tuple);
            timelineStream.emitTuple(tuple);
            return band;
        } finally {
            rwlock.writeLock().unlock();
        }
    }


    public TimelineBand getBand(UUID uuid) {
        rwlock.readLock().lock();
        try {
            return bandFromCache(uuid);
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public void listBands(String user, BandListener consumer) {
        rwlock.readLock().lock();
        try {
            // increase the limit to generate a token when the limit is reached
            StreamSqlStatement stmt = ydb.createStatement(
                    "select * from " + TIMELINE_TABLE_NAME + " where shared or username = ?", user);

            ydb.execute(stmt, new ResultListener() {
                int count = 0;

                @Override
                public void next(Tuple tuple) {
                    consumer.next(fromTuple(tuple));
                    count++;
                }

                @Override
                public void completeExceptionally(Throwable t) {
                    consumer.completeExceptionally(t);
                }

                @Override
                public void complete() {
                    consumer.complete(null);
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

}
