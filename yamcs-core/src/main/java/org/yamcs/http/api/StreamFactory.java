package org.yamcs.http.api;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.http.InternalServerErrorException;
import org.yamcs.logging.Log;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlException;

public class StreamFactory {

    private static AtomicInteger streamCounter = new AtomicInteger();
    private static final Log log = new Log(StreamFactory.class);

    public static void stream(String instance, String selectSql, StreamSubscriber subscriber) {
        stream(instance, selectSql, Collections.emptyList(), subscriber);
    }

    public static void stream(String instance, String selectSql, List<Object> args, StreamSubscriber subscriber) {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);

        String streamName = "http_stream" + streamCounter.incrementAndGet();
        String sql = new StringBuilder("create stream ")
                .append(streamName)
                .append(" as ")
                .append(selectSql)
                .append(" nofollow")
                .toString();

        log.debug("Executing: {}", sql);
        try {
            ydb.executeDiscardingResult(sql, args.toArray());
        } catch (StreamSqlException | ParseException e) {
            throw new InternalServerErrorException(e);
        }

        Stream stream = ydb.getStream(streamName);
        stream.addSubscriber(subscriber);
        stream.start();
        return;
    }

    public static Stream insertStream(String instance, TableDefinition table) {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);

        String streamName = "http_stream" + streamCounter.incrementAndGet();
        String sql = new StringBuilder("create stream ")
                .append(streamName)
                .append(" ")
                .append(table.getTupleDefinition().getStringDefinition())
                .toString();

        log.debug("Executing: {}", sql);
        try {
            ydb.executeDiscardingResult(sql);
            ydb.executeDiscardingResult(String.format("insert into %s select * from %s", table.getName(), streamName));
        } catch (StreamSqlException | ParseException e) {
            throw new InternalServerErrorException(e);
        }

        return ydb.getStream(streamName);
    }

    public static Stream loadStream(String instance, TableDefinition table) {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);

        String streamName = "http_stream" + streamCounter.incrementAndGet();
        String sql = new StringBuilder("create stream ")
                .append(streamName)
                .append(" ")
                .append(table.getTupleDefinition().getStringDefinition())
                .toString();

        log.debug("Executing: {}", sql);
        try {
            ydb.executeDiscardingResult(sql);
            ydb.executeDiscardingResult(String.format("load into %s select * from %s", table.getName(), streamName));
        } catch (StreamSqlException | ParseException e) {
            throw new InternalServerErrorException(e);
        }

        return ydb.getStream(streamName);
    }
}
