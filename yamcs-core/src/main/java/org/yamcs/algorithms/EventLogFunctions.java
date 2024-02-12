package org.yamcs.algorithms;

import java.time.Instant;
import java.util.Map;

import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.protobuf.Event.EventSeverity;
import org.yamcs.utils.TimeEncoding;

/**
 * Library of functions available from within Algorithm scripts using this naming scheme:
 * <p>
 * The java method {@code EventLogFunctions.[method]} is available in scripts as {@code EventLog.[method]}
 */
public class EventLogFunctions {
    public static final String DEFAULT_SOURCE = "CustomAlgorithm";

    private final EventProducer eventProducer;

    public EventLogFunctions(String yamcsInstance) {
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance);
        eventProducer.setSource(DEFAULT_SOURCE);
    }

    private String getAlgoName() {
        return new Throwable().getStackTrace()[2].getFileName();
    }

    public void info(String msg) {
        info(getAlgoName(), msg);
    }

    public void info(String type, String msg) {
        eventProducer.sendInfo(type, msg);
    }

    public void info(String source, String type, String msg) {
        eventProducer.setSource(source);
        eventProducer.sendInfo(type, msg);
        eventProducer.setSource(DEFAULT_SOURCE);
    }

    public void info(Map<String, Object> event) {
        record(EventSeverity.INFO, event);
    }

    public void watch(String msg) {
        watch(getAlgoName(), msg);
    }

    public void watch(String type, String msg) {
        eventProducer.sendWatch(type, msg);
    }

    public void watch(String source, String type, String msg) {
        eventProducer.setSource(source);
        eventProducer.sendWatch(type, msg);
        eventProducer.setSource(DEFAULT_SOURCE);
    }

    public void watch(Map<String, Object> event) {
        record(EventSeverity.WATCH, event);
    }

    public void warning(String msg) {
        warning(getAlgoName(), msg);
    }

    public void warning(String type, String msg) {
        eventProducer.sendWarning(type, msg);
    }

    public void warning(String source, String type, String msg) {
        eventProducer.setSource(source);
        eventProducer.sendWarning(type, msg);
        eventProducer.setSource(DEFAULT_SOURCE);
    }

    public void warning(Map<String, Object> event) {
        record(EventSeverity.WARNING, event);
    }

    public void distress(String msg) {
        distress(getAlgoName(), msg);
    }

    public void distress(String type, String msg) {
        eventProducer.sendDistress(type, msg);
    }

    public void distress(String source, String type, String msg) {
        eventProducer.setSource(source);
        eventProducer.sendDistress(type, msg);
        eventProducer.setSource(DEFAULT_SOURCE);
    }

    public void distress(Map<String, Object> event) {
        record(EventSeverity.DISTRESS, event);
    }

    public void critical(String msg) {
        critical(getAlgoName(), msg);
    }

    public void critical(String type, String msg) {
        eventProducer.sendCritical(type, msg);
    }

    public void critical(String source, String type, String msg) {
        eventProducer.setSource(source);
        eventProducer.sendCritical(type, msg);
        eventProducer.setSource(DEFAULT_SOURCE);
    }

    public void critical(Map<String, Object> event) {
        record(EventSeverity.CRITICAL, event);
    }

    public void severe(String msg) {
        severe(getAlgoName(), msg);
    }

    public void severe(String type, String msg) {
        eventProducer.sendSevere(type, msg);
    }

    public void severe(String source, String type, String msg) {
        eventProducer.setSource(source);
        eventProducer.sendSevere(type, msg);
        eventProducer.setSource(DEFAULT_SOURCE);
    }

    public void severe(Map<String, Object> event) {
        record(EventSeverity.SEVERE, event);
    }

    private void record(EventSeverity severity, Map<String, Object> event) {
        var eventb = eventProducer.newEvent().setSeverity(severity);
        for (var entry : event.entrySet()) {
            switch (entry.getKey()) {
            case "message":
                eventb.setMessage((String) event.get("message"));
                break;
            case "type":
                eventb.setType((String) event.get("type"));
                break;
            case "source":
                eventb.setSource((String) event.get("source"));
                break;
            case "time":
                var time = Instant.parse((String) event.get("time"));
                eventb.setGenerationTime(TimeEncoding.fromUnixMillisec(time.toEpochMilli()));
                break;
            }
        }
        eventProducer.sendEvent(eventb.build());
    }
}
