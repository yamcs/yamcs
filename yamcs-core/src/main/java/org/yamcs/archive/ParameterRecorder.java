package org.yamcs.archive;

import static org.yamcs.StandardTupleDefinitions.PARAMETER;
import static org.yamcs.StandardTupleDefinitions.PARAMETER_COL_GROUP;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.StreamConfig.StreamConfigEntry;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsService;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.AbstractService;

/**
 * ParameterRecorder Records (processed) Parameters
 * 
 * The base table definition is {@link ParameterDataLinkInitialiser}
 * 
 * This records parameters as tuples - good for realtime recording but not very efficient for retrieval of a few
 * parameters over long time periods.
 * 
 * The {@link org.yamcs.parameterarchive} records parameters in a columnar fashion - not good for realtime but much more
 * efficient for retrieval especially retrieval of few parameters over long time periods.
 * 
 * @author nm
 *
 */
public class ParameterRecorder extends AbstractService implements YamcsService {

    String yamcsInstance;
    Stream realtimeStream, dumpStream;

    static public final String TABLE_NAME = "pp";
    List<String> streams = new ArrayList<>();

    public ParameterRecorder(String yamcsInstance) {
        this(yamcsInstance, null);
    }

    public ParameterRecorder(String yamcsInstance, Map<String, Object> config) {
        this.yamcsInstance = yamcsInstance;
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        try {
            String cols = PARAMETER.getStringDefinition1();
            if (ydb.getTable(TABLE_NAME) == null) {
                String query = "create table " + TABLE_NAME + "(" + cols + ", primary key(gentime, seqNum)) histogram("
                        + PARAMETER_COL_GROUP + ") partition by time_and_value(gentime"
                        + XtceTmRecorder.getTimePartitioningSchemaSql() + ",group) table_format=compressed";
                ydb.execute(query);
            }

            StreamConfig sc = StreamConfig.getInstance(yamcsInstance);
            if (config == null || !config.containsKey("streams")) {
                List<StreamConfigEntry> sceList = sc.getEntries(StandardStreamType.param);
                for (StreamConfigEntry sce : sceList) {
                    streams.add(sce.getName());
                    ydb.execute("insert_append into " + TABLE_NAME + " select * from " + sce.getName());
                }
            } else if (config.containsKey("streams")) {
                List<String> streamNames = YConfiguration.getList(config, "streams");
                for (String sn : streamNames) {
                    StreamConfigEntry sce = sc.getEntry(StandardStreamType.param, sn);
                    if (sce == null) {
                        throw new ConfigurationException("No stream config found for '" + sn + "'");
                    }
                    streams.add(sce.getName());
                    ydb.execute("insert_append into " + TABLE_NAME + " select * from " + sce.getName());
                }
            }
        } catch (Exception e) {
            throw new ConfigurationException("exception when creating parameter input stream", e);
        }
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        Utils.closeTableWriters(ydb, streams);
        notifyStopped();
    }

}
