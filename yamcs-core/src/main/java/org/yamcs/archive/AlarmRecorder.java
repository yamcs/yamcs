package org.yamcs.archive;

import static org.yamcs.alarms.AlarmServer.ALARM_TUPLE_DEFINITION;

import java.util.List;

import org.yamcs.ConfigurationException;
import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.StreamConfig.StreamConfigEntry;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

import com.google.common.util.concurrent.AbstractService;

/**
 * Records alarms.
 * Uses a 'simple' upsert_append solution for now.
 */
public class AlarmRecorder extends AbstractService {

    public static final String TABLE_NAME = "alarms";
    
    public AlarmRecorder(String yamcsInstance) throws ConfigurationException, StreamSqlException, ParseException {
        YarchDatabase ydb = YarchDatabase.getInstance(yamcsInstance);

        String cols = ALARM_TUPLE_DEFINITION.getStringDefinition1();
        if (ydb.getTable(TABLE_NAME) == null) {
            String query="create table "+TABLE_NAME+"("+cols+", primary key(triggerTime, parameter, seqNum)) table_format=compressed";
            ydb.execute(query);
        }

        StreamConfig sc = StreamConfig.getInstance(yamcsInstance);
        List<StreamConfigEntry> sceList = sc.getEntries(StandardStreamType.alarm);
        for(StreamConfigEntry sce : sceList){
            Stream inputStream = ydb.getStream(sce.getName());
            if (inputStream == null) {
                throw new ConfigurationException("Cannot find stream '" + sce.getName() + "'");
            }
            ydb.execute("upsert_append into " + TABLE_NAME + " select * from " + sce.getName());
        }
    }
    
    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }
}
