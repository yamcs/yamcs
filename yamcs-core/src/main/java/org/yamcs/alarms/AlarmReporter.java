package org.yamcs.alarms;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.yamcs.AbstractProcessorService;
import org.yamcs.ConfigurationException;
import org.yamcs.Processor;
import org.yamcs.ProcessorService;
import org.yamcs.YConfiguration;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.parameter.ParameterProcessorManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Event.EventSeverity;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Pvalue.RangeCondition;
import org.yamcs.xtce.AlarmReportType;
import org.yamcs.xtce.AlarmType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.mdb.Mdb;

/**
 * Generates events for parameters out of limits.
 * <p>
 * This was used when Yamcs did not have the {@link AlarmServer}. It may be removed in the future.
 */
public class AlarmReporter extends AbstractProcessorService implements ProcessorService {

    private EventProducer eventProducer;
    private Map<Parameter, ActiveAlarm> activeAlarms = new HashMap<>();
    // Last value of each param (for detecting changes in value)
    private Map<Parameter, ParameterValue> lastValuePerParameter = new HashMap<>();

    @Override
    public void init(Processor processor, YConfiguration config, Object spec) {
        super.init(processor, config, spec);
        eventProducer = EventProducerFactory.getEventProducer(processor.getInstance());
        String source = config.getString("source", "AlarmChecker");
        eventProducer.setSource(source);
    }

    @Override
    public void doStart() {
        ParameterProcessorManager ppm = processor.getParameterProcessorManager();
        ppm.getAlarmChecker().enableReporting(this);

        // Auto-subscribe to parameters with alarms
        Set<Parameter> requiredParameters = new HashSet<>();
        try {
            Mdb mdb = MdbFactory.getInstance(getYamcsInstance());
            for (Parameter parameter : mdb.getParameters()) {
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
            ppm.subscribeToProviders(requiredParameters);
        }
        notifyStarted();
    }

    @Override
    public void doStop() {
        notifyStopped();
    }

    /**
     * Sends an event if an alarm condition for the active context has been triggered {@code minViolations} times. This
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

            EventSeverity severity = getEventSeverity(pv.getMonitoringResult());
            eventProducer.sendEvent(severity, null, message);
        }
    }

    private void sendStateChangeEvent(ParameterValue pv) {
        EventSeverity severity = getEventSeverity(pv.getMonitoringResult());
        eventProducer.sendEvent(severity, null, "Parameter " + pv.getParameter().getQualifiedName()
                + " transitioned to state " + pv.getEngValue().getStringValue());
    }

    EventSeverity getEventSeverity(MonitoringResult mr) {
        switch (mr) {
        case WATCH:
            return EventSeverity.WATCH;
        case WARNING:
            return EventSeverity.WARNING;
        case DISTRESS:
            return EventSeverity.DISTRESS;
        case CRITICAL:
            return EventSeverity.CRITICAL;
        case SEVERE:
            return EventSeverity.SEVERE;
        case IN_LIMITS:
            return EventSeverity.INFO;
        default:
            throw new IllegalStateException("Unexpected monitoring result: " + mr);
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
