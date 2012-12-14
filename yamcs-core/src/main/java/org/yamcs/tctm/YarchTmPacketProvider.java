package org.yamcs.tctm;

import java.nio.ByteBuffer;

import org.yamcs.ConfigurationException;
import org.yamcs.TmProcessor;
import org.yamcs.archive.PacketWithTime;
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
    
    public YarchTmPacketProvider(String archiveInstance, String streamName) throws ConfigurationException {
        YarchDatabase ydb=YarchDatabase.getInstance(archiveInstance);
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
    public String getTmMode() {
        return "RT_NORM";
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
        long rectime = (Long)tuple.getColumn("rectime");
        long gentime = (Long)tuple.getColumn("gentime");
        byte[] packet=(byte[])tuple.getColumn("packet");
        PacketWithTime pwrt=new PacketWithTime(rectime,  gentime, ByteBuffer.wrap(packet));
        tmProcessor.processPacket(pwrt);
    }

    @Override
    public void streamClosed(Stream stream) {
       notifyStopped();
        
    }

    @Override
    public long getDataCount() {
        // TODO Auto-generated method stub
        return 0;
    }

}
