package org.yamcs.xtceproc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.api.QuietEventProducer;
import org.yamcs.xtce.Calibrator;
import org.yamcs.xtce.ContextCalibrator;
import org.yamcs.xtce.CriteriaEvaluator;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.JavaExpressionCalibrator;
import org.yamcs.xtce.MathOperationCalibrator;
import org.yamcs.xtce.NumericDataEncoding;
import org.yamcs.xtce.PolynomialCalibrator;
import org.yamcs.xtce.SplineCalibrator;
import org.yamcs.xtce.XtceDb;

/**
 * Holds information related and required for XTCE processing. 
 * It is separated from Processor because it has to be usable when not a full blown processor is available (e.g. XTCE packet processing)
 *  
 *  Ultimately should be connected with the ParameterCache for things that depend on the history (contextual alarms, contextual calibrations, algorithms, etc)
 *  
 *  Not thread safe
 *
 */
public class ProcessorData {
    /**
     * converts raw values into engineering values
     */
    final ParameterTypeProcessor parameterTypeProcessor = new ParameterTypeProcessor(this);

    private Map<Calibrator, CalibratorProc> calibrators = new HashMap<>();
    private Map<DataEncoding, DataDecoder> decoders = new HashMap<>();

    final XtceDb xtcedb;
    static Logger log = LoggerFactory.getLogger(SequenceEntryProcessor.class.getName());
    final EventProducer eventProducer;
    
    /**
     * 
     * @param xtcedb
     * @param generateEvents - generate events in case of errors when processing data 
     */
    public ProcessorData(String instance, String procName, XtceDb xtcedb, boolean generateEvents) {
        this.xtcedb = xtcedb;
        if((instance != null) && generateEvents) { 
            eventProducer = EventProducerFactory.getEventProducer(instance);
        } else {//instance can be null when running in test or as a library - in this case we don't generate events
            eventProducer = new QuietEventProducer();
        }
        eventProducer.setSource("PROC_"+procName);
        eventProducer.setRepeatedEventReduction(true, 10000);
    }
    
    /**
     * returns a calibrator processor for the given data encoding. 
     * Can be null if the DataEncoding does not define a calibrator.
     * 
     * @param de
     * @param result - if used in the context of container processing, this represents the intermediate result
     * 
     * @return a calibrator processor or null
     */
    public CalibratorProc getCalibrator(CriteriaEvaluator contextEvaluator, DataEncoding de) {
        if(de instanceof NumericDataEncoding) {
            NumericDataEncoding nde =  (NumericDataEncoding) de;
            Calibrator c = nde.getDefaultCalibrator();

            List<ContextCalibrator> clist = nde.getContextCalibratorList();
            if(clist!=null) {
                if(contextEvaluator==null) {
                    log.warn("For {} : context calibrators without a context evaluator", de);
                } else {
                    for(ContextCalibrator cc: clist) {
                        if(cc.getContextMatch().isMet(contextEvaluator)) {
                            c = cc.getCalibrator();
                            break;
                        }
                    }
                }
            }
            try {
                return getCalibrator(c);
            } catch (Exception e) {
                return null;
            }
        } else {
            throw new IllegalStateException("Calibrators not supported for: "+de);
        }
    }

    public CalibratorProc getDecalibrator(DataEncoding de) {
        return getCalibrator(null, de);
    }

    private CalibratorProc getCalibrator(Calibrator c) {
        if(c==null) {
            return null;
        }
        CalibratorProc calibrator = calibrators.get(c);
        if(calibrator==null) {
            if(c instanceof PolynomialCalibrator) {
                calibrator= new PolynomialCalibratorProc((PolynomialCalibrator) c);
            } else if(c instanceof SplineCalibrator) {
                calibrator = new SplineCalibratorProc((SplineCalibrator) c);
            } else if(c instanceof JavaExpressionCalibrator) {
                calibrator = JavaExpressionCalibratorFactory.compile((JavaExpressionCalibrator) c);
            } else if(c instanceof MathOperationCalibrator) {
                calibrator = MathOperationCalibratorFactory.compile((MathOperationCalibrator) c);
            }  else {
                throw new IllegalStateException("No calibrator processor for "+c);
            }
            calibrators.put(c, calibrator);
        }
        return calibrator;
    }

    public DataDecoder getDataDecoder(DataEncoding de) {
        DataDecoder dd = decoders.get(de);
        if(dd==null) {
            dd = DataDecoderFactory.get(de.getFromBinaryTransformAlgorithm());
        }

        return dd;
    }

    public XtceDb getXtceDb() {
        return xtcedb;
    }

    /**
     * Returns the parameter type processor (this is the guy that converts from raw to engineering value) used by the associated processor.
     * @return
     */
    public ParameterTypeProcessor getParameterTypeProcessor() {
        return parameterTypeProcessor;
    }

    public EventProducer getEventProducer() {
        return eventProducer;
    }
}
