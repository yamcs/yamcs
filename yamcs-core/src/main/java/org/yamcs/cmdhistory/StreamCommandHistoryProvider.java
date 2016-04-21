package org.yamcs.cmdhistory;

import java.util.List;

import org.yamcs.ConfigurationException;
import org.yamcs.commanding.InvalidCommandId;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.parameter.Value;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.tctm.TcUplinkerAdapter;
import org.yamcs.utils.ValueUtility;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;

import com.google.common.util.concurrent.AbstractService;

/**
 * 
 * provides command history from streams
 * @author nm
 *
 */
public class StreamCommandHistoryProvider  extends AbstractService implements CommandHistoryProvider, StreamSubscriber {

    CommandHistoryRequestManager chrm;
    Stream realtimeCmdHistoryStream; 

    
    @Override
    public void setCommandHistoryRequestManager(CommandHistoryRequestManager chrm) {
        this.chrm = chrm;
    }
    

    @Override
    public void onTuple(Stream s, Tuple tuple) {
        if(tuple.hasColumn(PreparedCommand.CNAME_SOURCE) && tuple.hasColumn(PreparedCommand.CNAME_BINARY)) {
            PreparedCommand pc=PreparedCommand.fromTuple(tuple);
            chrm.addCommand(pc);
        } else {
            int i=TcUplinkerAdapter.TC_TUPLE_DEFINITION.getColumnDefinitions().size();
            CommandId cmdId=PreparedCommand.getCommandId(tuple);
            List<ColumnDefinition> columns=tuple.getDefinition().getColumnDefinitions();
            while(i<columns.size()) {
                ColumnDefinition cd=columns.get(i++);
                String key=cd.getName();
                try {
                    Value v = ValueUtility.getColumnValue(cd, tuple.getColumn(key));
                    chrm.updateCommand(cmdId, key, v);
                } catch (InvalidCommandId e) {
                    e.printStackTrace();
                }
            }
        }
    }


    @Override
    public void streamClosed(Stream stream) {
    }


    @Override
    protected void doStart() {
        String instance = chrm.getInstance();
        YarchDatabase ydb = YarchDatabase.getInstance(chrm.getInstance());
        Stream realtimeCmdHistoryStream = ydb.getStream(YarchCommandHistoryAdapter.REALTIME_CMDHIST_STREAM_NAME);
        if(realtimeCmdHistoryStream == null) {
            String msg ="Cannot find stream '"+YarchCommandHistoryAdapter.REALTIME_CMDHIST_STREAM_NAME+" in instance "+instance; 
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
