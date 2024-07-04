package org.yamcs.archive;

import static org.yamcs.StandardTupleDefinitions.PARAMETER;
import static org.yamcs.StandardTupleDefinitions.PARAMETER_COL_GROUP;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.AbstractYamcsService;
import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.StreamConfig.StreamConfigEntry;
import org.yamcs.YConfiguration;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlException;

/**
 * ParameterRecorder Records (processed) Parameters
 * <p>
 * This records parameters as tuples - good for realtime recording but not very efficient for retrieval of a few
 * parameters over long time periods.
 * <p>
 * The {@link org.yamcs.parameterarchive} records parameters in a columnar fashion - not good for realtime but much more
 * efficient for retrieval especially retrieval of few parameters over long time periods.
 *
 */
public class ParameterRecorder extends AbstractYamcsService {

    public static final String TABLE_NAME = "pp";
    public static final String CF_NAME = XtceTmRecorder.CF_NAME;
    
    Stream realtimeStream;
    Stream dumpStream;

    List<String> streams = new ArrayList<>();

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("streams", OptionType.LIST).withElementType(OptionType.STRING);
        return spec;
    }

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        try {
            String cols = PARAMETER.getStringDefinition1();
            if (ydb.getTable(TABLE_NAME) == null) {
                var timePart = ydb.getTimePartitioningSchema(config);

                var partitionBy = timePart == null ? "partition by value(group)"
                        : "partition by time_and_value(gentime('" + timePart.getName() + "'), group)";

                String query = "create table " + TABLE_NAME + "(" + cols + ", primary key(gentime, seqNum)) histogram("
                        + PARAMETER_COL_GROUP + ") " + partitionBy
                        + " table_format=compressed,column_family:" + CF_NAME;
                ydb.execute(query);
            }

            StreamConfig sc = StreamConfig.getInstance(yamcsInstance);
            if (!config.containsKey("streams")) {
                List<StreamConfigEntry> sceList = sc.getEntries(StandardStreamType.PARAM);
                for (StreamConfigEntry sce : sceList) {
                    streams.add(sce.getName());
                    ydb.execute("insert_append into " + TABLE_NAME + " select * from " + sce.getName());
                }
            } else if (config.containsKey("streams")) {
                List<String> streamNames = config.getList("streams");
                for (String sn : streamNames) {
                    StreamConfigEntry sce = sc.getEntry(StandardStreamType.PARAM, sn);
                    if (sce == null) {
                        throw new ConfigurationException("No stream config found for '" + sn + "'");
                    }
                    streams.add(sce.getName());
                    ydb.execute("insert_append into " + TABLE_NAME + " select * from " + sce.getName());
                }
            }
        } catch (ParseException | StreamSqlException e) {
            throw new InitException("Exception when creating parameter input stream", e);
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
