package org.yamcs.timeline;

import java.util.UUID;

import org.yamcs.InitException;
import org.yamcs.YamcsServer;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.LogEntry;
import org.yamcs.protobuf.TimelineItemLog;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlResult;

public class TimelineItemLogDb {
    public static final TupleDefinition LOG_DEF = new TupleDefinition();
    public static final String CNAME_TIME = "logtime";
    public static final String CNAME_ID = "uuid";
    public static final String CNAME_USER = "user";
    public static final String CNAME_TYPE = "type";
    public static final String CNAME_MSG = "msg";

    static {
        LOG_DEF.addColumn(CNAME_TIME, DataType.TIMESTAMP);
        LOG_DEF.addColumn(CNAME_ID, DataType.UUID);
        LOG_DEF.addColumn(CNAME_USER, DataType.STRING);
        LOG_DEF.addColumn(CNAME_TYPE, DataType.STRING);
        LOG_DEF.addColumn(CNAME_MSG, DataType.STRING);
    }

    final Log log;
    final static String TABLE_NAME = "timeline_log";

    final YarchDatabaseInstance ydb;
    final Stream logStream;

    final TimeService timeService;

    public TimelineItemLogDb(String yamcsInstance) throws InitException {
        log = new Log(getClass(), yamcsInstance);

        ydb = YarchDatabase.getInstance(yamcsInstance);
        try {
            logStream = setupLogRecording();
        } catch (ParseException | StreamSqlException e) {
            throw new InitException(e);
        }
        timeService = YamcsServer.getTimeService(yamcsInstance);
    }

    private Stream setupLogRecording() throws StreamSqlException, ParseException {
        if (ydb.getTable(TABLE_NAME) == null) {
            String query = "create table " + TABLE_NAME + "(" + LOG_DEF.getStringDefinition1()
                    + ", seq long auto_increment, primary key(logtime, uuid, seq))";
            ydb.execute(query);
        }
        String streamName = TABLE_NAME + "_in";

        if (ydb.getStream(streamName) == null) {
            ydb.execute("create stream " + streamName + LOG_DEF.getStringDefinition());
        }
        ydb.execute("upsert into " + TABLE_NAME + " select * from " + streamName);
        return ydb.getStream(streamName);
    }

    public TimelineItemLog getLog(UUID uuid) {
        TimelineItemLog.Builder logb = TimelineItemLog.newBuilder().setId(uuid.toString());
        StreamSqlResult r = null;
        try {
            r = ydb.execute("select * from " + TABLE_NAME + " where uuid = ?", uuid);
            while (r.hasNext()) {
                logb.addEntries(fromTuple(r.next()));
            }
        } catch (StreamSqlException | ParseException e) {
            log.error("Exception when executing query", e);
        } finally {
            if (r != null) {
                r.close();
            }
        }

        return logb.build();
    }

    public LogEntry addLogEntry(UUID uuid, LogEntry entry) {
        Tuple tuple = toTuple(uuid, entry);
        log.debug("Adding log {}", tuple);
        logStream.emitTuple(tuple);

        return fromTuple(tuple);
    }

    private LogEntry fromTuple(Tuple tuple) {
        long time = tuple.getTimestampColumn(CNAME_TIME);
        LogEntry.Builder logb = LogEntry.newBuilder().setTime(TimeEncoding.toProtobufTimestamp(time));

        if (tuple.hasColumn(CNAME_USER)) {
            logb.setUser(tuple.getColumn(CNAME_USER));
        }
        if (tuple.hasColumn(CNAME_TYPE)) {
            logb.setType(tuple.getColumn(CNAME_TYPE));
        }

        if (tuple.hasColumn(CNAME_MSG)) {
            logb.setMsg(tuple.getColumn(CNAME_MSG));
        }

        return logb.build();
    }

    private Tuple toTuple(UUID uuid, LogEntry entry) {
        Tuple tuple = new Tuple();
        long time = entry.hasTime() ? TimeEncoding.fromProtobufTimestamp(entry.getTime())
                : timeService.getMissionTime();

        tuple.addColumn(CNAME_TIME, DataType.TIMESTAMP, time);
        tuple.addColumn(CNAME_ID, DataType.UUID, uuid);

        if (entry.hasUser()) {
            tuple.addColumn(CNAME_USER, entry.getUser());
        }

        if (entry.hasType()) {
            tuple.addColumn(CNAME_TYPE, entry.getType());
        }

        if (entry.hasMsg()) {
            tuple.addColumn(CNAME_MSG, entry.getMsg());
        }

        return tuple;
    }
}
