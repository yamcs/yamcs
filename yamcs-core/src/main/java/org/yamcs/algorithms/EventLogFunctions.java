package org.yamcs.algorithms;

import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;

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
}
