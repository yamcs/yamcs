package org.yamcs.archive;

import java.util.stream.Collectors;

import org.yamcs.AbstractYamcsService;
import org.yamcs.InitException;
import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StreamConfigEntry;
import org.yamcs.YConfiguration;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.protobuf.Db.Event;
import org.yamcs.yarch.streamsql.StreamSqlException;

/**
 * Sets up the archiving of the events coming on events_realtime and events_dump streams into the yarch table events.
 * 
 * @author nm
 *
 */
public class EventRecorder extends AbstractYamcsService {

    public static final String TABLE_NAME = "events";
    public static final String REALTIME_EVENT_STREAM_NAME = "events_realtime";
    public static final String CF_NAME = XtceTmRecorder.CF_NAME;
    
    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);

        try {
            if (ydb.getTable(TABLE_NAME) == null) {
                var timePart = ydb.getTimePartitioningSchema(config);

                var partitionBy = timePart == null ? ""
                        : "partition by time(gentime('" + timePart.getName() + "'))";

                ydb.execute("create table " + TABLE_NAME
                        + "(gentime timestamp, source enum, seqNum int, body PROTOBUF('" + Event.class.getName()
                        + "'), primary key(gentime, source, seqNum)) histogram(source) " + partitionBy
                        + " table_format=compressed,column_family:"+CF_NAME);
            }

            StreamConfig sc = StreamConfig.getInstance(yamcsInstance);
            for (StreamConfigEntry sce : sc.getEntries()) {
                if (sce.getType() == StreamConfig.StandardStreamType.EVENT) {
                    ydb.execute("insert into " + TABLE_NAME + " select * from " + sce.getName());
                }
            }
        } catch (ParseException | StreamSqlException e) {
            throw new InitException(e);
        }
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        StreamConfig sc = StreamConfig.getInstance(yamcsInstance);

        Utils.closeTableWriters(ydb, sc.getEntries().stream().map(sce -> sce.getName()).collect(Collectors.toList()));

        notifyStopped();
    }
}
