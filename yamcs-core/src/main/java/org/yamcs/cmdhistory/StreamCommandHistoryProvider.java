package org.yamcs.cmdhistory;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.ConfigurationException;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.utils.ValueUtility;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.util.concurrent.AbstractService;

/**
 * 
 * provides command history from streams
 * 
 * @author nm
 *
 */
public class StreamCommandHistoryProvider extends AbstractService implements CommandHistoryProvider, StreamSubscriber {
    CommandHistoryRequestManager chrm;
    Stream realtimeCmdHistoryStream;
    String yamcsInstance;

    public StreamCommandHistoryProvider(String yamcsInstance) {
        this.yamcsInstance = yamcsInstance;
    }

    @Override
    public void setCommandHistoryRequestManager(CommandHistoryRequestManager chrm) {
        this.chrm = chrm;
    }

    @Override
    public void onTuple(Stream s, Tuple tuple) {
        // Skip stream update for 'first' tuple (username is set only in first message).
        if (!tuple.hasColumn(PreparedCommand.CNAME_USERNAME)) {
            int i = StandardTupleDefinitions.TC.getColumnDefinitions().size();
            CommandId cmdId = PreparedCommand.getCommandId(tuple);
            List<ColumnDefinition> columns = tuple.getDefinition().getColumnDefinitions();
            List<Attribute> l = new ArrayList<>(columns.size() - i);
            while (i < columns.size()) {
                ColumnDefinition cd = columns.get(i++);
                String name = cd.getName();
                if (!PreparedCommand.isProtectedColumn(name)) {
                    Value v = ValueUtility.getColumnValue(cd, tuple.getColumn(name));
                    l.add(new Attribute(name, v));
                }
            }
            chrm.updateCommand(cmdId, l);
        }
    }

    @Override
    public void streamClosed(Stream stream) {
        notifyFailed(new Exception("Stream " + stream.getName() + " closed"));
    }

    @Override
    protected void doStart() {
        String instance = chrm.getInstance();
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(chrm.getInstance());
        Stream realtimeCmdHistoryStream = ydb.getStream(StreamCommandHistoryPublisher.REALTIME_CMDHIST_STREAM_NAME);
        if (realtimeCmdHistoryStream == null) {
            String msg = "Cannot find stream '" + StreamCommandHistoryPublisher.REALTIME_CMDHIST_STREAM_NAME
                    + " in instance " + instance;
            notifyFailed(new ConfigurationException(msg));
        } else {
            realtimeCmdHistoryStream.addSubscriber(this);
            notifyStarted();
        }
    }

    @Override
    protected void doStop() {
        realtimeCmdHistoryStream.removeSubscriber(this);
        notifyStopped();
    }

}
