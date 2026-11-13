package org.yamcs.activities.triggers;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.yamcs.Processor;
import org.yamcs.alarms.AbstractAlarmServer;
import org.yamcs.alarms.ActiveAlarm;
import org.yamcs.alarms.AlarmListener;
import org.yamcs.alarms.AlarmNotificationType;
import org.yamcs.http.api.AlarmsApi;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.AlarmSeverity;
import org.yamcs.protobuf.activities.AlarmCondition;
import org.yamcs.protobuf.activities.AlarmCondition.AlarmType;
import org.yamcs.yarch.protobuf.Db;

/**
 * Reports parameter or event alarm notifications that match an {@link AlarmCondition}.
 */
class AlarmTriggerMonitor implements TriggerMonitor {

    private final TriggerService service;
    private final TriggerService.TriggerState state;
    private final Processor processor;
    private final AlarmCondition condition;

    private final List<Pattern> subjectPatterns = new ArrayList<>();
    private final Set<AlarmNotificationType> notifications = EnumSet.noneOf(AlarmNotificationType.class);

    private AbstractAlarmServer<?, ParameterValue> parameterAlarmServer;
    private AbstractAlarmServer<?, Db.Event> eventAlarmServer;
    private ParameterAlarmListener parameterListener;
    private EventAlarmListener eventListener;

    AlarmTriggerMonitor(TriggerService service, TriggerService.TriggerState state, Processor processor) {
        this.service = service;
        this.state = state;
        this.processor = processor;
        this.condition = state.getDefinition().getAlarmCondition();

        for (var subject : condition.getSubjectsList()) {
            subjectPatterns.add(globToPattern(subject));
        }
        for (var notification : condition.getNotificationsList()) {
            try {
                notifications.add(AlarmNotificationType.valueOf(notification.name()));
            } catch (IllegalArgumentException e) {
                // e.g. ACTIVE, which is not an actual transition
            }
        }
    }

    @Override
    public void start() {
        if (condition.getAlarmType() == AlarmType.EVENT) {
            eventAlarmServer = processor.getEventAlarmServer();
            if (eventAlarmServer == null) {
                throw new IllegalStateException(
                        "Processor '" + processor.getName() + "' has no event alarm server");
            }
            eventListener = new EventAlarmListener();
            eventAlarmServer.addAlarmListener(eventListener);
        } else {
            if (!processor.hasAlarmServer()) {
                throw new IllegalStateException(
                        "Processor '" + processor.getName() + "' has no parameter alarm server");
            }
            parameterAlarmServer = processor.getParameterProcessorManager().getAlarmServer();
            parameterListener = new ParameterAlarmListener();
            parameterAlarmServer.addAlarmListener(parameterListener);
        }
    }

    @Override
    public void stop() {
        if (parameterAlarmServer != null) {
            parameterAlarmServer.removeAlarmListener(parameterListener);
            parameterAlarmServer = null;
        }
        if (eventAlarmServer != null) {
            eventAlarmServer.removeAlarmListener(eventListener);
            eventAlarmServer = null;
        }
    }

