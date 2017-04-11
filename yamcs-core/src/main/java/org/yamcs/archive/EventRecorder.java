package org.yamcs.archive;

import java.util.stream.Collectors;

import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StreamConfigEntry;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

import com.google.common.util.concurrent.AbstractService;

/**
 * Sets up the archiving of the events coming on events_realtime and events_dump streams
 *  into the yarch table events.
 * @author nm
 *
 */
public class EventRecorder extends AbstractService {
    static TupleDefinition eventTpdef;
    public static final String TABLE_NAME = "events";
    final String yamcsInstance;
    
    static final public String REALTIME_EVENT_STREAM_NAME = "events_realtime";
    
    public EventRecorder(String instance) throws StreamSqlException, ParseException {
        YarchDatabase ydb=YarchDatabase.getInstance(instance);
        this.yamcsInstance = instance;
        
        if(ydb.getTable(TABLE_NAME)==null) {
            ydb.execute("create table "+TABLE_NAME+"(gentime timestamp, source enum, seqNum int, body PROTOBUF('org.yamcs.protobuf.Yamcs$Event'), primary key(gentime, source, seqNum)) histogram(source)"
                    + " partition by time(gentime"+XtceTmRecorder.getTimePartitioningSchemaSql()+") table_format=compressed");
        }
        eventTpdef = ydb.getTable("events").getTupleDefinition();
        
        StreamConfig sc = StreamConfig.getInstance(instance);
        for(StreamConfigEntry sce: sc.getEntries()) {
            if(sce.getType() == StreamConfig.StandardStreamType.event) {
                ydb.execute("insert into "+TABLE_NAME+" select * from "+sce.getName());
            }
        }
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        YarchDatabase ydb=YarchDatabase.getInstance(yamcsInstance);
        StreamConfig sc = StreamConfig.getInstance(yamcsInstance);
            
        Utils.closeTableWriters(ydb,  sc.getEntries().stream().map(sce -> sce.getName()).collect(Collectors.toList()));

        notifyStopped();
    }

}
