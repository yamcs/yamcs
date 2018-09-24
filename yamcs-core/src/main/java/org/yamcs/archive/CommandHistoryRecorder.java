package org.yamcs.archive;

import java.util.Arrays;

import org.slf4j.Logger;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.YamcsService;
import org.yamcs.cmdhistory.StreamCommandHistoryPublisher;
import org.yamcs.utils.LoggingUtils;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.AbstractService;

/**
 * Records command history the key is formed by generation time, origin and sequence number the value is formed by a
 * arbitrary number of attributes
 * 
 * 
 * @author nm
 *
 */
public class CommandHistoryRecorder extends AbstractService implements YamcsService {
    final String instance;
    static TupleDefinition eventTpdef;
    final Logger log;
    final public static String TABLE_NAME = "cmdhist";

    public CommandHistoryRecorder(String instance) {
        this.instance = instance;
        log = LoggingUtils.getLogger(this.getClass(), instance);
    }

    @Override
    protected void doStart() {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);

        String keycols = StandardTupleDefinitions.TC.getStringDefinition1();
        try {
            if (ydb.getTable("cmdhist") == null) {
                String q = "create table cmdhist (" + keycols
                        + ", PRIMARY KEY(gentime, origin, seqNum)) histogram(cmdName) partition by time(gentime) table_format=compressed";
                ydb.execute(q);
            }

            Stream stream = ydb.getStream(StreamCommandHistoryPublisher.REALTIME_CMDHIST_STREAM_NAME);
            if (stream == null) {
                log.warn("The stream {} has not been found",
                        StreamCommandHistoryPublisher.REALTIME_CMDHIST_STREAM_NAME);
                notifyFailed(new Exception("The stream " + StreamCommandHistoryPublisher.REALTIME_CMDHIST_STREAM_NAME
                        + " has not been found"));
                return;
            }
            ydb.execute("upsert_append into " + TABLE_NAME + " select * from "
                    + StreamCommandHistoryPublisher.REALTIME_CMDHIST_STREAM_NAME);

            stream = ydb.getStream(StreamCommandHistoryPublisher.DUMP_CMDHIST_STREAM_NAME);
            if (stream == null) {
                log.warn("The stream {} has not been found", StreamCommandHistoryPublisher.DUMP_CMDHIST_STREAM_NAME);
                notifyFailed(new Exception("The stream " + StreamCommandHistoryPublisher.DUMP_CMDHIST_STREAM_NAME
                        + " has not been found"));
                return;
            }
            ydb.execute("upsert_append into " + TABLE_NAME + " select * from "
                    + StreamCommandHistoryPublisher.DUMP_CMDHIST_STREAM_NAME);

        } catch (Exception e) {
            log.error("Failed to setup the recording", e);
            notifyFailed(e);
            return;
        }

        notifyStarted();
    }

    @Override
    protected void doStop() {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        Utils.closeTableWriters(ydb, Arrays.asList(StreamCommandHistoryPublisher.REALTIME_CMDHIST_STREAM_NAME,
                StreamCommandHistoryPublisher.DUMP_CMDHIST_STREAM_NAME));
        notifyStopped();
    }
}
