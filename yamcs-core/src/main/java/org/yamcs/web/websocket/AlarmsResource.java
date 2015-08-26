package org.yamcs.web.websocket;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.YProcessorException;
import org.yamcs.alarms.ActiveAlarm;
import org.yamcs.alarms.AlarmListener;
import org.yamcs.alarms.AlarmServer;
import org.yamcs.protobuf.Alarms.AcknowledgeInfo;
import org.yamcs.protobuf.Alarms.Alarm;
import org.yamcs.protobuf.SchemaAlarms;
import org.yamcs.protobuf.Websocket.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.utils.TimeEncoding;

/**
 * Provides realtime alarm subscription via web.
 */
public class AlarmsResource extends AbstractWebSocketResource implements AlarmListener {
    Logger log;

    public AlarmsResource(YProcessor channel, WebSocketServerHandler wsHandler) {
        super(channel, wsHandler);
        log = LoggerFactory.getLogger(AlarmsResource.class.getName() + "[" + channel.getInstance() + "]");
        wsHandler.addResource("alarms", this);
    }

    @Override
    public WebSocketReplyData processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder, AuthenticationToken authenticationToken) throws WebSocketException {
        switch (ctx.getOperation()) {
        case "subscribe":
            return subscribe(ctx.getRequestId());
        case "unsubscribe":
            return unsubscribe(ctx.getRequestId());
        default:
            throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '"+ctx.getOperation()+"'");
        }
    }

    private WebSocketReplyData subscribe(int requestId) throws WebSocketException {
        if (!processor.hasAlarmServer()) {
            throw new WebSocketException(requestId, "Alarms are not enabled for processor " + processor.getName());
        }
        
        try {
            WebSocketReplyData reply = toAckReply(requestId);
            wsHandler.sendReply(reply);
            
            AlarmServer alarmServer = processor.getParameterRequestManager().getAlarmServer();
            for (ActiveAlarm activeAlarm : alarmServer.getActiveAlarms().values()) {
                sendAlarm(Alarm.Type.ACTIVE, activeAlarm);
            }
            doSubscribe();
            return null;
        } catch (IOException e) {
            log.error("Exception when sending data", e);
            return null;
        }
    }
    
    private WebSocketReplyData unsubscribe(int requestId) throws WebSocketException {
        doUnsubscribe();
        return toAckReply(requestId);
    }

    @Override
    public void quit() {
        doUnsubscribe();
    }

    @Override
    public void switchYProcessor(YProcessor newProcessor) throws YProcessorException {
        doUnsubscribe();
        processor = newProcessor;
        doSubscribe();
    }
    
    private void doSubscribe() {
        if (processor.hasAlarmServer()) {
            AlarmServer alarmServer = processor.getParameterRequestManager().getAlarmServer();
            alarmServer.subscribe(this);
        }
    }
    
    private void doUnsubscribe() {
        if (processor.hasAlarmServer()) {
            AlarmServer alarmServer = processor.getParameterRequestManager().getAlarmServer();
            alarmServer.unsubscribe(this);       
        }
    }
    
    @Override
    public void notifyTriggered(ActiveAlarm activeAlarm) {
        sendAlarm(Alarm.Type.TRIGGERED, activeAlarm);
    }
    
    @Override
    public void notifySeverityIncrease(ActiveAlarm activeAlarm) {
        sendAlarm(Alarm.Type.SEVERITY_INCREASED, activeAlarm);
    }
    
    @Override
    public void notifyParameterValueUpdate(ActiveAlarm activeAlarm) {
        sendAlarm(Alarm.Type.PVAL_UPDATED, activeAlarm);
    }
    
    @Override
    public void notifyAcknowledged(ActiveAlarm activeAlarm) {
        sendAlarm(Alarm.Type.ACKNOWLEDGED, activeAlarm);
    }
    
    @Override
    public void notifyCleared(ActiveAlarm activeAlarm) {
        sendAlarm(Alarm.Type.CLEARED, activeAlarm);
    }
    
    private void sendAlarm(Alarm.Type type, ActiveAlarm activeAlarm) {
        NamedObjectId parameterId = NamedObjectId.newBuilder()
                .setName(activeAlarm.triggerValue.getParameter().getQualifiedName())
                .build();
        Alarm.Builder alarmb = Alarm.newBuilder();        
        alarmb.setType(type);
        alarmb.setId(activeAlarm.id);
        alarmb.setTriggerValue(activeAlarm.triggerValue.toGpb(parameterId));
        alarmb.setMostSevereValue(activeAlarm.mostSevereValue.toGpb(parameterId));
        alarmb.setCurrentValue(activeAlarm.currentValue.toGpb(parameterId));
        alarmb.setViolations(activeAlarm.violations);
        
        if (activeAlarm.acknowledged) {
            AcknowledgeInfo.Builder acknowledgeb = AcknowledgeInfo.newBuilder();
            String username = activeAlarm.usernameThatAcknowledged;
            if (username == null) {
                username = (activeAlarm.autoAcknowledge) ? "autoAcknowledged" : "unknown";
            }
            acknowledgeb.setAcknowledgedBy(username);
            acknowledgeb.setAcknowledgeMessage(activeAlarm.message);
            acknowledgeb.setAcknowledgeTime(activeAlarm.acknowledgeTime);
            acknowledgeb.setAcknowledgeTimeUTC(TimeEncoding.toString(activeAlarm.acknowledgeTime));
            alarmb.setAcknowledgeInfo(acknowledgeb.build());
        }
        
        try {
            wsHandler.sendData(ProtoDataType.ALARM, alarmb.build(), SchemaAlarms.Alarm.WRITE);
        } catch (Exception e) {
            log.warn("got error when sending alarm, quitting", e);
            quit();
        }
    }
}
