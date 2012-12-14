package org.yamcs.archive;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.ThreadSafe;
import org.yamcs.YConfiguration;
import org.yamcs.usoctools.PayloadModel;
import org.yamcs.usoctools.XtceUtil;
import org.yamcs.xtce.MdbMappings;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;

import com.google.common.util.concurrent.AbstractService;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsSession;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.utils.CcsdsPacket;
import org.yamcs.utils.YObjectLoader;

/**
 * This class decodes Flight Segment Events.
 * @author nm
 *
 */
@ThreadSafe
public class FSEventDecoder extends AbstractService implements StreamSubscriber{
    String instance;
    Map<String,PayloadModel> opsnameToPayloadModel =new HashMap<String,PayloadModel>();
    private Logger log=LoggerFactory.getLogger(this.getClass().getName());
    YamcsClient msgClient;
    final SimpleString realtimeAddress, dumpAddress; //addresses where to send realtime and dump events
    
    final Stream realtimeTmStream, dumpTmStream; //TM packets come from here
    XtceUtil xtceutil;
    
    public FSEventDecoder(String instance) throws ConfigurationException {
        YConfiguration conf=YConfiguration.getConfiguration("yamcs."+instance);
        YObjectLoader<PayloadModel> objLoader=new YObjectLoader<PayloadModel>();
        
        List<Object> decoders=conf.getList("eventDecoders");
        try {
            for(Object d:decoders) {
                PayloadModel payloadModel;
                if(d instanceof String) {
                    log.debug("adding FS Event decoder "+d);
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
                log.debug("added payload model "+d+" for payload"+payloadModel.getPayloadName());
            }

            YarchDatabase dict=YarchDatabase.getInstance(instance);
            realtimeTmStream=dict.getStream(XtceTmRecorder.REALTIME_TM_STREAM_NAME);
            if(realtimeTmStream==null) throw new ConfigurationException("There is no stream named "+XtceTmRecorder.REALTIME_TM_STREAM_NAME);
            dumpTmStream=dict.getStream(XtceTmRecorder.DUMP_TM_STREAM_NAME);
            if(dumpTmStream==null) throw new ConfigurationException("There is no stream named "+XtceTmRecorder.DUMP_TM_STREAM_NAME);
            
            realtimeAddress=new SimpleString(instance+".events_realtime");
            dumpAddress=new SimpleString(instance+".events_dump");
            YamcsSession ys=YamcsSession.newBuilder().build();
            msgClient=ys.newClientBuilder().setDataProducer(true).build();
        } catch (HornetQException e) {
            throw new ConfigurationException(e.toString());
        } catch (YamcsApiException e) {
            throw new ConfigurationException(e.getMessage(),e);
        } catch (IOException e) {
            throw new ConfigurationException(e.toString());
        }
        xtceutil=XtceUtil.getInstance(XtceDbFactory.getInstance(instance));
        
        log.info("FSEventDecoder for instance "+instance+" started");
        realtimeTmStream.addSubscriber(this);
        dumpTmStream.addSubscriber(this);
    }

    /**
     * do nothing, we are just waiting for tuples to come
     */
    @Override
    protected void doStart() {
        notifyStarted();
    }

   @Override
   public void onTuple(Stream stream, Tuple t) {
        byte[] packet=(byte[])t.getColumn("packet");
        long rectime=(Long)t.getColumn("rectime");
        
        if(stream==realtimeTmStream) {
            processPacket(rectime, packet,realtimeAddress);
        } else {
            processPacket(rectime, packet, dumpAddress);
        }
        
    }
    
    @Override
    public void streamClosed(Stream stream) {//shouldn't happen
    }

    private void processPacket(long rectime, byte[] packet, SimpleString address) {
        ByteBuffer bb=ByteBuffer.wrap(packet);
        int packetId=CcsdsPacket.getPacketID(bb);
        int apid=CcsdsPacket.getAPID(bb);
        String opsName=xtceutil.getPacketNameByApidPacketid(apid, packetId, MdbMappings.MDB_OPSNAME);
        if(opsName==null) opsName=xtceutil.getPacketNameByPacketId(packetId, MdbMappings.MDB_OPSNAME);
        if(opsName==null) {
            log.info("cannot find an opsname for packetId " +packetId);
            return;
        }
        PayloadModel payloadModel=opsnameToPayloadModel.get(opsName);
        
        if(payloadModel==null) {
            return;
        }
        Collection<Event> events=payloadModel.decode(rectime, packet);
        if(events!=null) {
            try {
                for(Event ev:events) {
                    msgClient.sendData(address, ProtoDataType.EVENT, ev);
                }
            } catch (HornetQException e) {
                log.warn("Exception when sending event: "+e);
            }
        }
    }

  

    @Override
    protected void doStop() {
        // TODO Auto-generated method stub
        
    }
}
