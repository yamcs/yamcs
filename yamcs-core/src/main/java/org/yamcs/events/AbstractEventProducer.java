package org.yamcs.events;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Event.EventSeverity;
import org.yamcs.yarch.protobuf.Db.Event;

/**
 * Default implementation of an EventProducer that provides shortcut methods for sending message of different severity
 * types.
 */
public abstract class AbstractEventProducer implements EventProducer {
    private static final Logger log = LoggerFactory.getLogger(EventProducer.class);
    protected boolean logAllMessages = true;
    String source;
    AtomicInteger seqNo = new AtomicInteger();

    private boolean repeatedEventReduction; // Whether to check for message repetitions
    private Event originalEvent; // Original evt of a series of repeated events
    private Event lastRepeat; // Last evt of a series of repeated events
    private int repeatCounter = 0;
    private long repeatedEventTimeout = 60000; // how long in milliseconds to buffer repeated events

    // Flushes the Event Buffer about every minute
    private Timer flusher;

    @Override
    public void setSource(String source) {
        this.source = source;
    }

    @Override
    public void setSeqNo(int sn) {
        this.seqNo.set(sn);
    }


    @Override
    public void sendInfo(String msg) {
        sendInfo(getInvokingClass(), msg);
    }

    @Override
    public void sendWatch(String msg) {
        sendWatch(getInvokingClass(), msg);
    }

    @Override
    public void sendWarning(String msg) {
        sendWarning(getInvokingClass(), msg);
    }

    @Override
    public void sendCritical(String msg) {
        sendCritical(getInvokingClass(), msg);
    }

    @Override
    public void sendDistress(String msg) {
        sendDistress(getInvokingClass(), msg);
    }

    @Override
    public void sendSevere(String msg) {
        sendSevere(getInvokingClass(), msg);
    }

    private String getInvokingClass() {
        Throwable throwable = new Throwable();
        String classname = throwable.getStackTrace()[2].getClassName();
        int idx = classname.lastIndexOf('.');
        return classname.substring(idx + 1);
    }

    @Override
    public void sendEvent(EventSeverity severity, String type, String msg) {
        if (logAllMessages) {
            log.debug("event: {}; {}; {}", severity, type, msg);
        }
        Event.Builder eventb = newEvent().setSeverity(severity).setMessage(msg);
        if (type != null) {
            eventb.setType(type);
        }
        Event e = eventb.build();
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
     * By default event repetitions are checked for possible reduction. Disable if 'realtime' events are required.
     */
    @Override
    public synchronized void setRepeatedEventReduction(boolean repeatedEventReduction,
            long repeatedEventTimeoutMillisec) {
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
                    .setMessage("Repeated " + repeatCounter + " times: " + lastRepeat.getMessage())
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
                && originalEvent.hasType() == e.hasType()
                && (!originalEvent.hasType() || originalEvent.getType().equals(e.getType()));
    }

    @Override
    public Event.Builder newEvent() {
        long t = getMissionTime();
        return Event.newBuilder().setSource(source).setSeqNumber(seqNo.getAndIncrement()).setGenerationTime(t)
                .setReceptionTime(t);
    }

    public abstract long getMissionTime();
}
