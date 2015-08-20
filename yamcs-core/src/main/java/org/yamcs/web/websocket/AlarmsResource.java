package org.yamcs.web.websocket;

import java.io.IOException;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.YProcessorException;
import org.yamcs.alarms.ActiveAlarm;
import org.yamcs.alarms.AlarmListener;
import org.yamcs.alarms.AlarmServer;
import org.yamcs.protobuf.Alarms.Alarm;
import org.yamcs.protobuf.Alarms.AlarmNotice;
import org.yamcs.protobuf.SchemaAlarms;
import org.yamcs.protobuf.Websocket.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.xtce.Parameter;

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
            try {
                for (Entry<Parameter, ActiveAlarm> entry : alarmServer.getActiveAlarms().entrySet()) {
                    NamedObjectId id = NamedObjectId.newBuilder().setName(entry.getKey().getQualifiedName()).build();
                    Alarm.Builder alarmb = Alarm.newBuilder();
                    alarmb.setId(entry.getValue().id);
                    alarmb.setTriggerValue(entry.getValue().triggerValue.toGpb(id));
                    alarmb.setMostSevereValue(entry.getValue().mostSevereValue.toGpb(id));
                    alarmb.setCurrentValue(entry.getValue().currentValue.toGpb(id));
                    alarmb.setViolations(entry.getValue().violations);
                    wsHandler.sendData(ProtoDataType.ALARM, alarmb.build(), SchemaAlarms.Alarm.WRITE);   
                }
            } catch (IOException e) {
                log.warn("got error when sending parameter updates, quitting", e);
                quit();
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
        NamedObjectId parameterId = NamedObjectId.newBuilder()
                .setName(activeAlarm.triggerValue.getParameter().getQualifiedName())
                .build();
        Alarm.Builder alarmb = Alarm.newBuilder();        
        alarmb.setId(activeAlarm.id);
        alarmb.setTriggerValue(activeAlarm.triggerValue.toGpb(parameterId));
        alarmb.setMostSevereValue(activeAlarm.mostSevereValue.toGpb(parameterId));
        alarmb.setCurrentValue(activeAlarm.currentValue.toGpb(parameterId));
        alarmb.setViolations(activeAlarm.violations);
        
        try {
            wsHandler.sendData(ProtoDataType.ALARM, alarmb.build(), SchemaAlarms.Alarm.WRITE);
        } catch (Exception e) {
            log.warn("got error when sending alarm, quitting", e);
            quit();
        }
    }
    
    @Override
    public void notifySeverityIncrease(ActiveAlarm activeAlarm) {
        NamedObjectId parameterId = NamedObjectId.newBuilder()
                .setName(activeAlarm.mostSevereValue.getParameter().getQualifiedName())
                .build();
        AlarmNotice.Builder alarmb = AlarmNotice.newBuilder();        
        alarmb.setType(AlarmNotice.Type.SEVERITY_INCREASED);
        alarmb.setAlarmId(activeAlarm.id);
        alarmb.setPval(activeAlarm.mostSevereValue.toGpb(parameterId));
        
        try {
            wsHandler.sendData(ProtoDataType.ALARM_NOTICE, alarmb.build(), SchemaAlarms.AlarmNotice.WRITE);
        } catch (Exception e) {
            log.warn("got error when sending alarm updates, quitting", e);
            quit();
        }
    }
    
    @Override
    public void notifyUpdate(ActiveAlarm activeAlarm) {
        NamedObjectId parameterId = NamedObjectId.newBuilder()
                .setName(activeAlarm.currentValue.getParameter().getQualifiedName())
                .build();
        AlarmNotice.Builder alarmb = AlarmNotice.newBuilder();
        alarmb.setType(AlarmNotice.Type.UPDATED);
        alarmb.setAlarmId(activeAlarm.id);
        alarmb.setPval(activeAlarm.currentValue.toGpb(parameterId));
        
        try {
            wsHandler.sendData(ProtoDataType.ALARM_NOTICE, alarmb.build(), SchemaAlarms.AlarmNotice.WRITE);
        } catch (Exception e) {
            log.warn("got error when sending alarm updates, quitting", e);
            quit();
        }
    }
    
    @Override
    public void notifyCleared(ActiveAlarm activeAlarm) {
        NamedObjectId parameterId = NamedObjectId.newBuilder()
                .setName(activeAlarm.currentValue.getParameter().getQualifiedName())
                .build();
        AlarmNotice.Builder alarmb = AlarmNotice.newBuilder();
        alarmb.setType(AlarmNotice.Type.CLEARED);
        alarmb.setAlarmId(activeAlarm.id);
        alarmb.setPval(activeAlarm.currentValue.toGpb(parameterId));
        
        String username = activeAlarm.usernameThatAcknowledged;
        if (username == null) {
            username = (activeAlarm.autoAcknowledge) ? "autoAcknowledged" : "unknown";
        }
        alarmb.setUsername(username);
        
        try {
            wsHandler.sendData(ProtoDataType.ALARM_NOTICE, alarmb.build(), SchemaAlarms.AlarmNotice.WRITE);
        } catch (Exception e) {
            log.warn("got error when sending alarm updates, quitting", e);
            quit();
        }
    }
}