    private boolean matches(AlarmNotificationType notificationType, ActiveAlarm<?> alarm, String subject,
            AlarmSeverity severity) {
        if (!notifications.contains(notificationType)) {
            return false;
        }
        if (alarm.isShelved() && !condition.getIncludeShelved()) {
            return false;
        }
        if (severity == null || severity.getNumber() < condition.getMinSeverity().getNumber()) {
            return false;
        }
        if (subjectPatterns.isEmpty()) {
            return true;
        }
        for (var pattern : subjectPatterns) {
            if (pattern.matcher(subject).matches()) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> baseContext(AlarmNotificationType notificationType, ActiveAlarm<?> alarm,
            String subject, AlarmSeverity severity) {
        var context = new LinkedHashMap<String, Object>();
        context.put("alarm.type", condition.getAlarmType().name());
        context.put("alarm.subject", subject);
        context.put("alarm.notification", notificationType.name());
        context.put("alarm.severity", severity.name());
        context.put("alarm.seqNum", alarm.getId());
        context.put("processor", processor.getName());
        return context;
    }

    private void onParameterAlarm(AlarmNotificationType notificationType, ActiveAlarm<ParameterValue> alarm) {
        var pv = alarm.getMostSevereValue();
        if (pv == null || pv.getMonitoringResult() == null) {
            return;
        }
        var subject = pv.getParameter().getQualifiedName();
        var severity = AlarmsApi.getParameterAlarmSeverity(pv.getMonitoringResult());
        if (!matches(notificationType, alarm, subject, severity)) {
            return;
        }

        var context = baseContext(notificationType, alarm, subject, severity);
        var current = alarm.getCurrentValue() != null ? alarm.getCurrentValue() : pv;
        if (current.getEngValue() != null) {
            var value = TriggerService.toContextValue(current.getEngValue());
            context.put("value", value);
            context.put(ArgumentTemplates.PARAM_PREFIX + subject, value);
        }
        service.onConditionMet(state, context);
    }

    private void onEventAlarm(AlarmNotificationType notificationType, ActiveAlarm<Db.Event> alarm) {
        var event = alarm.getMostSevereValue();
        if (event == null) {
            return;
        }
        var subject = event.hasType() ? event.getSource() + "/" + event.getType() : event.getSource();
        var severity = AlarmsApi.getEventAlarmSeverity(event.getSeverity());
        if (!matches(notificationType, alarm, subject, severity)) {
            return;
        }

        var current = alarm.getCurrentValue() != null ? alarm.getCurrentValue() : event;
        var context = baseContext(notificationType, alarm, subject, severity);
        context.put("value", current.getMessage());
        context.put("event.source", current.getSource());
        context.put("event.type", current.hasType() ? current.getType() : "");
        context.put("event.message", current.getMessage());
        context.put("event.severity", current.getSeverity().name());
        service.onConditionMet(state, context);
    }

    /**
     * Converts a glob with {@code *} and {@code ?} wildcards to a regular expression.
     */
    static Pattern globToPattern(String glob) {
        var sb = new StringBuilder();
        var literal = new StringBuilder();
        for (char c : glob.toCharArray()) {
            if (c == '*' || c == '?') {
                if (literal.length() > 0) {
                    sb.append(Pattern.quote(literal.toString()));
                    literal.setLength(0);
                }
                sb.append(c == '*' ? ".*" : ".");
            } else {
                literal.append(c);
            }
        }
        if (literal.length() > 0) {
            sb.append(Pattern.quote(literal.toString()));
        }
        return Pattern.compile(sb.toString());
    }

    private class ParameterAlarmListener implements AlarmListener<ParameterValue> {

        @Override
        public void notifyUpdate(AlarmNotificationType notificationType, ActiveAlarm<ParameterValue> activeAlarm) {
            onParameterAlarm(notificationType, activeAlarm);
        }

        @Override
        public void notifySeverityIncrease(ActiveAlarm<ParameterValue> activeAlarm) {
            onParameterAlarm(AlarmNotificationType.SEVERITY_INCREASED, activeAlarm);
        }

        @Override
        public void notifyValueUpdate(ActiveAlarm<ParameterValue> activeAlarm) {
            onParameterAlarm(AlarmNotificationType.VALUE_UPDATED, activeAlarm);
        }
    }

    private class EventAlarmListener implements AlarmListener<Db.Event> {

        @Override
        public void notifyUpdate(AlarmNotificationType notificationType, ActiveAlarm<Db.Event> activeAlarm) {
            onEventAlarm(notificationType, activeAlarm);
        }

        @Override
        public void notifySeverityIncrease(ActiveAlarm<Db.Event> activeAlarm) {
            onEventAlarm(AlarmNotificationType.SEVERITY_INCREASED, activeAlarm);
        }

        @Override
        public void notifyValueUpdate(ActiveAlarm<Db.Event> activeAlarm) {
            onEventAlarm(AlarmNotificationType.VALUE_UPDATED, activeAlarm);
        }
    }
}
