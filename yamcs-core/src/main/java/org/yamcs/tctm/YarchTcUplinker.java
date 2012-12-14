package org.yamcs.tctm;

import org.yamcs.ConfigurationException;
import org.yamcs.cmdhistory.CommandHistory;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;

import com.google.common.util.concurrent.AbstractService;
/**
 * Sends commands to yarch streams
 * @author nm
 *
 */
public class YarchTcUplinker extends AbstractService implements TcUplinker{
    volatile boolean disabled=false;
    Stream stream;
    volatile long sentTcCount;
    
    public YarchTcUplinker(String archiveInstance, String streamName) throws ConfigurationException {
        YarchDatabase ydb=YarchDatabase.getInstance(archiveInstance);
        stream=ydb.getStream(streamName);
        if(stream==null) throw new ConfigurationException("Cannot find a stream named "+streamName);
    }
    
    
    @Override
    public String getLinkStatus() {
        return "OK";
    }

    @Override
    public String getDetailedStatus() {
        return "Pushing telecommands to "+stream;
    }

    @Override
    public void enable() {
        disabled=false;
    }

    @Override
    public void disable() {
        disabled=true;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public long getDataCount() {
        return sentTcCount;
    }

    @Override
    public void sendTc(PreparedCommand pc) {
        stream.emitTuple(pc.toTuple());
        sentTcCount++;
    }

    @Override
    public void setCommandHistoryListener(CommandHistory commandHistoryListener) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public String getFwLinkStatus() {
        return "OK";
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
