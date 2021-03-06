package org.yamcs.algorithms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Processor;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.ProcessorData;

/**
 * Library of functions available from within Algorithm scripts using this naming scheme:
 * <p>
 * The java method <tt>AlgorithmFunctions.[method]</tt> is available in scripts as <tt>Yamcs.[method]</tt>
 */
public class AlgorithmFunctions {
    private static final Logger log = LoggerFactory.getLogger(AlgorithmFunctions.class);
    private XtceDb xtcedb;
    private EventProducer eventProducer;
    private final String yamcsInstance;
    private final ProcessorData processorData;
    private final String defaultSource = "CustomAlgorithm";
    private final AlgorithmExecutionContext context;

    // can be null if the algorithms are not running inside a processor
    private final Processor processor;

    public AlgorithmFunctions(Processor processor, AlgorithmExecutionContext context) {
        this.yamcsInstance = processor.getInstance();

        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance);
        eventProducer.setSource(defaultSource);
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

    public void watch(String msg) {
        watch(getAlgoName(), msg);
    }

    public void watch(String type, String msg) {
        eventProducer.sendWatch(type, msg);
    }

    public void watch(String source, String type, String msg) {
        eventProducer.setSource(source);
        eventProducer.sendWatch(type, msg);
        eventProducer.setSource(defaultSource);
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
        eventProducer.setSource(defaultSource);
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
        eventProducer.setSource(defaultSource);
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
        eventProducer.setSource(defaultSource);
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
        eventProducer.setSource(defaultSource);
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
     * Little endian to host long
     */
    public long letohl(int value) {
        long x = value & 0xFFFFFFFFl;
        return (((x >> 24) & 0xff) + ((x >> 8) & 0xff00) + ((x & 0xff00) << 8) + ((x & 0xff) << 24));
    }
}
