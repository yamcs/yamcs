package org.yamcs.archive;

import java.util.List;
import java.util.stream.Collectors;

import org.yamcs.AbstractYamcsService;
import org.yamcs.ConfigurationException;
import org.yamcs.Spec;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.StreamConfig;
import org.yamcs.Spec.OptionType;
import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

/**
 * Records command history the key is formed by generation time, origin and sequence number the value is formed by a
 * arbitrary number of attributes
 * 
 * 
 * @author nm
 *
 */
public class CommandHistoryRecorder extends AbstractYamcsService {

    public static final String TABLE_NAME = "cmdhist";

    static TupleDefinition eventTpdef;
    List<String> streamNames;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("streams", OptionType.LIST).withElementType(OptionType.STRING);
        return spec;
    }

    @Override
    protected void doStart() {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);

        String keycols = StandardTupleDefinitions.TC.getStringDefinition1();
        try {
            if (ydb.getTable(TABLE_NAME) == null) {
                var timePart = ydb.getTimePartitioningSchema(config);

                var partitionBy = timePart == null ? ""
                        : "partition by time(gentime('" + timePart.getName() + "'))";

                String q = "create table "+TABLE_NAME+" (" + keycols
                        + ", PRIMARY KEY(gentime, origin, seqNum)) histogram(cmdName) " + partitionBy
                        + " table_format=compressed";
                ydb.execute(q);
            }
            if (config.containsKey("streams")) {
                streamNames = config.getList("streams");
            } else {
                streamNames = StreamConfig.getInstance(yamcsInstance)
                    .getEntries(StandardStreamType.CMD_HIST).stream().map(sce -> sce.getName()).collect(Collectors.toList());
            }
            if (streamNames.isEmpty()) {
                notifyFailed(new ConfigurationException(
                        "No command history streams have been configured. Please remove this service if the command history is not used."));
                return;
            }
            
            for (String sn: streamNames) {
                Stream stream = ydb.getStream(sn);
                if (stream == null) {
                    log.warn("The stream {} has not been found", sn);
                    notifyFailed(new ConfigurationException("The stream " + sn + " has not been found"));
                    return;
                }
                ydb.execute("upsert_append into " + TABLE_NAME + " select * from "+sn);
            }
        } catch (Exception e) {
            log.error("Failed to setup the recording", e);
            notifyFailed(e);
            return;
        }

        notifyStarted();
    }

    @Override
    protected void doStop() {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        Utils.closeTableWriters(ydb, StreamConfig.getInstance(yamcsInstance)
                .getEntries(StandardStreamType.CMD_HIST).stream().map(sce -> sce.getName())
                .collect(Collectors.toList()));
        notifyStopped();
    }
}
