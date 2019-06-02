package org.yamcs.web.websocket;

import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.alarms.ActiveAlarm;
import org.yamcs.alarms.AlarmListener;
import org.yamcs.alarms.AlarmServer;
import org.yamcs.alarms.EventAlarmServer;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Alarms.ParameterAlarmData;
import org.yamcs.protobuf.Alarms.AlarmNotificationType;
import org.yamcs.protobuf.Alarms.EventAlarmData;
import org.yamcs.protobuf.Web.AlarmSubscriptionRequest;
import org.yamcs.protobuf.Web.AlarmSubscriptionRequest.AlarmSubscriptionType;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.web.rest.processor.ProcessorHelper;
import org.yamcs.xtce.Parameter;

/**
 * Provides realtime alarm subscription via web.
 */
public class AlarmResource implements WebSocketResource {

    public static final String RESOURCE_NAME = "alarms";

    private ConnectedWebSocketClient client;

    private volatile boolean subscribed = false;

    private AlarmServer<Parameter, ParameterValue> parameterAlarmServer;
    private EventAlarmServer eventAlarmServer;

    MyAlarmListener<ParameterValue> plistener = new MyAlarmListener<>();
    MyAlarmListener<Event> elistener = new MyAlarmListener<>();
    private boolean subscribeParamAlarms;
    private boolean subscribeEventAlarms;
    private boolean subscribeSummaryAlarms;

    public AlarmResource(ConnectedWebSocketClient client) {
        this.client = client;
        Processor processor = client.getProcessor();
        if (processor != null && processor.hasAlarmServer()) {
            parameterAlarmServer = processor.getParameterRequestManager().getAlarmServer();
            eventAlarmServer = processor.getEventAlarmServer();
        }
    }

    @Override
    public WebSocketReply subscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {
        AlarmSubscriptionRequest.AlarmSubscriptionType type = AlarmSubscriptionType.SUMMARY;
        if (ctx.getData() != null) {
            AlarmSubscriptionRequest req = decoder.decodeMessageData(ctx, AlarmSubscriptionRequest.newBuilder())
                    .build();
            if (req.hasType()) {
                type = req.getType();
            }
        }
        if (type == AlarmSubscriptionType.PARAMETER) {
            subscribeParamAlarms = true;
        } else if (type == AlarmSubscriptionType.EVENT) {
            subscribeEventAlarms = true;
        } else {
            subscribeSummaryAlarms = true;
        }

        client.sendReply(WebSocketReply.ack(ctx.getRequestId()));
        subscribed = true;
        applySubscription();
        return null;
    }

    @Override
    public WebSocketReply unsubscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        AlarmSubscriptionRequest.AlarmSubscriptionType type = AlarmSubscriptionType.PARAMETER;
        if (ctx.getData() != null) {
            AlarmSubscriptionRequest req = decoder.decodeMessageData(ctx, AlarmSubscriptionRequest.newBuilder())
                    .build();
            if (req.hasType()) {
                type = req.getType();
            }
        }
        if (type == AlarmSubscriptionType.PARAMETER) {
            subscribeParamAlarms = false;
            if (parameterAlarmServer != null) {
                parameterAlarmServer.unsubscribeAlarm(plistener);
            }
        } else {
            subscribeEventAlarms = false;
            if (eventAlarmServer != null) {
                eventAlarmServer.unsubscribeAlarm(elistener);
            }
        }

        subscribed = false;
        return WebSocketReply.ack(ctx.getRequestId());
    }

    @Override
    public void socketClosed() {
        if (parameterAlarmServer != null) {
            parameterAlarmServer.unsubscribeAlarm(plistener);
        }
    }

    @Override
    public void unselectProcessor() {
        if (parameterAlarmServer != null) {
            parameterAlarmServer.unsubscribeAlarm(plistener);
        }
        parameterAlarmServer = null;
    }

    @Override
    public void selectProcessor(Processor processor) throws ProcessorException {
        if (processor.hasAlarmServer()) {
            parameterAlarmServer = processor.getParameterRequestManager().getAlarmServer();
        }
        if (subscribed) {
            applySubscription();
        }
    }

    private void applySubscription() {
        if ((subscribeParamAlarms || subscribeSummaryAlarms) && parameterAlarmServer != null) {
            for (ActiveAlarm<ParameterValue> activeAlarm : parameterAlarmServer.getActiveAlarms().values()) {
                sendAlarm(AlarmNotificationType.ACTIVE, activeAlarm);
            }
            parameterAlarmServer.subscribeAlarm(plistener);
        }
        if ((subscribeEventAlarms || subscribeSummaryAlarms) && eventAlarmServer != null) {
            for (ActiveAlarm<Event> activeAlarm : eventAlarmServer.getActiveAlarms().values()) {
                sendAlarm(AlarmNotificationType.ACTIVE, activeAlarm);
            }
            eventAlarmServer.subscribeAlarm(elistener);
        }
    }

    class MyAlarmListener<T> implements AlarmListener<T> {
        @Override
        public void notifyTriggered(ActiveAlarm<T> activeAlarm) {
            sendAlarm(AlarmNotificationType.TRIGGERED, activeAlarm);
        }

        @Override
        public void notifySeverityIncrease(ActiveAlarm<T> activeAlarm) {
            sendAlarm(AlarmNotificationType.SEVERITY_INCREASED, activeAlarm);
        }

        @Override
        public void notifyValueUpdate(ActiveAlarm<T> activeAlarm) {
            sendAlarm(AlarmNotificationType.UPDATED, activeAlarm);
        }

        @Override
        public void notifyAcknowledged(ActiveAlarm<T> activeAlarm) {
            sendAlarm(AlarmNotificationType.ACKNOWLEDGED, activeAlarm);
        }

        @Override
        public void notifyCleared(ActiveAlarm<T> activeAlarm) {
            sendAlarm(AlarmNotificationType.CLEARED, activeAlarm);
        }
    }

    private void sendAlarm(AlarmNotificationType type, ActiveAlarm<?> activeAlarm) {
        if (subscribeParamAlarms && activeAlarm.triggerValue instanceof ParameterValue) {
            client.sendData(ProtoDataType.PARAMETER_ALARM_DATA, ProcessorHelper.toParameterAlarmData(type,
                    (ActiveAlarm<ParameterValue>) activeAlarm));
        }
        
        if (subscribeEventAlarms && activeAlarm.triggerValue instanceof Event) {
                client.sendData(ProtoDataType.EVENT_ALARM_DATA, ProcessorHelper.toEventAlarmData(type, (ActiveAlarm<Event>) activeAlarm));
        }
        if(subscribeSummaryAlarms) {
            client.sendData(ProtoDataType.ALARM_DATA, ProcessorHelper.toSummaryAlarmData(type, activeAlarm));
        }
    }
}
