package org.yamcs.algorithms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Processor;
import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.ProcessorData;

/**
 * Library of functions available from within Algorithm scripts using this
 * naming scheme:
 * <p>
 * The java method <tt>AlgorithmUtils.[method]</tt> is available in scripts as <tt>Yamcs.[method]</tt>
 */
public class AlgorithmUtils {
    private static final Logger log = LoggerFactory.getLogger(AlgorithmUtils.class);
    private XtceDb xtcedb;
    private EventProducer eventProducer;
    private final String yamcsInstance;
    private final ProcessorData processorData;

    //can be null if the algorithms are not running inside a processor
    private final Processor processor;
    public AlgorithmUtils(String yamcsInstance, ProcessorData processorData, Processor processor, XtceDb xtcedb) {
        this.yamcsInstance = yamcsInstance;
        
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance);
        eventProducer.setSource("CustomAlgorithm");
        this.xtcedb = xtcedb;
        this.processorData = processorData;
        this.processor = processor;
    }
    
    /**
     * Calibrate raw value according to the calibration rule of the given parameter
     * @return a Float or String object
     */
    public Object calibrate(int raw, String parameter) {
        Parameter p = xtcedb.getParameter(parameter);
        if (p != null) {
            if (p.getParameterType() instanceof EnumeratedParameterType) {
                EnumeratedParameterType ptype = (EnumeratedParameterType)p.getParameterType();
                return ptype.calibrate(raw);
            } else {
                DataEncoding encoding = ((BaseDataType)p.getParameterType()).getEncoding();
                if(encoding instanceof IntegerDataEncoding) {
                    return  processorData.getCalibrator(encoding).calibrate(raw);
                }
            }
        } else {
            log.warn(String.format("Cannot find parameter %s to calibrate %d", parameter, raw));
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
    public void info(String type, String msg) {
        
        eventProducer.sendInfo(type, msg);
    }
    
    public void warning(String msg) {
        warning(getAlgoName(), msg);
    }
    
    public void warning(String type, String msg) {
        eventProducer.sendWarning(type, msg);
    }
    
    public void error(String msg) {
        error(getAlgoName(), msg);
    }
    
    public void error(String type, String msg) {
        eventProducer.sendError(type, msg);
    }

    /**
     * returns the processor name if the algorithm is running in a processor or null otherwise
     * @return
     */
    public String processorName() {
        return (processor==null) ? null:processor.getName();
    }
    
    /**
     * Little endian to host long
     */
    public long letohl(int value) {
        return ((value>>24)&0xff) + ((value>>8)&0xff00) + ((value&0xff00)<<8) + ((value&0xff)<<24);
    }
}
