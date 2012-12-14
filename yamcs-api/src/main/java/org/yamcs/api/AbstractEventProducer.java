package org.yamcs.api;

import java.util.concurrent.atomic.AtomicInteger;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;
import org.yamcs.utils.TimeEncoding;

public abstract class AbstractEventProducer implements EventProducer {
    
    SimpleString address;
    String source;
    AtomicInteger seqNo=new AtomicInteger();
    
    /* (non-Javadoc)
     * @see org.yamcs.api.EventProducer#setSource(java.lang.String)
     */
    @Override
    public void setSource(String source) {
        this.source=source;
    }
    
    /* (non-Javadoc)
     * @see org.yamcs.api.EventProducer#setSeqNo(int)
     */
    @Override
    public void setSeqNo(int sn) {
        this.seqNo.set(sn);
    }
    
    /* (non-Javadoc)
     * @see org.yamcs.api.EventProducer#sendError(java.lang.String, java.lang.String)
     */
    @Override
    public synchronized void sendError(String type, String msg) {
        Event.Builder eb=newEvent().setSeverity(EventSeverity.ERROR).setType(type).setMessage(msg);
        try {
            sendEvent(eb.build());
        } catch (HornetQException e) {
            e.printStackTrace();
        }
    }
    
    /* (non-Javadoc)
     * @see org.yamcs.api.EventProducer#sendWarning(java.lang.String, java.lang.String)
     */
    @Override
    public synchronized void sendWarning(String type, String msg) {
        Event.Builder eb=newEvent().setSeverity(EventSeverity.WARNING).setType(type).setMessage(msg);
        try {
            sendEvent(eb.build());
        } catch (HornetQException e) {
            e.printStackTrace();
        }
    }
    
    /* (non-Javadoc)
     * @see org.yamcs.api.EventProducer#sendInfo(java.lang.String, java.lang.String)
     */
    @Override
    public synchronized void sendInfo(String type, String msg) {
        Event.Builder eb=newEvent().setSeverity(EventSeverity.INFO).setType(type).setMessage(msg);
        try {
            sendEvent(eb.build());
        } catch (HornetQException e) {
            e.printStackTrace();
        }
    }

    private Event.Builder newEvent() {
        long t=TimeEncoding.currentInstant();
        return Event.newBuilder().setSource(source).
            setSeqNumber(seqNo.getAndIncrement()).setGenerationTime(t).
            setReceptionTime(t);
    }
}
