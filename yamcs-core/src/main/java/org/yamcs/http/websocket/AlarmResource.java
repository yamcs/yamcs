package org.yamcs.http.websocket;

import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.alarms.ActiveAlarm;
import org.yamcs.alarms.AlarmListener;
import org.yamcs.alarms.AlarmServer;
import org.yamcs.alarms.EventAlarmServer;
import org.yamcs.http.api.AlarmsApi;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.AlarmData;
import org.yamcs.protobuf.AlarmNotificationType;
import org.yamcs.protobuf.AlarmSubscriptionRequest;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.xtce.Parameter;

/**
 * Provides realtime alarm subscription via web.
 */
public class AlarmResource implements WebSocketResource {

    private ConnectedWebSocketClient client;

    private volatile boolean subscribed = false;

    private AlarmServer<Parameter, ParameterValue> parameterAlarmServer;
    private EventAlarmServer eventAlarmServer;

    MyAlarmListener<ParameterValue> plistener = new MyAlarmListener<>();
    MyAlarmListener<Event> elistener = new MyAlarmListener<>();
    private boolean sendDetail;

    public AlarmResource(ConnectedWebSocketClient client) {
        this.client = client;
        Processor processor = client.getProcessor();
        if (processor != null && processor.hasAlarmServer()) {
            parameterAlarmServer = processor.getParameterRequestManager().getAlarmServer();
            eventAlarmServer = processor.getEventAlarmServer();
        }
    }

    @Override
    public String getName() {
        return "alarms";
    }

    @Override
    public WebSocketReply subscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {
        sendDetail = false;
        if (ctx.getData() != null) {
            AlarmSubscriptionRequest req = decoder.decodeMessageData(ctx, AlarmSubscriptionRequest.newBuilder())
                    .build();
            if (req.hasDetail()) {
                sendDetail = req.getDetail();
            }
        }

        client.sendReply(WebSocketReply.ack(ctx.getRequestId()));
        subscribed = true;
        applySubscription();
        return null;
    }

    @Override
    public WebSocketReply unsubscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        if (parameterAlarmServer != null) {
            parameterAlarmServer.removeAlarmListener(plistener);
        }
        if (eventAlarmServer != null) {
            eventAlarmServer.removeAlarmListener(elistener);
        }

        subscribed = false;
        return WebSocketReply.ack(ctx.getRequestId());
    }

    @Override
    public void socketClosed() {
        if (parameterAlarmServer != null) {
            parameterAlarmServer.removeAlarmListener(plistener);
        }
        if (eventAlarmServer != null) {
            eventAlarmServer.removeAlarmListener(elistener);
        }
    }

    @Override
    public void unselectProcessor() {
        if (parameterAlarmServer != null) {
            parameterAlarmServer.removeAlarmListener(plistener);
            parameterAlarmServer = null;
        }
        if (eventAlarmServer != null) {
            eventAlarmServer.removeAlarmListener(elistener);
            eventAlarmServer = null;
        }
    }

    @Override
    public void selectProcessor(Processor processor) throws ProcessorException {
        if (processor.hasAlarmServer()) {
            parameterAlarmServer = processor.getParameterRequestManager().getAlarmServer();
            eventAlarmServer = processor.getEventAlarmServer();
        }
        if (subscribed) {
            applySubscription();
        }
    }

    private void applySubscription() {
        // Every subscribe request may change a previous subscribe request
        // Therefore unregister past listeners, before maybe re-adding some of them.
        if (parameterAlarmServer != null) {
            parameterAlarmServer.removeAlarmListener(plistener);
        }
        if (eventAlarmServer != null) {
            eventAlarmServer.removeAlarmListener(elistener);
        }

        if (parameterAlarmServer != null) {
            for (ActiveAlarm<ParameterValue> activeAlarm : parameterAlarmServer.getActiveAlarms().values()) {
                sendAlarm(AlarmNotificationType.ACTIVE, activeAlarm);
            }
            parameterAlarmServer.addAlarmListener(plistener);
        }
        if (eventAlarmServer != null) {
            for (ActiveAlarm<Event> activeAlarm : eventAlarmServer.getActiveAlarms().values()) {
                sendAlarm(AlarmNotificationType.ACTIVE, activeAlarm);
            }
            eventAlarmServer.addAlarmListener(elistener);
        }
    }

    class MyAlarmListener<T> implements AlarmListener<T> {

        @Override
        public void notifySeverityIncrease(ActiveAlarm<T> activeAlarm) {
            sendAlarm(AlarmNotificationType.SEVERITY_INCREASED, activeAlarm);
        }

        @Override
        public void notifyValueUpdate(ActiveAlarm<T> activeAlarm) {
            sendAlarm(AlarmNotificationType.VALUE_UPDATED, activeAlarm);
        }

        @Override
        public void notifyUpdate(org.yamcs.alarms.AlarmNotificationType notificationType, ActiveAlarm<T> activeAlarm) {
            sendAlarm(AlarmsApi.protoNotificationType.get(notificationType), activeAlarm);

        }
    }

    private void sendAlarm(AlarmNotificationType type, ActiveAlarm<?> activeAlarm) {
        AlarmData alarmData = AlarmsApi.toAlarmData(type, activeAlarm, sendDetail);
        client.sendData(ProtoDataType.ALARM_DATA, alarmData);
    }
}
