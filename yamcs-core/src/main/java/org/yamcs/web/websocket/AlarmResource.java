package org.yamcs.web.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ParameterValue;
import org.yamcs.YProcessor;
import org.yamcs.YProcessorException;
import org.yamcs.protobuf.Alarms.AlarmNotice;
import org.yamcs.protobuf.Alarms.AlarmNotice.Type;
import org.yamcs.protobuf.SchemaAlarms;
import org.yamcs.protobuf.Websocket.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;

/**
 * Provides realtime alarm subscription via web.
 */
public class AlarmResource extends AbstractWebSocketResource implements StreamSubscriber {
    Logger log;

    public AlarmResource(YProcessor channel, WebSocketServerHandler wsHandler) {
        super(channel, wsHandler);
        log = LoggerFactory.getLogger(AlarmResource.class.getName() + "[" + channel.getInstance() + "]");
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
        
        doSubscribe();
        return toAckReply(requestId);
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
    
    // FIXME This obviously will only ever generate realtime alarms
    private void doSubscribe() {
        if (processor.hasAlarmServer()) {
            YarchDatabase ydb = YarchDatabase.getInstance(processor.getInstance());
            Stream stream = ydb.getStream("alarms_realtime");
            stream.addSubscriber(this);
        }
    }
    
    private void doUnsubscribe() {
        if (processor.hasAlarmServer()) {
            YarchDatabase ydb = YarchDatabase.getInstance(processor.getInstance());
            Stream stream = ydb.getStream("alarms_realtime");
            stream.removeSubscriber(this);       
        }
    }
    
    @Override
    public void onTuple(Stream stream, Tuple tuple) {
        AlarmNotice.Builder alarmb = AlarmNotice.newBuilder();
        alarmb.setTriggerTime((Long) tuple.getColumn("triggerTime"));
        
        String alarmEvent = (String) tuple.getColumn("event");
        ParameterValue pval;
        switch (alarmEvent) {
        case "TRIGGERED":
            alarmb.setType(Type.TRIGGERED);
            pval = (ParameterValue) tuple.getColumn("triggerPV");
            break;
        case "UPDATED":
            alarmb.setType(Type.UPDATED);
            pval = (ParameterValue) tuple.getColumn("updatedPV");
            break;
        case "SEVERITY_INCREASED":
            alarmb.setType(Type.SEVERITY_INCREASED);
            pval = (ParameterValue) tuple.getColumn("severityIncreasedPV");
            break;
        case "CLEARED":
            alarmb.setType(Type.CLEARED);
            alarmb.setUsername((String) tuple.getColumn("username"));
            pval = (ParameterValue) tuple.getColumn("clearedPV");
            break;
        default:
            throw new IllegalArgumentException("Unexpected alarm event " + alarmEvent);
        }
        
        String qualifiedName = (String) tuple.getColumn("parameter");
        NamedObjectId id = NamedObjectId.newBuilder().setName(qualifiedName).build();
        alarmb.setPval(pval.toGpb(id));
        
        try {
            wsHandler.sendData(ProtoDataType.ALARM_NOTICE, alarmb.build(), SchemaAlarms.AlarmNotice.WRITE);
        } catch (Exception e) {
            log.warn("got error when sending alarm updates, quitting", e);
            quit();
        }
    }

    @Override
    public void streamClosed(Stream stream) {
    }
}
