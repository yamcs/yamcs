package org.yamcs.timeline;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.yamcs.InitException;
import org.yamcs.logging.Log;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
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

public class TimelineViewDb {
    public static final TupleDefinition TIMELINE_DEF = new TupleDefinition();
    public static final String CNAME_ID = "uuid";
    public static final String CNAME_NAME = "name";
    public static final String CNAME_DESCRIPTION = "description";
    public static final String CNAME_BANDS = "bands";

    static {
        TIMELINE_DEF.addColumn(CNAME_ID, DataType.UUID);
        TIMELINE_DEF.addColumn(CNAME_NAME, DataType.STRING);
        TIMELINE_DEF.addColumn(CNAME_DESCRIPTION, DataType.STRING);
        TIMELINE_DEF.addColumn(CNAME_BANDS, DataType.array(DataType.UUID));
    }

    final Log log;
    final private ReadWriteLock rwlock = new ReentrantReadWriteLock();
    final static String TABLE_NAME = "timeline_view";

    final YarchDatabaseInstance ydb;
    final Stream viewStream;

    LoadingCache<UUID, TimelineView> viewCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(new CacheLoader<UUID, TimelineView>() {
                @Override
                public TimelineView load(UUID uuid) {
                    return doGetview(uuid);
                }
            });

    public TimelineViewDb(String yamcsInstance) throws InitException {
        log = new Log(getClass(), yamcsInstance);

        ydb = YarchDatabase.getInstance(yamcsInstance);
        try {
            viewStream = setupTimelineRecording();
        } catch (ParseException | StreamSqlException e) {
            throw new InitException(e);
        }

    }

    public TimelineView deleteView(UUID uuid) {
        rwlock.writeLock().lock();
        try {
            TimelineView view = doGetview(uuid);
            if (view == null) {
                return null;
            }

            doDeleteView(uuid);

            return view;
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

    private TimelineView doGetview(UUID uuid) {
        StreamSqlResult r = ydb.executeUnchecked("select * from " + TABLE_NAME + " where uuid = ?", uuid);
        try {
            if (r.hasNext()) {
                Tuple tuple = r.next();
                try {
                    TimelineView view = new TimelineView(tuple);
                    log.trace("Read view from db {}", view);
                    return view;
                } catch (Exception e) {
                    log.error("Cannot decode tuple {} intro timeline view", tuple);
                }
            }
        } finally {
            r.close();
        }
        throw new NoSuchItemException();
    }

    private void doDeleteView(UUID uuid) {
        viewCache.invalidate(uuid);
        StreamSqlResult r = ydb.executeUnchecked("delete from " + TABLE_NAME + " where uuid = ?", uuid);
        r.close();
    }

    // returns null if uuid does not exist
    private TimelineView viewFromCache(UUID uuid) {
        try {
            return viewCache.getUnchecked(uuid);
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

    public TimelineView addView(TimelineView view) {
        rwlock.writeLock().lock();
        try {
            Tuple tuple = view.toTuple();
            log.debug("Adding timeline view to RDB: {}", tuple);
            viewStream.emitTuple(tuple);
            return view;
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public TimelineView updateView(TimelineView view) {
        rwlock.writeLock().lock();
        try {
            doDeleteView(view.getId());

            Tuple tuple = view.toTuple();
            log.debug("Updating timeline view in RDB: {}", tuple);
            viewStream.emitTuple(tuple);
            return view;
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public TimelineView getView(UUID uuid) {
        rwlock.readLock().lock();
        try {
            return viewFromCache(uuid);
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public void listViews(ViewListener consumer) {
        rwlock.readLock().lock();
        try {
            // increase the limit to generate a token when the limit is reached
            StreamSqlStatement stmt = ydb.createStatement(
                    "select * from " + TABLE_NAME);

            ydb.execute(stmt, new ResultListener() {
                @Override
                public void next(Tuple tuple) {
                    consumer.next(new TimelineView(tuple));
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
