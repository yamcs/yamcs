package org.yamcs;

import org.yamcs.api.AbstractEventProducer;
import org.yamcs.archive.EventRecorder;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;

/**
 * Event producer used from inside Yamcs to report events.
 * It writes directly to the realtime_event stream
 * 
 * 
 * @author nm
 *
 */
public class StreamEventProducer extends AbstractEventProducer {
    final Stream realtimeEventStream;
    final TupleDefinition tdef;
    final TimeService timeService;
    
    public StreamEventProducer(String yamcsInstance) {
        realtimeEventStream = YarchDatabase.getInstance(yamcsInstance).getStream(EventRecorder.REALTIME_EVENT_STREAM_NAME);                
        if(realtimeEventStream==null) throw new ConfigurationException("Cannot find a stream named "+EventRecorder.REALTIME_EVENT_STREAM_NAME);
        
        tdef=realtimeEventStream.getDefinition();       
        timeService = YamcsServer.getTimeService(yamcsInstance);
    }
    
    @Override
    public void sendEvent(Event event) {
        Tuple t=new Tuple(tdef, new Object[]{event.getGenerationTime(), 
                event.getSource(), event.getSeqNumber(), event});
        
        realtimeEventStream.emitTuple(t);
    }

    @Override
    public void close() {        
    }

    @Override
    public long getMissionTime() {
        long t;
        if(timeService==null) {
            t = TimeEncoding.getWallclockTime();
        } else {
            t= timeService.getMissionTime();
        }
        return t;
    }

}
