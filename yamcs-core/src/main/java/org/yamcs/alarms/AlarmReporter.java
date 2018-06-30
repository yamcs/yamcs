package org.yamcs.alarms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.Processor;
import org.yamcs.ProcessorService;
import org.yamcs.YConfiguration;
import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.RangeCondition;
import org.yamcs.xtce.AlarmReportType;
import org.yamcs.xtce.AlarmType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.common.util.concurrent.AbstractService;

/**
 * Generates alarm events for a processor, by subscribing to all relevant parameters.
 */
public class AlarmReporter extends AbstractService implements ParameterConsumer, ProcessorService {

    private static final Logger log = LoggerFactory.getLogger(AlarmReporter.class);

    private String yamcsInstance;
    private Processor processor;

    private String source;

    private EventProducer eventProducer;
    private Map<Parameter, ActiveAlarm> activeAlarms = new HashMap<>();
    // Last value of each param (for detecting changes in value)
    private Map<Parameter, ParameterValue> lastValuePerParameter = new HashMap<>();

    public AlarmReporter(String yamcsInstance) {
        this(yamcsInstance, Collections.emptyMap());
    }

    public AlarmReporter(String yamcsInstance, Map<String, Object> config) {
        this.yamcsInstance = yamcsInstance;
        source = YConfiguration.getString(config, "source", "AlarmChecker");
    }

