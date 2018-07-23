package org.yamcs.web.websocket;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class AlarmResource extends AbstractWebSocketResource implements AlarmListener {
    private static final Logger log = LoggerFactory.getLogger(AlarmResource.class);
    public static final String RESOURCE_NAME = "alarms";
    private volatile boolean subscribed = false;

    public AlarmResource(WebSocketClient client) {
        super(client);
    }

    @Override
    public WebSocketReply processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {
        switch (ctx.getOperation()) {
        case "subscribe":
            return subscribe(ctx.getRequestId());
        case "unsubscribe":
            return unsubscribe(ctx.getRequestId());
        default:
            throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '" + ctx.getOperation() + "'");
        }
    }

    private WebSocketReply subscribe(int requestId) throws WebSocketException {
        try {
            wsHandler.sendReply(WebSocketReply.ack(requestId));
            doSubscribe();
            return null;
        } catch (IOException e) {
            log.error("Exception when sending data", e);
            return null;
        }
    }

    private WebSocketReply unsubscribe(int requestId) throws WebSocketException {
        doUnsubscribe();
        return WebSocketReply.ack(requestId);
    }

    @Override
    public void quit() {
        doUnsubscribe();
    }

    @Override
    public void switchProcessor(Processor oldProcessor, Processor newProcessor) throws ProcessorException {
        if (subscribed) {
            doUnsubscribe();
            super.switchProcessor(oldProcessor, newProcessor);
            doSubscribe();
        } else {
            super.switchProcessor(oldProcessor, newProcessor);

        }
    }

    private void doSubscribe() {
        subscribed = true;
        if (processor.hasAlarmServer()) {
            AlarmServer alarmServer = processor.getParameterRequestManager().getAlarmServer();
            for (ActiveAlarm activeAlarm : alarmServer.getActiveAlarms().values()) {
                sendAlarm(AlarmData.Type.ACTIVE, activeAlarm);
            }
            alarmServer.subscribe(this);
        }
    }

    private void doUnsubscribe() {
        if (processor.hasAlarmServer()) {
            AlarmServer alarmServer = processor.getParameterRequestManager().getAlarmServer();
            alarmServer.unsubscribe(this);
        }
        subscribed = false;
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

        try {
            wsHandler.sendData(ProtoDataType.ALARM_DATA, alarmData);
        } catch (Exception e) {
            log.warn("Got error when sending alarm, quitting", e);
            quit();
        }
    }
}
