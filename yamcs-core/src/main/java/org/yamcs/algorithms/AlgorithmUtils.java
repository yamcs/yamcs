package org.yamcs.algorithms;

import javax.script.ScriptEngine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;

/**
 * Library of functions available from within Algorithm scripts using this
 * naming scheme:
 * <p>
 * The java method <tt>AlgorithmUtils.[method]</tt> is available in scripts as <tt>Yamcs.[method]</tt>
 */
class AlgorithmUtils {
    private static final Logger log = LoggerFactory.getLogger(AlgorithmUtils.class);
    private XtceDb xtcedb;
    private ScriptEngine engine;
    private EventProducer eventProducer;

    public AlgorithmUtils(String yamcsInstance, XtceDb xtcedb, ScriptEngine engine) {
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance);
        eventProducer.setSource("CustomAlgorithm");
        this.xtcedb = xtcedb;
        this.engine = engine;
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
                    return ((IntegerDataEncoding) encoding).getDefaultCalibrator().calibrate(raw);
                }
            }
        } else {
            log.warn(String.format("Cannot find parameter %s to calibrate %d", parameter, raw));
        }
        return null;
    }
    
    public void info(String msg) {
        info((String) engine.get(AlgorithmManager.KEY_ALGO_NAME), msg);
    }
    
    public void info(String type, String msg) {
        eventProducer.sendInfo(type, msg);
    }
    
    public void warning(String msg) {
        warning((String) engine.get(AlgorithmManager.KEY_ALGO_NAME), msg);
    }
    
    public void warning(String type, String msg) {
        eventProducer.sendWarning(type, msg);
    }
    
    public void error(String msg) {
        error((String) engine.get(AlgorithmManager.KEY_ALGO_NAME), msg);
    }
    
    public void error(String type, String msg) {
        eventProducer.sendError(type, msg);
    }

    /**
     * Little endian to host long
     */
    public long letohl(int value) {
        return ((value>>24)&0xff) + ((value>>8)&0xff00) + ((value&0xff00)<<8) + ((value&0xff)<<24);
    }
}
