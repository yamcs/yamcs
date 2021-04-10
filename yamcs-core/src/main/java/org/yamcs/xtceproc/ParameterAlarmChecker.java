package org.yamcs.xtceproc;

import java.util.Iterator;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.alarms.AlarmReporter;
import org.yamcs.alarms.AlarmServer;
import org.yamcs.parameter.LastValueCache;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.RangeCondition;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.xtce.AlarmLevels;
import org.yamcs.xtce.AlarmRanges;
import org.yamcs.xtce.AlarmType;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.EnumerationAlarm;
import org.yamcs.xtce.EnumerationAlarm.EnumerationAlarmItem;
import org.yamcs.xtce.util.DoubleRange;
import org.yamcs.xtceproc.MatchCriteriaEvaluator.EvaluatorInput;
import org.yamcs.xtceproc.MatchCriteriaEvaluator.MatchResult;
import org.yamcs.xtce.EnumerationContextAlarm;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.NumericAlarm;
import org.yamcs.xtce.NumericContextAlarm;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;

/**
 * Part of the TM processing chain. Is called upon by the
 * ParameterRequestManager whenever a new parameter value may need alarms
 * published together with it.
 */
public class ParameterAlarmChecker {

    private AlarmReporter alarmReporter;

    private AlarmServer<Parameter, ParameterValue> alarmServer;

    ParameterRequestManager prm;
    Logger log = LoggerFactory.getLogger(this.getClass());

    LastValueCache lastValueCache;
    final ProcessorData pdata;

    public ParameterAlarmChecker(ParameterRequestManager prm, ProcessorData pdata) {
        this.prm = prm;
        this.lastValueCache = prm.getLastValueCache();
        this.pdata = pdata;
    }

    /**
     * Called from the ParameterRequestManager when a new parameter has been subscribed
     * Check and subscribe any dependencies required for alarm checking
     */
    public void parameterSubscribed(Parameter p) {
        ParameterType ptype = pdata.getParameterType(p);
        if (ptype == null) {
            log.debug("Parameter {} has no type", p.getName());
            return;
        }
        Set<Parameter> params = ptype.getDependentParameters();
        prm.subscribeToProviders(params);
    }

    /**
     * Updates the iterator supplied ParameterValues with monitoring (out of limits)
     * information.
     * <p>
     * The method is called once before the algorithms are run and once after the algorithms to also check
     * the new values.
     */
    public void performAlarmChecking(ParameterValueList currentDelivery, Iterator<ParameterValue> it) {
        while (it.hasNext()) {
            ParameterValue pval = it.next();
            ParameterType ptype = pdata.getParameterType(pval.getParameter());
            if (ptype != null && ptype.hasAlarm()) {
                performAlarmChecking(currentDelivery, pval, ptype);
            } else if (pval.getMonitoringResult() != null) {
                // monitoring result set already - either processed parameters or some service like the
                // TimeCorrelationService
                if (alarmServer != null) {
                    alarmServer.update(pval, 1, false, false);
                }

            } // else do not set the MonitoringResult
        }
    }

    public void enableReporting(AlarmReporter reporter) {
        this.alarmReporter = reporter;
    }

    public void enableServer(AlarmServer<Parameter, ParameterValue> server) {
        this.alarmServer = server;
    }

    /**
     * Updates the ParameterValue with monitoring (out of limits) information
     */
    private void performAlarmChecking(ParameterValueList currentDelivery, ParameterValue pv, ParameterType ptype) {
        if (ptype instanceof FloatParameterType) {
            performAlarmCheckingFloat(currentDelivery, (FloatParameterType) ptype, pv);
        } else if (ptype instanceof EnumeratedParameterType) {
            performAlarmCheckingEnumerated(currentDelivery, (EnumeratedParameterType) ptype, pv);
        } else if (ptype instanceof IntegerParameterType) {
            performAlarmCheckingInteger(currentDelivery, (IntegerParameterType) ptype, pv);
        }
    }

