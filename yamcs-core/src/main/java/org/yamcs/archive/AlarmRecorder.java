package org.yamcs.archive;

import java.util.List;

import org.yamcs.AbstractYamcsService;
import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.StreamConfig.StreamConfigEntry;
import org.yamcs.YConfiguration;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlException;

import static org.yamcs.alarms.AlarmStreamer.CNAME_TRIGGER_TIME;
/**
 * Records alarms. Uses a 'simple' upsert_append solution for now.
 */
public class AlarmRecorder extends AbstractYamcsService {

    public static final String PARAMETER_ALARM_TABLE_NAME = "alarms";
    public static final String EVENT_ALARM_TABLE_NAME = "event_alarms";

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        try {
            var timePart = ydb.getTimePartitioningSchema(config);

            var partitionBy = timePart == null ? ""
                    : "partition by time(" + CNAME_TRIGGER_TIME + "('" + timePart.getName() + "'))";

            if (ydb.getTable(PARAMETER_ALARM_TABLE_NAME) == null) {

                String cols = StandardTupleDefinitions.PARAMETER_ALARM.getStringDefinition1();
                String query = "create table " + PARAMETER_ALARM_TABLE_NAME + "(" + cols
                        + ", primary key(" + CNAME_TRIGGER_TIME + ", parameter, seqNum)) " + partitionBy
                        + " table_format=compressed";
                ydb.execute(query);
            }
            setupRecording(yamcsInstance, PARAMETER_ALARM_TABLE_NAME, StandardStreamType.PARAMETER_ALARM);

            if (ydb.getTable(EVENT_ALARM_TABLE_NAME) == null) {
                String cols = StandardTupleDefinitions.EVENT_ALARM.getStringDefinition1();
                String query = "create table " + EVENT_ALARM_TABLE_NAME + "(" + cols
                        + ", primary key(" + CNAME_TRIGGER_TIME + ", eventSource, seqNum)) " + partitionBy
                        + " table_format=compressed";
                ydb.execute(query);
            }

            setupRecording(yamcsInstance, EVENT_ALARM_TABLE_NAME, StandardStreamType.EVENT_ALARM);
        } catch (ParseException | StreamSqlException e) {
            throw new InitException(e);
        }
    }

    private void setupRecording(String yamcsInstance, String tblName, StandardStreamType stype)
            throws StreamSqlException, ParseException {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        StreamConfig sc = StreamConfig.getInstance(yamcsInstance);
        List<StreamConfigEntry> sceList = sc.getEntries(stype);
        for (StreamConfigEntry sce : sceList) {
            Stream inputStream = ydb.getStream(sce.getName());
            if (inputStream == null) {
                throw new ConfigurationException("Cannot find stream '" + sce.getName() + "'");
            }
            ydb.execute("upsert_append into " + tblName + " select * from " + sce.getName());
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
