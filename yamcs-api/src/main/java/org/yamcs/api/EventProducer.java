package org.yamcs.api;

import org.hornetq.api.core.HornetQException;
import org.yamcs.protobuf.Yamcs.Event;

public interface EventProducer {

    public abstract void sendEvent(Event event) throws HornetQException;

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
}