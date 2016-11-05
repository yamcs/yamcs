package org.yamcs.archive;

import java.util.Arrays;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.yamcs.api.YamcsApiException;
import org.yamcs.hornetq.EventTupleTranslator;
import org.yamcs.hornetq.StreamAdapter;
import org.yamcs.yarch.Stream;
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
    static final public String TABLE_NAME="events";
    static final public String REALTIME_EVENT_STREAM_NAME = "events_realtime";
    static final public String DUMP_EVENT_STREAM_NAME = "events_dump";
    final String yamcsInstance;

    public EventRecorder(String instance) throws StreamSqlException, ParseException, ActiveMQException, YamcsApiException {
        YarchDatabase ydb=YarchDatabase.getInstance(instance);
        this.yamcsInstance = instance;
        if(ydb.getTable(TABLE_NAME)==null) {
            ydb.execute("create table "+TABLE_NAME+"(gentime timestamp, source enum, seqNum int, body PROTOBUF('org.yamcs.protobuf.Yamcs$Event'), primary key(gentime, source, seqNum)) histogram(source)"
                    + " partition by time(gentime"+XtceTmRecorder.getTimePartitioningSchemaSql()+") table_format=compressed");
        }
        eventTpdef = ydb.getTable("events").getTupleDefinition();
        
        ydb.execute("insert into "+TABLE_NAME+" select * from "+REALTIME_EVENT_STREAM_NAME);
        ydb.execute("insert into "+TABLE_NAME+" select * from "+DUMP_EVENT_STREAM_NAME);
        
      
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        YarchDatabase ydb=YarchDatabase.getInstance(yamcsInstance);
        Utils.closeTableWriters(ydb,  Arrays.asList(REALTIME_EVENT_STREAM_NAME, DUMP_EVENT_STREAM_NAME));
      
        notifyStopped();
    }

}
