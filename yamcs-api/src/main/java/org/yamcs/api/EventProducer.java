package org.yamcs.api;

import org.yamcs.protobuf.Yamcs.Event;

public interface EventProducer {

    public abstract void sendEvent(Event event);

    public abstract void setSource(String source);

    public abstract void setSeqNo(int sn);

    public abstract void sendError(String type, String msg);

    public abstract void sendWarning(String type, String msg);

    public abstract void sendInfo(String type, String msg);

    /**
     * Creates a default Event Builder with these fields pre-filled: source,
     * seqNo, receptionTime, generationTime
     */
    public abstract Event.Builder newEvent();
    
    /**
     * Closes the connection to the server; the producer is unusable after this is called
     */
    public void close();
}