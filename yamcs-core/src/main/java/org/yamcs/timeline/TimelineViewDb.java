package org.yamcs.timeline;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.yamcs.InitException;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.TimelineView;
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

public class TimelineViewDb {
    static final Random random = new Random();
    public static final TupleDefinition TIMELINE_DEF = new TupleDefinition();
    public static final String CNAME_ID = "uuid";
    public static final String CNAME_NAME = "name";
    public static final String CNAME_DESCRIPTION = "description";
    public static final String CNAME_VIEWS = "views";

    static {
        TIMELINE_DEF.addColumn(CNAME_ID, DataType.UUID);
        TIMELINE_DEF.addColumn(CNAME_NAME, DataType.STRING);
        TIMELINE_DEF.addColumn(CNAME_DESCRIPTION, DataType.STRING);
        TIMELINE_DEF.addColumn(CNAME_VIEWS, DataType.array(DataType.ENUM));

    }
    final Log log;
    final private ReadWriteLock rwlock = new ReentrantReadWriteLock();
    final static String TIMELINE_TABLE_NAME = "timeline_view";

    final YarchDatabaseInstance ydb;
    final Stream timelineStream;


    LoadingCache<UUID, TimelineView> viewCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(
                    new CacheLoader<UUID, TimelineView>() {
                        @Override
                        public TimelineView load(UUID uuid) {
                            return doGetview(uuid);
                        }
                    });

    public TimelineViewDb(String yamcsInstance) throws InitException {
        log = new Log(getClass(), yamcsInstance);

        ydb = YarchDatabase.getInstance(yamcsInstance);
        try {
            timelineStream = setupTimelineRecording();
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

    private TimelineView doGetview(UUID uuid) {
        StreamSqlResult r = ydb.executeUnchecked("select * from " + TIMELINE_TABLE_NAME + " where uuid = ?", uuid);
        try {
            if (r.hasNext()) {
                Tuple tuple = r.next();
                try {
                    TimelineView view = fromTuple(tuple);
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
        StreamSqlResult r = ydb.executeUnchecked("delete from " + TIMELINE_TABLE_NAME + " where uuid = ?", uuid);
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

    public static TimelineView fromTuple(Tuple tuple) {
        TimelineView.Builder builder = TimelineView.newBuilder()
                .setUuid(tuple.getColumn(CNAME_ID).toString())
                .setName(tuple.getColumn(CNAME_NAME))
                .setDescription(tuple.getColumn(CNAME_DESCRIPTION))
                .addAllBands(tuple.getColumn(CNAME_VIEWS));
        return builder.build();

    }

    private Tuple toTuple(TimelineView view) {
        Tuple tuple = new Tuple();
        tuple.addColumn(CNAME_ID, DataType.UUID, UUID.fromString(view.getUuid()));
        tuple.addColumn(CNAME_NAME, view.getName());
        tuple.addColumn(CNAME_DESCRIPTION, view.getDescription());
        if (!view.getBandsList().isEmpty()) {
            tuple.addColumn(CNAME_VIEWS, DataType.array(DataType.ENUM), view.getBandsList());
        }

        return tuple;
    }

    static class NoSuchItemException extends RuntimeException {

    }

    public TimelineView addView(TimelineView view) {
        rwlock.writeLock().lock();
        try {
            Tuple tuple = toTuple(view);
            log.debug("Adding timeline view to RDB: {}", tuple);
            timelineStream.emitTuple(tuple);
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
                    "select * from " + TIMELINE_TABLE_NAME);

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