    @Override
    public void init(Processor processor) {
        this.processor = processor;
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance);
        eventProducer.setSource(source);
    }

    @Override
    public void doStart() {
        if (processor == null) {
            log.warn("DEPRECATION: Define AlarmReporter as a processor service in processor.yaml"
                    + " rather than an instance service in yamcs.(instance).yaml");
            init(Processor.getFirstProcessor(yamcsInstance));
        }

        ParameterRequestManager prm = processor.getParameterRequestManager();
        prm.getAlarmChecker().enableReporting(this);

        // Auto-subscribe to parameters with alarms
        Set<Parameter> requiredParameters = new HashSet<>();
        try {
            XtceDb xtcedb = XtceDbFactory.getInstance(yamcsInstance);
            for (Parameter parameter : xtcedb.getParameters()) {
                ParameterType ptype = parameter.getParameterType();
                if (ptype != null && ptype.hasAlarm()) {
                    requiredParameters.add(parameter);
                    requiredParameters.addAll(ptype.getDependentParameters());
                }
            }
        } catch (ConfigurationException e) {
            notifyFailed(e);
            return;
        }

        if (!requiredParameters.isEmpty()) {
            List<Parameter> params = new ArrayList<>(requiredParameters); // Now that we have uniques..
            prm.addRequest(params, this);
        }
        notifyStarted();
    }

    @Override
    public void doStop() {
        notifyStopped();
    }

    @Override
    public void updateItems(int subscriptionId, List<ParameterValue> items) {
        // Nothing. The real business of sending events, happens while checking the alarms
        // because that's where we have easy access to the XTCE definition of the active
        // alarm. The PRM is only used to signal the parameter subscriptions.
    }

    /**
     * Sends an event if an alarm condition for the active context has been triggered <tt>minViolations</tt> times. This
     * configuration does not affect events for parameters that go back to normal, or that change severity levels while
     * the alarm is already active.
     */
    public void reportNumericParameterEvent(ParameterValue pv, AlarmType alarmType, int minViolations) {
        boolean sendUpdateEvent = false;

        if (alarmType == null) {
            // TODO: do something with more interesting
            return;
        }

        if (alarmType.getAlarmReportType() == AlarmReportType.ON_VALUE_CHANGE) {
            ParameterValue oldPv = lastValuePerParameter.get(pv.getParameter());
            if (oldPv != null && hasChanged(oldPv, pv)) {
                sendUpdateEvent = true;
            }
            lastValuePerParameter.put(pv.getParameter(), pv);
        }

        if (pv.getMonitoringResult() == MonitoringResult.IN_LIMITS) {
            if (activeAlarms.containsKey(pv.getParameter())) {
                eventProducer.sendInfo(null,
                        "Parameter " + pv.getParameter().getQualifiedName() + " is back to normal");
                activeAlarms.remove(pv.getParameter());
            }
        } else { // out of limits
            MonitoringResult previousMonitoringResult = null;
            ActiveAlarm activeAlarm = activeAlarms.get(pv.getParameter());
            if (activeAlarm == null || activeAlarm.alarmType != alarmType) {
                activeAlarm = new ActiveAlarm(alarmType, pv.getMonitoringResult());
            } else {
                previousMonitoringResult = activeAlarm.monitoringResult;
                activeAlarm.monitoringResult = pv.getMonitoringResult();
                activeAlarm.violations++;
            }

            if (activeAlarm.violations == minViolations || (activeAlarm.violations > minViolations
                    && previousMonitoringResult != activeAlarm.monitoringResult)) {
                sendUpdateEvent = true;
            }

            activeAlarms.put(pv.getParameter(), activeAlarm);
        }

        if (sendUpdateEvent) {
            sendValueChangeEvent(pv);
        }
    }

    public void reportEnumeratedParameterEvent(ParameterValue pv, AlarmType alarmType, int minViolations) {
        boolean sendUpdateEvent = false;

        if (alarmType == null) {
            // TODO: something more interesting
            return;
        }

        if (alarmType.getAlarmReportType() == AlarmReportType.ON_VALUE_CHANGE) {
            ParameterValue oldPv = lastValuePerParameter.get(pv.getParameter());
            if (oldPv != null && hasChanged(oldPv, pv)) {
                sendUpdateEvent = true;
            }
            lastValuePerParameter.put(pv.getParameter(), pv);
        }

        if (pv.getMonitoringResult() == MonitoringResult.IN_LIMITS) {
            if (activeAlarms.containsKey(pv.getParameter())) {
                eventProducer.sendInfo(null, "Parameter " + pv.getParameter().getQualifiedName()
                        + " is back to a normal state (" + pv.getEngValue().getStringValue() + ")");
                activeAlarms.remove(pv.getParameter());
            }
        } else { // out of limits
            MonitoringResult previousMonitoringResult = null;
            ActiveAlarm activeAlarm = activeAlarms.get(pv.getParameter());
            if (activeAlarm == null || activeAlarm.alarmType != alarmType) {
                activeAlarm = new ActiveAlarm(alarmType, pv.getMonitoringResult());
            } else {
                previousMonitoringResult = activeAlarm.monitoringResult;
                activeAlarm.monitoringResult = pv.getMonitoringResult();
                activeAlarm.violations++;
            }

            if (activeAlarm.violations == minViolations || (activeAlarm.violations > minViolations
                    && previousMonitoringResult != activeAlarm.monitoringResult)) {
                sendUpdateEvent = true;
            }

            activeAlarms.put(pv.getParameter(), activeAlarm);
        }

        if (sendUpdateEvent) {
            sendStateChangeEvent(pv);
        }
    }

    private void sendValueChangeEvent(ParameterValue pv) {
        if (pv.getMonitoringResult() == null) {
            eventProducer.sendInfo(null,
                    "Parameter " + pv.getParameter().getQualifiedName() + " has changed to value " + pv.getEngValue());
            return;
        }

        if (pv.getMonitoringResult() == MonitoringResult.IN_LIMITS) {
            eventProducer.sendInfo(null,
                    "Parameter " + pv.getParameter().getQualifiedName() + " has changed to value " + pv.getEngValue());
        } else {
            String message;
            if (pv.getRangeCondition() == RangeCondition.LOW) {
                message = "Parameter " + pv.getParameter().getQualifiedName() + " is too low";
            } else if (pv.getRangeCondition() == RangeCondition.HIGH) {
                message = "Parameter " + pv.getParameter().getQualifiedName() + " is too high";
            } else {
                throw new IllegalStateException("Unexpected range condition: " + pv.getRangeCondition());
            }

            switch (pv.getMonitoringResult()) {
            case WATCH:
                eventProducer.sendWatch(null, message);
                break;
            case WARNING:
                eventProducer.sendWarning(null, message);
                break;
            case DISTRESS:
                eventProducer.sendDistress(null, message);
                break;
            case CRITICAL:
                eventProducer.sendCritical(null, message);
                break;
            case SEVERE:
                eventProducer.sendSevere(null, message);
                break;
            default:
                throw new IllegalStateException("Unexpected monitoring result: " + pv.getMonitoringResult());
            }
        }
    }

    private void sendStateChangeEvent(ParameterValue pv) {
        switch (pv.getMonitoringResult()) {
        case WATCH:
            eventProducer.sendWatch(null, "Parameter " + pv.getParameter().getQualifiedName()
                    + " transitioned to state " + pv.getEngValue().getStringValue());
            break;
        case WARNING:
            eventProducer.sendWarning(null, "Parameter " + pv.getParameter().getQualifiedName()
                    + " transitioned to state " + pv.getEngValue().getStringValue());
            break;
        case DISTRESS:
            eventProducer.sendDistress(null, "Parameter " + pv.getParameter().getQualifiedName()
                    + " transitioned to state " + pv.getEngValue().getStringValue());
            break;
        case CRITICAL:
            eventProducer.sendCritical(null, "Parameter " + pv.getParameter().getQualifiedName()
                    + " transitioned to state " + pv.getEngValue().getStringValue());
            break;
        case SEVERE:
            eventProducer.sendSevere(null, "Parameter " + pv.getParameter().getQualifiedName()
                    + " transitioned to state " + pv.getEngValue().getStringValue());
            break;
        case IN_LIMITS:
            eventProducer.sendInfo(null, "Parameter " + pv.getParameter().getQualifiedName() + " transitioned to state "
                    + pv.getEngValue().getStringValue());
            break;
        default:
            throw new IllegalStateException("Unexpected monitoring result: " + pv.getMonitoringResult());
        }
    }

    private boolean hasChanged(ParameterValue pvOld, ParameterValue pvNew) {
        // Crude string value comparison.
        return !pvOld.getEngValue().equals(pvNew.getEngValue());
    }

    private static class ActiveAlarm {
        MonitoringResult monitoringResult;
        AlarmType alarmType;
        int violations = 1;

        ActiveAlarm(AlarmType alarmType, MonitoringResult monitoringResult) {
            this.alarmType = alarmType;
            this.monitoringResult = monitoringResult;
        }
    }
}
