package org.yamcs.xtceproc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Processor;
import org.yamcs.ProcessorConfig;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.events.QuietEventProducer;
import org.yamcs.parameter.LastValueCache;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.Value;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Calibrator;
import org.yamcs.xtce.ContextCalibrator;
import org.yamcs.xtce.CriteriaEvaluator;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.EnumerationAlarm;
import org.yamcs.xtce.EnumerationContextAlarm;
import org.yamcs.xtce.JavaExpressionCalibrator;
import org.yamcs.xtce.MathOperationCalibrator;
import org.yamcs.xtce.NumericAlarm;
import org.yamcs.xtce.NumericContextAlarm;
import org.yamcs.xtce.NumericDataEncoding;
import org.yamcs.xtce.NumericParameterType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.PolynomialCalibrator;
import org.yamcs.xtce.SplineCalibrator;
import org.yamcs.xtce.XtceDb;

/**
 * Holds information related and required for XTCE processing. It is separated from Processor because it has to be
 * usable when not a full blown processor is available (e.g. XTCE packet processing)
 * 
 * 
 * Contains a cache of the last known value for each parameter
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

    private Map<String, Object> userData = new HashMap<>();

    private LastValueCache lastValueCache = new LastValueCache();

    /**
     * used to store parameter types which are changed dynamically (so they don't correspond anymore to MDB)
     */
    Map<Parameter, ParameterType> typeOverrides = new HashMap<>();
    String yamcsInstance;

    private ProcessorConfig processorConfig;

    public ProcessorData(Processor proc, ProcessorConfig config) {
        this(proc.getInstance(), proc.getName(), proc.getXtceDb(), config);

        long genTime = TimeEncoding.getWallclockTime();
        // populate with /yamcs/processor variables (these never change)
        ParameterValue procNamePv = getProcessorPV(xtcedb, genTime, "name", proc.getName());
        lastValueCache.put(procNamePv.getParameter(), procNamePv);

        String mode = proc.isReplay() ? "replay" : "realtime";
        ParameterValue procModePv = getProcessorPV(xtcedb, genTime, "mode", mode);
        lastValueCache.put(procModePv.getParameter(), procModePv);
    }

    /**
     * @param xtcedb
     * @param config
     *            - generate events in case of errors when processing data
     */
    public ProcessorData(String instance, String procName, XtceDb xtcedb, ProcessorConfig config) {
        this.yamcsInstance = instance;
        this.xtcedb = xtcedb;
        this.processorConfig = config;

        if ((instance != null) && config.generateEvents()) {
            eventProducer = EventProducerFactory.getEventProducer(instance);
        } else {// instance can be null when running in test or as a library - in this case we don't generate events
            eventProducer = new QuietEventProducer();
        }
        eventProducer.setSource("PROCESOR(" + procName + ")");
        eventProducer.setRepeatedEventReduction(true, 10000);

        // populate last value cache with the default (initial) value for each parameter that has one
        long genTime = TimeEncoding.getWallclockTime();
        for (Parameter p : xtcedb.getParameters()) {
            ParameterType ptype = p.getParameterType();
            if (ptype != null) {

                Object o = p.getInitialValue();

                if (o == null) {
                    o = ptype.getInitialValue();
                }
                if (o != null) {
                    Value v = DataTypeProcessor.getValueForType(ptype, o);
                    ParameterValue pv = new ParameterValue(p);
                    pv.setEngineeringValue(v);
                    pv.setGenerationTime(genTime);
                    pv.setAcquisitionTime(genTime);
                    lastValueCache.put(p, pv);
                }
            }
        }

        log.debug("Initialized lastValueCache with {} entries", lastValueCache.size());

    }

    private ParameterValue getProcessorPV(XtceDb xtceDb, long time, String name, String value) {
        ParameterValue pv = new ParameterValue(
                xtceDb.createSystemParameter((XtceDb.YAMCS_SPACESYSTEM_NAME + "/processor/" + name)));
        pv.setAcquisitionTime(time);
        pv.setGenerationTime(time);
        pv.setStringValue(value);
        return pv;
    }

    /**
     * Set some object to be shared with all the users of this processor data
     * 
     * @param key
     * @param value
     */
    public <T> void setUserData(String key, T value) {
        userData.put(key, value);
    }

    /**
     * Get the instance of the user defined object if any. Returns null if no data has been set.
     * 
     * @param key
     * @return
     */
    @SuppressWarnings("unchecked")
    public <T> T getUserData(String key) {
        return (T) userData.get(key);
    }

    /**
     * returns a calibrator processor for the given data encoding. Can be null if the DataEncoding does not define a
     * calibrator.
     * 
     * @return a calibrator processor or null
     */
    public CalibratorProc getCalibrator(CriteriaEvaluator contextEvaluator, DataEncoding de) {
        if (de instanceof NumericDataEncoding) {
            NumericDataEncoding nde = (NumericDataEncoding) de;
            Calibrator c = nde.getDefaultCalibrator();

            List<ContextCalibrator> clist = nde.getContextCalibratorList();
            if (clist != null) {
                if (contextEvaluator == null) {
                    log.warn("For {} : context calibrators without a context evaluator", de);
                } else {
                    for (ContextCalibrator cc : clist) {
                        if (cc.getContextMatch().isMet(contextEvaluator)) {
                            c = cc.getCalibrator();
                            break;
                        }
                    }
                }
            }
            try {
                return getCalibrator(c);
            } catch (Exception e) {
                eventProducer.sendWarning("Could not get calibrator processor for " + c + ": " + e.getMessage());
                return null;
            }
        } else {
            throw new IllegalStateException("Calibrators not supported for: " + de);
        }
    }

    public CalibratorProc getDecalibrator(DataEncoding de) {
        return getCalibrator(null, de);
    }

    private CalibratorProc getCalibrator(Calibrator c) {
        if (c == null) {
            return null;
        }
        CalibratorProc calibrator = calibrators.get(c);
        if (calibrator == null) {
            if (c instanceof PolynomialCalibrator) {
                calibrator = new PolynomialCalibratorProc((PolynomialCalibrator) c);
            } else if (c instanceof SplineCalibrator) {
                calibrator = new SplineCalibratorProc((SplineCalibrator) c);
            } else if (c instanceof JavaExpressionCalibrator) {
                calibrator = JavaExpressionCalibratorFactory.compile((JavaExpressionCalibrator) c);
            } else if (c instanceof MathOperationCalibrator) {
                calibrator = MathOperationCalibratorFactory.compile((MathOperationCalibrator) c);
            } else {
                throw new IllegalStateException("No calibrator processor for " + c);
            }
            calibrators.put(c, calibrator);
        }
        return calibrator;
    }

    public DataDecoder getDataDecoder(DataEncoding de) {
        DataDecoder dd = decoders.get(de);
        if (dd == null) {
            dd = DataDecoderFactory.get(de.getFromBinaryTransformAlgorithm());
        }

        return dd;
    }

    public XtceDb getXtceDb() {
        return xtcedb;
    }

    /**
     * Returns the parameter type processor (this is the guy that converts from raw to engineering value) used by the
     * associated processor.
     * 
     * @return
     */
    public ParameterTypeProcessor getParameterTypeProcessor() {
        return parameterTypeProcessor;
    }

    public EventProducer getEventProducer() {
        return eventProducer;
    }

    public LastValueCache getLastValueCache() {
        return lastValueCache;
    }

    public ParameterType getParameterType(Parameter parameter) {
        ParameterType pt = typeOverrides.get(parameter);
        if (pt == null) {
            pt = parameter.getParameterType();
        }
        return pt;
    }

    public void clearParameterOverrides(Parameter p) {
        typeOverrides.remove(p);
    }

    public void clearParameterCalibratorOverrides(Parameter p) {
        ParameterType ptype = typeOverrides.get(p);
        if (ptype == null) {
            return;
        }
        ptype.setEncoding(p.getParameterType().getEncoding());
    }

    public void setDefaultCalibrator(Parameter p, Calibrator defaultCalibrator) {
        NumericParameterType ptype = getNumericTypeOverride(p);
        DataEncoding enc = ptype.getEncoding().copy();
        ptype.setEncoding(enc);

        ((NumericDataEncoding) enc).setDefaultCalibrator(defaultCalibrator);
    }

    public void setContextCalibratorList(Parameter p, List<ContextCalibrator> contextCalibrator) {
        NumericParameterType ptype = getNumericTypeOverride(p);
        DataEncoding enc = ptype.getEncoding().copy();
        ptype.setEncoding(enc);

        ((NumericDataEncoding) enc).setContextCalibratorList(contextCalibrator);

    }

    private NumericParameterType getNumericTypeOverride(Parameter p) {
        ParameterType ptype = typeOverrides.get(p);
        if (ptype == null) {
            ptype = p.getParameterType();
            if (!(ptype instanceof NumericParameterType)) {
                throw new IllegalArgumentException("'" + ptype.getName() + "' is a non numeric type");
            }
            ptype = ptype.copy();
            typeOverrides.put(p, ptype);
        }

        return (NumericParameterType) ptype;
    }

    private EnumeratedParameterType getEnumeratedTypeOverride(Parameter p) {
        ParameterType ptype = typeOverrides.get(p);
        if (ptype == null) {
            ptype = p.getParameterType();
            if (!(ptype instanceof EnumeratedParameterType)) {
                throw new IllegalArgumentException("'" + ptype.getName() + "' is a non enumerated type");
            }
            ptype = ptype.copy();
            typeOverrides.put(p, ptype);
        }

        return (EnumeratedParameterType) ptype;
    }

    public void clearParameterAlarmOverrides(Parameter p) {
        ParameterType ptype = typeOverrides.get(p);
        if (ptype == null) {
            return;
        }
        if (ptype instanceof NumericParameterType) {
            NumericParameterType optype = (NumericParameterType) p.getParameterType();
            ((NumericParameterType) ptype).setDefaultAlarm(optype.getDefaultAlarm());
        } else if (ptype instanceof EnumeratedParameterType) {
            EnumeratedParameterType optype = (EnumeratedParameterType) p.getParameterType();
            ((EnumeratedParameterType) ptype).setDefaultAlarm(optype.getDefaultAlarm());
        } else {
            throw new IllegalArgumentException("Can only have alarms on numeric and enumerated parameters");
        }
    }

    public void removeDefaultCalibrator(Parameter p) {
        setDefaultCalibrator(p, null);
    }

    public void removeDefaultAlarm(Parameter p) {
        NumericParameterType ptype = getNumericTypeOverride(p);
        ptype.setDefaultAlarm(null);
    }

    public void setDefaultNumericAlarm(Parameter p, NumericAlarm alarm) {
        NumericParameterType ptype = getNumericTypeOverride(p);
        ptype.setDefaultAlarm(alarm);
    }

    public void setDefaultEnumerationAlarm(Parameter p, EnumerationAlarm alarm) {
        EnumeratedParameterType ptype = getEnumeratedTypeOverride(p);
        ptype.setDefaultAlarm(alarm);
    }

    public void setNumericContextAlarm(Parameter p, List<NumericContextAlarm> contextAlarmList) {
        NumericParameterType ptype = getNumericTypeOverride(p);
        ptype.setContextAlarmList(contextAlarmList);
    }

    public void setEnumerationContextAlarm(Parameter p, List<EnumerationContextAlarm> contextAlarmList) {
        EnumeratedParameterType ptype = getEnumeratedTypeOverride(p);
        ptype.setContextAlarmList(contextAlarmList);
    }

    public String getYamcsInstance() {
        return yamcsInstance;
    }

    public ProcessorConfig getProcessorConfig() {
        return processorConfig;
    }

}
