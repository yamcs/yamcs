package org.yamcs.api;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;

/**
 * Default implementation of an EventProducer that provides shortcut methods for
 * sending message of different severity types.
 */
public abstract class AbstractEventProducer implements EventProducer {
    String source;
    AtomicInteger seqNo = new AtomicInteger();

    private boolean repeatedEventReduction; // Whether to check for message repetitions
    private Event originalEvent; // Original evt of a series of repeated events
    private Event lastRepeat; // Last evt of a series of repeated events
    private int repeatCounter = 0;
    private long repeatedEventTimeout = 60000; //how long in milliseconds to buffer repeated events
    
    
    // Flushes the Event Buffer about each minute
    private Timer flusher;

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
        sendMessage(EventSeverity.ERROR, type, msg);
    }

    /* (non-Javadoc)
     * @see org.yamcs.api.EventProducer#sendWarning(java.lang.String, java.lang.String)
     */
    @Override
    public synchronized void sendWarning(String type, String msg) {
        sendMessage(EventSeverity.WARNING, type, msg);
    }

    /* (non-Javadoc)
     * @see org.yamcs.api.EventProducer#sendInfo(java.lang.String, java.lang.String)
     */
    @Override
    public synchronized void sendInfo(String type, String msg) {
        sendMessage(EventSeverity.INFO, type, msg);
    }

    public synchronized void sendWatch(String type, String msg) {
        sendMessage(EventSeverity.WATCH, type, msg);
    }
    public synchronized void sendDistress(String type, String msg) {
        sendMessage(EventSeverity.DISTRESS, type, msg);
    }
    public synchronized void sendCritical(String type, String msg) {
        sendMessage(EventSeverity.CRITICAL, type, msg);
    }
    public synchronized void sendSevere(String type, String msg) {
        sendMessage(EventSeverity.SEVERE, type, msg);
    }
    
    private void sendMessage(EventSeverity severity, String type, String msg) {
        Event e = newEvent().setSeverity(severity).setType(type).setMessage(msg).build();
        if (!repeatedEventReduction) {
            sendEvent(e);
        } else {
            if (originalEvent == null) {
                sendEvent(e);
                originalEvent = e;
            } else if (isRepeat(e)) {
                if (flusher == null) { // Prevent buffering repeated events forever
                    flusher = new Timer(true);
                    flusher.scheduleAtFixedRate(new TimerTask() {
                        @Override
                        public void run() {
                            flushEventBuffer(false);
                        }
                    }, repeatedEventTimeout, repeatedEventTimeout);
                }
                lastRepeat = e;
                repeatCounter++;
            } else { // No more repeats
                if (flusher != null) {
                    flusher.cancel();
                    flusher = null;
                }
                flushEventBuffer(true);
                sendEvent(e);
                originalEvent = e;
                lastRepeat = null;
            }
        }
    }

    /** 
     * By default event repetitions are checked for possible reduction. Disable if
     * 'realtime' events are required.
     */
    public synchronized void setRepeatedEventReduction(boolean repeatedEventReduction, long repeatedEventTimeoutMillisec) {
        this.repeatedEventReduction = repeatedEventReduction;
        this.repeatedEventTimeout = repeatedEventTimeoutMillisec;
        if (!repeatedEventReduction) {
            if (flusher != null) {
                flusher.cancel();
                flusher = null;
            }
            flushEventBuffer(true);
        }
    }

    protected synchronized void flushEventBuffer(boolean startNewSequence) {
        if (repeatCounter > 1) {
            sendEvent(Event.newBuilder(lastRepeat)
                    .setMessage("Repeated "+repeatCounter+" times: "+lastRepeat.getMessage())
                    .build());
        } else if (repeatCounter == 1) {
            sendEvent(lastRepeat);
            lastRepeat = null;
        }
        if (startNewSequence) {
            originalEvent = null;
        }
        repeatCounter = 0;
    }

    /**
     * Checks whether the specified Event is a repeat of the previous Event.
     */
    private boolean isRepeat(Event e) {
        if (originalEvent == e) {
            return true;
        }
        return originalEvent.getMessage().equals(e.getMessage())
                && originalEvent.getSeverity().equals(e.getSeverity())
                && originalEvent.getSource().equals(e.getSource())
                && originalEvent.getType().equals(e.getType());
    }

    @Override
    public Event.Builder newEvent() {
        long t = getMissionTime();
        return Event.newBuilder().setSource(source).
                setSeqNumber(seqNo.getAndIncrement()).setGenerationTime(t).
                setReceptionTime(t);
    }

    public abstract long getMissionTime() ;
}