    private void performAlarmCheckingInteger(ParameterValueList currentDelivery,
            IntegerParameterType ipt, ParameterValue pv) {
        long intCalValue = 0;
        if (pv.getEngValue().getType() == Type.SINT32) {
            intCalValue = pv.getEngValue().getSint32Value();
        } else if (pv.getEngValue().getType() == Type.UINT32) {
            intCalValue = 0xFFFFFFFFL & pv.getEngValue().getUint32Value();
        } else if (pv.getEngValue().getType() == Type.SINT64) {
            intCalValue = pv.getEngValue().getSint64Value();
        } else if (pv.getEngValue().getType() == Type.UINT64) {
            intCalValue = pv.getEngValue().getUint64Value();
        } else {
            throw new IllegalStateException("Unexpected integer value");
        }

        // Determine applicable ranges based on context
        boolean mon = false;
        AlarmType alarmType = null;
        AlarmRanges staticAlarmRanges = null;
        int minViolations = 1;
        boolean autoAck = false;
        boolean latching = false;
        if (ipt.getContextAlarmList() != null) {
            for (NumericContextAlarm nca : ipt.getContextAlarmList()) {
                EvaluatorInput input = new EvaluatorInput(currentDelivery, lastValueCache);
                MatchCriteriaEvaluator evaluator = pdata.getEvaluator(nca.getContextMatch());
                if (evaluator.evaluate(input) == MatchResult.OK) {
                    mon = true;
                    alarmType = nca;
                    staticAlarmRanges = nca.getStaticAlarmRanges();
                    minViolations = nca.getMinViolations();
                    autoAck = nca.isAutoAck();
                    latching = nca.isLatching();
                    break;
                }
            }
        }

        NumericAlarm defaultAlarm = ipt.getDefaultAlarm();
        if (!mon && defaultAlarm != null) {
            alarmType = defaultAlarm;
            staticAlarmRanges = defaultAlarm.getStaticAlarmRanges();
            minViolations = defaultAlarm.getMinViolations();
            autoAck = defaultAlarm.isAutoAck();
            latching = defaultAlarm.isLatching();
        }

        // Set MonitoringResult
        pv.setMonitoringResult(null); // The default is DISABLED, but set it to null, so that below code is more
                                      // readable
        if (staticAlarmRanges != null) {
            checkStaticAlarmRanges(pv, intCalValue, staticAlarmRanges);
        }

        // Notify when severity changes
        if (alarmReporter != null) {
            alarmReporter.reportNumericParameterEvent(pv, alarmType, minViolations);
        }
        if (alarmServer != null) {
            alarmServer.update(pv, minViolations, autoAck, latching);
        }
    }

    private void performAlarmCheckingFloat(ParameterValueList currentDelivery,
            FloatParameterType fpt, ParameterValue pv) {
        double doubleCalValue = 0;
        if (pv.getEngValue().getType() == Type.FLOAT) {
            doubleCalValue = pv.getEngValue().getFloatValue();
        } else if (pv.getEngValue().getType() == Type.DOUBLE) {
            doubleCalValue = pv.getEngValue().getDoubleValue();
        } else {
            throw new IllegalStateException("Unexpected float value");
        }

        // Determine applicable AlarmType based on context
        boolean mon = false;
        AlarmType alarmType = null;
        AlarmRanges staticAlarmRanges = null;

        int minViolations = 1;
        boolean autoAck = false;
        boolean latching = false;
        if (fpt.getContextAlarmList() != null) {
            EvaluatorInput input = new EvaluatorInput(currentDelivery, lastValueCache);
            for (NumericContextAlarm nca : fpt.getContextAlarmList()) {
                MatchCriteriaEvaluator evaluator = pdata.getEvaluator(nca.getContextMatch());
                if (evaluator.evaluate(input) == MatchResult.OK) {
                    mon = true;
                    alarmType = nca;
                    staticAlarmRanges = nca.getStaticAlarmRanges();
                    minViolations = nca.getMinViolations();
                    autoAck = nca.isAutoAck();
                    latching = nca.isLatching();
                    break;
                }
            }
        }

        NumericAlarm defaultAlarm = fpt.getDefaultAlarm();
        if (!mon && defaultAlarm != null) {
            alarmType = defaultAlarm;
            staticAlarmRanges = defaultAlarm.getStaticAlarmRanges();
            minViolations = defaultAlarm.getMinViolations();
            autoAck = defaultAlarm.isAutoAck();
            latching = defaultAlarm.isLatching();
        }

        // Set MonitoringResult
        pv.setMonitoringResult(null); // The default is DISABLED, but set it to null, so that below code is more
                                      // readable
        if (staticAlarmRanges != null) {
            checkStaticAlarmRanges(pv, doubleCalValue, staticAlarmRanges);
        }

        // Notify when severity changes
        if (alarmReporter != null) {
            alarmReporter.reportNumericParameterEvent(pv, alarmType, minViolations);
        }
        if (alarmServer != null) {
            alarmServer.update(pv, minViolations, autoAck, latching);
        }
    }

