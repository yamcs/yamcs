package org.yamcs.web.websocket;

import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.alarms.ActiveAlarm;
import org.yamcs.alarms.AlarmListener;
import org.yamcs.alarms.AlarmServer;
import org.yamcs.protobuf.Alarms.AlarmData;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.web.rest.processor.ProcessorHelper;

/**
 * Provides realtime alarm subscription via web.
 */
public class AlarmResource implements WebSocketResource, AlarmListener {

    public static final String RESOURCE_NAME = "alarms";

    private ConnectedWebSocketClient client;

    private volatile boolean subscribed = false;

    private AlarmServer alarmServer;

    public AlarmResource(ConnectedWebSocketClient client) {
        this.client = client;
        Processor processor = client.getProcessor();
        if (processor != null && processor.hasAlarmServer()) {
            alarmServer = processor.getParameterRequestManager().getAlarmServer();
        }
    }

    @Override
    public WebSocketReply subscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {
        client.sendReply(WebSocketReply.ack(ctx.getRequestId()));
        subscribed = true;
        applySubscription();
        return null;
    }

    @Override
    public WebSocketReply unsubscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        if (alarmServer != null) {
            alarmServer.unsubscribe(this);
        }
        subscribed = false;
        return WebSocketReply.ack(ctx.getRequestId());
    }

    @Override
    public void socketClosed() {
        if (alarmServer != null) {
            alarmServer.unsubscribe(this);
        }
    }

    @Override
    public void unselectProcessor() {
        if (alarmServer != null) {
            alarmServer.unsubscribe(this);
        }
        alarmServer = null;
    }

    @Override
    public void selectProcessor(Processor processor) throws ProcessorException {
        if (processor.hasAlarmServer()) {
            alarmServer = processor.getParameterRequestManager().getAlarmServer();
        }
        if (subscribed) {
            applySubscription();
        }
    }

    private void applySubscription() {
        if (alarmServer != null) {
            for (ActiveAlarm activeAlarm : alarmServer.getActiveAlarms().values()) {
                sendAlarm(AlarmData.Type.ACTIVE, activeAlarm);
            }
            alarmServer.subscribe(this);
        }
    }

    @Override
    public void notifyTriggered(ActiveAlarm activeAlarm) {
        sendAlarm(AlarmData.Type.TRIGGERED, activeAlarm);
    }

    @Override
    public void notifySeverityIncrease(ActiveAlarm activeAlarm) {
        sendAlarm(AlarmData.Type.SEVERITY_INCREASED, activeAlarm);
    }

    @Override
    public void notifyParameterValueUpdate(ActiveAlarm activeAlarm) {
        sendAlarm(AlarmData.Type.PVAL_UPDATED, activeAlarm);
    }

    @Override
    public void notifyAcknowledged(ActiveAlarm activeAlarm) {
        sendAlarm(AlarmData.Type.ACKNOWLEDGED, activeAlarm);
    }

    @Override
    public void notifyCleared(ActiveAlarm activeAlarm) {
        sendAlarm(AlarmData.Type.CLEARED, activeAlarm);
    }

    private void sendAlarm(AlarmData.Type type, ActiveAlarm activeAlarm) {
        AlarmData alarmData = ProcessorHelper.toAlarmData(type, activeAlarm);
        client.sendData(ProtoDataType.ALARM_DATA, alarmData);
    }
}
