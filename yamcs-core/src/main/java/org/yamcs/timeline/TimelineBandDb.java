package org.yamcs.timeline;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.yamcs.InitException;
import org.yamcs.logging.Log;
import org.yamcs.timeline.protobuf.BandFilter;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.DataType;
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

public class TimelineBandDb {
    public static final TupleDefinition TIMELINE_DEF = new TupleDefinition();
    public static final String CNAME_ID = "uuid";
    public static final String CNAME_NAME = "name";
    public static final String CNAME_DESCRIPTION = "description";
    public static final String CNAME_TYPE = "type";
    public static final String CNAME_SHARED = "shared";
    public static final String CNAME_USERNAME = "username";
    public static final String CNAME_TAGS = "tags";
    public static final String CNAME_SOURCE = "source";
    public static final String CNAME_FILTER = "filter";

    protected static final String PROP_PREFIX = "prop_";

    static {
        TIMELINE_DEF.addColumn(CNAME_ID, DataType.UUID);
        TIMELINE_DEF.addColumn(CNAME_NAME, DataType.STRING);
        TIMELINE_DEF.addColumn(CNAME_DESCRIPTION, DataType.STRING);
        TIMELINE_DEF.addColumn(CNAME_SHARED, DataType.BOOLEAN);
        TIMELINE_DEF.addColumn(CNAME_USERNAME, DataType.STRING);
        TIMELINE_DEF.addColumn(CNAME_TYPE, DataType.ENUM);
        TIMELINE_DEF.addColumn(CNAME_TAGS, DataType.array(DataType.ENUM));
        TIMELINE_DEF.addColumn(CNAME_SOURCE, DataType.ENUM);
        TIMELINE_DEF.addColumn(CNAME_FILTER, DataType.protobuf(BandFilter.class));
    }

    final Log log;
    final private ReadWriteLock rwlock = new ReentrantReadWriteLock();
    final static String TABLE_NAME = "timeline_band";

    final YarchDatabaseInstance ydb;
    final Stream bandStream;

    LoadingCache<UUID, TimelineBand> bandCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(new CacheLoader<UUID, TimelineBand>() {
                @Override
                public TimelineBand load(UUID uuid) {
                    return doGetBand(uuid);
                }
            });

    public TimelineBandDb(String yamcsInstance) throws InitException {
        log = new Log(getClass(), yamcsInstance);

        ydb = YarchDatabase.getInstance(yamcsInstance);
        try {
            bandStream = setupTimelineRecording();
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
        String streamName = TABLE_NAME + "_in";
        if (ydb.getTable(TABLE_NAME) == null) {
            String query = "create table " + TABLE_NAME + "(" + TIMELINE_DEF.getStringDefinition1()
                    + ", primary key(uuid))";
            ydb.execute(query);
        }
        if (ydb.getStream(streamName) == null) {
            ydb.execute("create stream " + streamName + TIMELINE_DEF.getStringDefinition());
        }
        ydb.execute("upsert into " + TABLE_NAME + " select * from " + streamName);
        return ydb.getStream(streamName);
    }

    private TimelineBand doGetBand(UUID uuid) {
        StreamSqlResult r = ydb.executeUnchecked("select * from " + TABLE_NAME + " where uuid = ?", uuid);
        try {
            if (r.hasNext()) {
                Tuple tuple = r.next();
                try {
                    TimelineBand band = new TimelineBand(tuple);
                    log.trace("Read band from db {}", band);
                    return band;
                } catch (Exception e) {
                    log.error("Cannot decode tuple {} to band", tuple);
                }
            }
        } finally {
            r.close();
        }
        throw new NoSuchItemException();
    }

    private void doDeleteBand(UUID uuid) {
        bandCache.invalidate(uuid);
        StreamSqlResult r = ydb.executeUnchecked("delete from " + TABLE_NAME + " where uuid = ?", uuid);
        r.close();
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

    @SuppressWarnings("serial")
    static class NoSuchItemException extends RuntimeException {
    }

    public TimelineBand addBand(TimelineBand band) {
        rwlock.writeLock().lock();
        try {
            Tuple tuple = band.toTuple();
            log.debug("Adding timeline band to RDB: {}", tuple);
            bandStream.emitTuple(tuple);
            return band;
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public TimelineBand updateBand(TimelineBand band) {
        rwlock.writeLock().lock();
        try {
            doDeleteBand(band.getId());

            Tuple tuple = band.toTuple();
            log.debug("Updating timeline band in RDB: {}", tuple);
            bandStream.emitTuple(tuple);
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
            StreamSqlStatement stmt = ydb.createStatement(
                    "select * from " + TABLE_NAME + " where shared or username = ?", user);

            ydb.execute(stmt, new ResultListener() {
                @Override
                public void next(Tuple tuple) {
                    consumer.next(new TimelineBand(tuple));
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
}