    private void checkRange(ParameterValue pv, MonitoringResult mr, DoubleRange fr, double v) {
        int x = fr.inRange(v);
        if (x < 0) {
            pv.setMonitoringResult(mr);
            pv.setRangeCondition(RangeCondition.LOW);
        } else if (x > 0) {
            pv.setMonitoringResult(mr);
            pv.setRangeCondition(RangeCondition.HIGH);
        }
    }

    /**
     * Verify limits, giving priority to highest severity
     */
    private void checkStaticAlarmRanges(ParameterValue pv, double doubleCalValue, AlarmRanges staticAlarmRanges) {
        pv.setMonitoringResult(null);
        DoubleRange watchRange = staticAlarmRanges.getWatchRange();
        DoubleRange warningRange = staticAlarmRanges.getWarningRange();
        DoubleRange distressRange = staticAlarmRanges.getDistressRange();
        DoubleRange criticalRange = staticAlarmRanges.getCriticalRange();
        DoubleRange severeRange = staticAlarmRanges.getSevereRange();
        if (severeRange != null) {
            checkRange(pv, MonitoringResult.SEVERE, severeRange, doubleCalValue);
        }
        if (pv.getMonitoringResult() == null && criticalRange != null) {
            checkRange(pv, MonitoringResult.CRITICAL, criticalRange, doubleCalValue);
        }
        if (pv.getMonitoringResult() == null && distressRange != null) {
            checkRange(pv, MonitoringResult.DISTRESS, distressRange, doubleCalValue);
        }
        if (pv.getMonitoringResult() == null && warningRange != null) {
            checkRange(pv, MonitoringResult.WARNING, warningRange, doubleCalValue);
        }
        if (pv.getMonitoringResult() == null && watchRange != null) {
            checkRange(pv, MonitoringResult.WATCH, watchRange, doubleCalValue);
        }

        if (pv.getMonitoringResult() == null) {
            pv.setMonitoringResult(MonitoringResult.IN_LIMITS);
        }

        pv.setWatchRange(watchRange);
        pv.setWarningRange(warningRange);
        pv.setDistressRange(distressRange);
        pv.setCriticalRange(criticalRange);
        pv.setSevereRange(severeRange);
    }

    private void performAlarmCheckingEnumerated(ParameterValueList currentDelivery,
            EnumeratedParameterType ept, ParameterValue pv) {
        pv.setMonitoringResult(null); // Default is DISABLED, but that doesn't seem fit when we are checking
        String s = pv.getEngValue().getStringValue();

        EnumerationAlarm alarm = ept.getDefaultAlarm();
        int minViolations = (alarm == null) ? 1 : alarm.getMinViolations();
        if (ept.getContextAlarmList() != null) {
            EvaluatorInput input = new EvaluatorInput(currentDelivery, lastValueCache);
            for (EnumerationContextAlarm nca : ept.getContextAlarmList()) {
                MatchCriteriaEvaluator evaluator = pdata.getEvaluator(nca.getContextMatch());
                if (evaluator.evaluate(input) == MatchResult.OK) {
                    alarm = nca;
                    minViolations = nca.getMinViolations();
                    break;
                }
            }
        }
        boolean autoAck = false;
        boolean latching = false;

        if (alarm != null) {
            AlarmLevels level = alarm.getDefaultAlarmLevel();
            for (EnumerationAlarmItem eai : alarm.getAlarmList()) {
                if (eai.getEnumerationLabel().equals(s)) {
                    level = eai.getAlarmLevel();
                }
            }

            switch (level) {
            case normal:
                pv.setMonitoringResult(MonitoringResult.IN_LIMITS);
                break;
            case watch:
                pv.setMonitoringResult(MonitoringResult.WATCH);
                break;
            case warning:
                pv.setMonitoringResult(MonitoringResult.WARNING);
                break;
            case distress:
                pv.setMonitoringResult(MonitoringResult.DISTRESS);
                break;
            case critical:
                pv.setMonitoringResult(MonitoringResult.CRITICAL);
                break;
            case severe:
                pv.setMonitoringResult(MonitoringResult.SEVERE);
                break;
            }

            if (alarmReporter != null) {
                alarmReporter.reportEnumeratedParameterEvent(pv, alarm, minViolations);
            }
            autoAck = alarm.isAutoAck();
            latching = alarm.isLatching();
        }

        if (alarmServer != null) {
            alarmServer.update(pv, minViolations, autoAck, latching);
        }
    }
}
