package org.yamcs.algorithms;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Processor;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.mdb.ProcessorData;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;

/**
 * Library of functions available from within Algorithm scripts using this naming scheme:
 * <p>
 * The java method {@code AlgorithmFunctions.[method]} is available in scripts as {@code Yamcs.[method]}
 */
public class AlgorithmFunctions {
    private static final Logger log = LoggerFactory.getLogger(AlgorithmFunctions.class);
    public static final String DEFAULT_SOURCE = "CustomAlgorithm";

    private XtceDb xtcedb;
    private EventProducer eventProducer;
    private final String yamcsInstance;
    private final ProcessorData processorData;

    private final AlgorithmExecutionContext context;

    private final Processor processor;

    public AlgorithmFunctions(Processor processor, AlgorithmExecutionContext context) {
        this.yamcsInstance = processor.getInstance();

        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance);
        eventProducer.setSource(DEFAULT_SOURCE);
        this.xtcedb = processor.getXtceDb();
        this.processorData = processor.getProcessorData();
        this.processor = processor;
        this.context = context;
    }

    /**
     * Calibrate raw value according to the calibration rule of the given parameter
     * 
     * @return a Float or String object
     */
    public Object calibrate(int raw, String parameter) {
        Parameter p = xtcedb.getParameter(parameter);
        if (p != null) {
            if (p.getParameterType() instanceof EnumeratedParameterType) {
                EnumeratedParameterType ptype = (EnumeratedParameterType) p.getParameterType();
                return ptype.calibrate(raw);
            } else {
                DataEncoding encoding = ((BaseDataType) p.getParameterType()).getEncoding();
                if (encoding instanceof IntegerDataEncoding) {
                    return processorData.getCalibrator(null, encoding).calibrate(raw);
                }
            }
        } else {
            log.warn("Cannot find parameter {} to calibrate {}", parameter, raw);
        }
        return null;
    }

    public String instance() {
        return yamcsInstance;
    }


    public long processorTimeMillis() {
        return processor.getCurrentTime();
    }

    public Instant processorTime() {
        return Instant.ofEpochMilli(TimeEncoding.toUnixMillisec(processor.getCurrentTime()));
    }

    public void info(String msg) {
        info(getAlgoName(), msg);
    }

    private String getAlgoName() {
        return new Throwable().getStackTrace()[2].getFileName();
    }

    public void log(String msg) {
        context.logTrace(getAlgoName(), msg);
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

    /**
     * returns the processor name if the algorithm is running in a processor or null otherwise
     * 
     * @return
     */
    public String processorName() {
        return (processor == null) ? null : processor.getName();
    }

    /**
     * Little endian to host
     */
    public long letohl(int value) {
        long x = value & 0xFFFFFFFFl;
        return (((x >> 24) & 0xff) + ((x >> 8) & 0xff00) + ((x & 0xff00) << 8) + ((x & 0xff) << 24));
    }
}
