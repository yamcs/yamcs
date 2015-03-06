package org.yamcs.tctm;

import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.TmProcessor;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.archive.TmProviderAdapter;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;

import com.google.common.util.concurrent.AbstractService;

/**
 * Provides packets from yarch streams
 * @author nm
 *
 */
public class YarchTmPacketProvider extends AbstractService implements TmPacketProvider, StreamSubscriber {
    Stream stream;
    TmProcessor tmProcessor;
    volatile boolean disabled=false;
    
    public YarchTmPacketProvider(String archiveInstance, Map<String, String> config) throws ConfigurationException {
        YarchDatabase ydb=YarchDatabase.getInstance(archiveInstance);
        if(!config.containsKey("stream")) {
        	throw new ConfigurationException("the config(args) fo rYarchTmPacketProvider has to contain a parameter 'stream' - stream name for retrieving telemetry from");
        }
        String streamName = config.get("stream");
        
        stream=ydb.getStream(streamName);
        if(stream==null) throw new ConfigurationException("Cannot find a stream named "+streamName);
    }
    
    @Override
    public void setTmProcessor(TmProcessor tmProcessor) {
        this.tmProcessor=tmProcessor;
    }

    @Override
    public String getLinkStatus() {
        return "OK";
    }

    @Override
    public String getDetailedStatus() {
        return "retrieving data from "+stream;
    }

    @Override
    public void disable() {
        disabled=true;
        if(isRunning()) {
            stream.removeSubscriber(this);
        }
    }

    @Override
    public void enable() {
        stream.addSubscriber(this);
        disabled=false;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    protected void doStart() {
        stream.addSubscriber(this);
        notifyStarted();
    }

    @Override
    protected void doStop() {
        stream.removeSubscriber(this);
        notifyStopped();
    }
    
    @Override
    public boolean isArchiveReplay() {
        return false;
    }
    
    @Override
    public void onTuple(Stream s, Tuple tuple) {
        //the definition of tuple is in TmProviderAdapter
        long rectime = (Long)tuple.getColumn(TmProviderAdapter.RECTIME_COLUMN);
        long gentime = (Long)tuple.getColumn(TmProviderAdapter.GENTIME_COLUMN);
        byte[] packet=(byte[])tuple.getColumn(TmProviderAdapter.PACKET_COLUMN);
        PacketWithTime pwrt=new PacketWithTime(rectime,  gentime, packet);
        tmProcessor.processPacket(pwrt);
    }

    @Override
    public void streamClosed(Stream s) {
       notifyStopped();
        
    }

    @Override
    public long getDataCount() {
        // TODO Auto-generated method stub
        return 0;
    }

}
