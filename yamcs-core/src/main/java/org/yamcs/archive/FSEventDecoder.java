package org.yamcs.archive;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.yamcs.ConfigurationException;
import org.yamcs.ThreadSafe;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.api.EventProducer;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.usoctools.PayloadModel;
import org.yamcs.usoctools.XtceUtil;
import org.yamcs.utils.CcsdsPacket;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.xtce.MdbMappings;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;

import com.google.common.util.concurrent.AbstractService;

/**
 * This class decodes Flight Segment Events.
 * @author nm
 *
 */
@ThreadSafe
public class FSEventDecoder extends AbstractService implements StreamSubscriber{
    String instance;
    Map<String,PayloadModel> opsnameToPayloadModel =new HashMap<String,PayloadModel>();
    private final Logger log;
    
    final Stream realtimeTmStream, dumpTmStream; //TM packets come from here
    XtceUtil xtceutil;
    EventProducer eventProducer;
    final Stream realtimeEventStream;
    final Stream dumpEventStream;
    final TupleDefinition tdef;
    
    public FSEventDecoder(String instance) throws ConfigurationException {
        log=YamcsServer.getLogger(this.getClass(), instance);
        YConfiguration conf=YConfiguration.getConfiguration("yamcs."+instance);
        YObjectLoader<PayloadModel> objLoader=new YObjectLoader<PayloadModel>();
        
        List<Object> decoders=conf.getList("eventDecoders");
        try {
            for(Object d:decoders) {
                PayloadModel payloadModel;
                if(d instanceof String) {
                    log.debug("Adding decoder "+d);
                    payloadModel = objLoader.loadObject((String)d, instance);
                } else if(d instanceof Map<?,?>) {
                    Map<?, ?> m = (Map<?, ?>) d; 
                    String className = YConfiguration.getString(m, "class");
                    Object args = m.get("args");
                    payloadModel = objLoader.loadObject(className, instance, args);
                } else {
                    throw new ConfigurationException("Event decoders have to be specified either by className or by {class: className, args: arguments} map");
                }
                for (String opsname:payloadModel.getEventPacketsOpsnames()) {
                    opsnameToPayloadModel.put(opsname, payloadModel);
                }
                log.debug("Added payload model "+d+" for payload"+payloadModel.getPayloadName());
            }

            YarchDatabase ydb = YarchDatabase.getInstance(instance);
            
            realtimeTmStream = ydb.getStream(XtceTmRecorder.REALTIME_TM_STREAM_NAME);
            if(realtimeTmStream==null) throw new ConfigurationException("There is no stream named "+XtceTmRecorder.REALTIME_TM_STREAM_NAME);
            
            dumpTmStream = ydb.getStream(XtceTmRecorder.DUMP_TM_STREAM_NAME);
            if(dumpTmStream==null) throw new ConfigurationException("There is no stream named "+XtceTmRecorder.DUMP_TM_STREAM_NAME);
            
            realtimeEventStream = ydb.getStream(EventRecorder.REALTIME_EVENT_STREAM_NAME);
            if(realtimeEventStream==null) throw new ConfigurationException("There is no stream named "+EventRecorder.REALTIME_EVENT_STREAM_NAME);
            tdef = realtimeEventStream.getDefinition();
            
            
            dumpEventStream = ydb.getStream(EventRecorder.DUMP_EVENT_STREAM_NAME);
            if(dumpEventStream==null) throw new ConfigurationException("There is no stream named "+EventRecorder.DUMP_EVENT_STREAM_NAME);

        } catch (IOException e) {
            throw new ConfigurationException(e.toString());
        }
        xtceutil=XtceUtil.getInstance(XtceDbFactory.getInstance(instance));
    
    }

    /**
     * do nothing, we are just waiting for tuples to come
     */
    @Override
    protected void doStart() {
        realtimeTmStream.addSubscriber(this);
        dumpTmStream.addSubscriber(this);
        notifyStarted();
    }

   @Override
   public void onTuple(Stream stream, Tuple t) {
        byte[] packet=(byte[])t.getColumn("packet");
        long rectime=(Long)t.getColumn("rectime");
        
        if(stream==realtimeTmStream) {
            processPacket(rectime, packet, realtimeEventStream);
        } else {
            processPacket(rectime, packet, dumpEventStream);
        }
        
    }
    
    @Override
    public void streamClosed(Stream stream) {//shouldn't happen
    }

   public void processPacket(long rectime, byte[] packet, Stream eventStream) {
        CcsdsPacket ccsdsPacket = new CcsdsPacket(packet);
        ByteBuffer bb=ByteBuffer.wrap(packet);
        int packetId=ccsdsPacket.getPacketID();
        int apid=CcsdsPacket.getAPID(bb);
        String opsName = xtceutil.getPacketNameByApidPacketid(apid, packetId, MdbMappings.MDB_OPSNAME);
        if(opsName==null) opsName=xtceutil.getPacketNameByPacketId(packetId, MdbMappings.MDB_OPSNAME);
        if(opsName==null) {
            log.info("Cannot find an opsname for packetId " +packetId);
            return;
        }
        PayloadModel payloadModel = opsnameToPayloadModel.get(opsName);
        
        if(payloadModel==null) {
            return;
        }
        Collection<Event> events=payloadModel.decode(rectime, packet);
        if(events!=null) {
            for(Event ev:events) {
                Tuple t=new Tuple(tdef, new Object[]{ev.getGenerationTime(), 
                        ev.getSource(), ev.getSeqNumber(), ev});
                eventStream.emitTuple(t);
            }
        }
    }

  

    @Override
    protected void doStop() {
        realtimeTmStream.removeSubscriber(this);
        dumpTmStream.removeSubscriber(this);
        notifyStopped();        
    }
}
