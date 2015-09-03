package org.yamcs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.StreamConfig.StreamConfigEntry;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.tctm.TmProviderAdapter;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;

import com.google.common.util.concurrent.AbstractService;

/**
 * Receives packets from yamcs streams and sends them to the Processor/TmProcessor for extraction of parameters.
 * 
 * Can read from multiple streams, each with its own root container used as start of XTCE packet processing
 * 
 * @author nm
 *
 */
public class StreamTmPacketProvider extends AbstractService implements TmPacketProvider {
    Stream stream;
    TmProcessor tmProcessor;
    volatile boolean disabled=false;
    volatile long lastPacketTime;
    
    
    List<StreamReader> readers = new ArrayList<StreamReader>();

    public StreamTmPacketProvider(String yamcsInstance, Map<String, Object> config) throws ConfigurationException {
        YarchDatabase ydb=YarchDatabase.getInstance(yamcsInstance);
        XtceDb xtcedb=XtceDbFactory.getInstance(yamcsInstance);

        if(!config.containsKey("streams")) throw new ConfigurationException("Cannot find key 'streams' in StreamTmPacketProvider");
        
        StreamConfig streamConfig = StreamConfig.getInstance(yamcsInstance);
        
        List<String> streams = (List<String>) config.get("streams");
        
        
        for(String streamName: streams) {
            StreamConfigEntry sce = streamConfig.getEntry(StandardStreamType.tm, streamName);
            SequenceContainer rootContainer;
            if(sce.getRootContainer()!=null) {
                rootContainer=sce.getRootContainer();
            } else {
                rootContainer=xtcedb.getRootSequenceContainer();
                if(rootContainer==null) throw new ConfigurationException("XtceDb does not have a root sequence container");    
            }
            Stream s=ydb.getStream(streamName);
            if(s==null) {
                throw new ConfigurationException("Cannot find stream '"+streamName+"'");
            }
            StreamReader reader = new StreamReader(s, rootContainer);
            readers.add(reader);
        }
    }

    @Override
    public void init(YProcessor proc, TmProcessor tmProcessor) {
        this.tmProcessor=tmProcessor;
    }

    @Override
    protected void doStart() {
        for(StreamReader sr:readers) {
            sr.stream.addSubscriber(sr);
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        for(StreamReader sr:readers) {
            sr.stream.removeSubscriber(sr);
        }
        notifyStopped();
    }

    @Override
    public boolean isArchiveReplay() {
        return false;
    }

    class StreamReader implements StreamSubscriber {
        Stream stream;
        SequenceContainer rootContainer;

        public StreamReader(Stream stream, SequenceContainer sc) {
            this.stream = stream;
            this.rootContainer = sc;
        }
        @Override
        public void onTuple(Stream s, Tuple tuple) {
            //the definition of tuple is in TmProviderAdapter
            long rectime = (Long)tuple.getColumn(TmProviderAdapter.RECTIME_COLUMN);
            long gentime = (Long)tuple.getColumn(TmProviderAdapter.GENTIME_COLUMN);
            byte[] packet=(byte[])tuple.getColumn(TmProviderAdapter.PACKET_COLUMN);
            PacketWithTime pwrt=new PacketWithTime(rectime,  gentime, packet);
            lastPacketTime = gentime;
            tmProcessor.processPacket(pwrt);
        }

        @Override
        public void streamClosed(Stream s) {
            notifyStopped();
        }
    }
}
