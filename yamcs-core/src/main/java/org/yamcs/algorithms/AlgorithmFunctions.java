package org.yamcs.algorithms;

import org.yamcs.Processor;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.logging.Log;
import org.yamcs.mdb.ProcessorData;
import org.yamcs.time.Instant;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.mdb.Mdb;

/**
 * Library of functions available from within Algorithm scripts using this naming scheme:
 * <p>
 * The java method {@code AlgorithmFunctions.[method]} is available in scripts as {@code Yamcs.[method]}
 */
public class AlgorithmFunctions {
    private final Log log;
    public static final String DEFAULT_SOURCE = "CustomAlgorithm";

    private Mdb mdb;

    @Deprecated // Moved to EventLogFunctions
    private EventProducer eventProducer;

    private final String yamcsInstance;
    private final ProcessorData processorData;

    private final AlgorithmExecutionContext context;

    private final Processor processor;

    public AlgorithmFunctions(Processor processor, AlgorithmExecutionContext context) {
        this.yamcsInstance = processor.getInstance();

        log = new Log(AlgorithmFunctions.class, yamcsInstance);
        log.setContext(processor.getName());

        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance);
        eventProducer.setSource(DEFAULT_SOURCE);
        this.mdb = processor.getMdb();
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
        Parameter p = mdb.getParameter(parameter);
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
        return Instant.get(processor.getCurrentTime());
    }

    private String getAlgoName() {
        return new Throwable().getStackTrace()[2].getFileName();
    }

    /**
     * Print a trace message in the Yamcs log. If tracing is enabled on the algorithm, it is also added to the trace
     * log.
     */
    public void trace(String msg) {
        if (log.isTraceEnabled()) {
            log.trace(getAlgoName() + ": " + msg);
        }
        context.logTrace(getAlgoName(), msg);
    }

    /**
     * Print a debug message in the Yamcs log. If tracing is enabled on the algorithm, it is also added to the trace
     * log.
     */
    public void debug(String msg) {
        if (log.isDebugEnabled()) {
            log.debug(getAlgoName() + ": " + msg);
        }
        context.logTrace(getAlgoName(), msg);
    }

    /**
     * Print a message in the Yamcs log. If tracing is enabled on the algorithm, it is also added to the trace log.
     */
    public void log(String msg) {
        log.info(getAlgoName() + ": " + msg);
        context.logTrace(getAlgoName(), msg);
    }

    /**
     * Print a warning message in the Yamcs log. If tracing is enabled on the algorithm, it is also added to the trace
     * log.
     */
    public void warn(String msg) {
        log.warn(getAlgoName() + ": " + msg);
        context.logTrace(getAlgoName(), msg);
    }

    /**
     * Print an error message in the Yamcs log. If tracing is enabled on the algorithm, it is also added to the trace
     * log.
     */
    public void error(String msg) {
        log.error(getAlgoName() + ": " + msg);
        context.logTrace(getAlgoName(), msg);
    }

    @Deprecated
    public void info(String msg) {
        info(getAlgoName(), msg);
    }

    @Deprecated
    public void info(String type, String msg) {
        log.warn("Deprecated: use EventLog.info instead of Yamcs.info");
        eventProducer.sendInfo(type, msg);
    }

    @Deprecated
    public void info(String source, String type, String msg) {
        log.warn("Deprecated: use EventLog.info instead of Yamcs.info");
        eventProducer.setSource(source);
        eventProducer.sendInfo(type, msg);
        eventProducer.setSource(DEFAULT_SOURCE);
    }

    @Deprecated
    public void watch(String msg) {
        watch(getAlgoName(), msg);
    }

    @Deprecated
    public void watch(String type, String msg) {
        log.warn("Deprecated: use EventLog.watch instead of Yamcs.watch");
        eventProducer.sendWatch(type, msg);
    }

    @Deprecated
    public void watch(String source, String type, String msg) {
        log.warn("Deprecated: use EventLog.watch instead of Yamcs.watch");
        eventProducer.setSource(source);
        eventProducer.sendWatch(type, msg);
        eventProducer.setSource(DEFAULT_SOURCE);
    }

    @Deprecated
    public void warning(String msg) {
        warning(getAlgoName(), msg);
    }

    @Deprecated
    public void warning(String type, String msg) {
        log.warn("Deprecated: use EventLog.warning instead of Yamcs.warning");
        eventProducer.sendWarning(type, msg);
    }

    @Deprecated
    public void warning(String source, String type, String msg) {
        log.warn("Deprecated: use EventLog.warning instead of Yamcs.warning");
        eventProducer.setSource(source);
        eventProducer.sendWarning(type, msg);
        eventProducer.setSource(DEFAULT_SOURCE);
    }

    @Deprecated
    public void distress(String msg) {
        distress(getAlgoName(), msg);
    }

    @Deprecated
    public void distress(String type, String msg) {
        log.warn("Deprecated: use EventLog.distress instead of Yamcs.distress");
        eventProducer.sendDistress(type, msg);
    }

    @Deprecated
    public void distress(String source, String type, String msg) {
        log.warn("Deprecated: use EventLog.distress instead of Yamcs.distress");
        eventProducer.setSource(source);
        eventProducer.sendDistress(type, msg);
        eventProducer.setSource(DEFAULT_SOURCE);
    }

    @Deprecated
    public void critical(String msg) {
        critical(getAlgoName(), msg);
    }

    @Deprecated
    public void critical(String type, String msg) {
        log.warn("Deprecated: use EventLog.critical instead of Yamcs.critical");
        eventProducer.sendCritical(type, msg);
    }

    @Deprecated
    public void critical(String source, String type, String msg) {
        log.warn("Deprecated: use EventLog.critical instead of Yamcs.critical");
        eventProducer.setSource(source);
        eventProducer.sendCritical(type, msg);
        eventProducer.setSource(DEFAULT_SOURCE);
    }

    @Deprecated
    public void severe(String msg) {
        severe(getAlgoName(), msg);
    }

    @Deprecated
    public void severe(String type, String msg) {
        log.warn("Deprecated: use EventLog.severe instead of Yamcs.severe");
        eventProducer.sendSevere(type, msg);
    }

    @Deprecated
    public void severe(String source, String type, String msg) {
        log.warn("Deprecated: use EventLog.severe instead of Yamcs.severe");
        eventProducer.setSource(source);
        eventProducer.sendSevere(type, msg);
        eventProducer.setSource(DEFAULT_SOURCE);
    }

    /**
     * returns the processor name if the algorithm is running in a processor or null otherwise
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
