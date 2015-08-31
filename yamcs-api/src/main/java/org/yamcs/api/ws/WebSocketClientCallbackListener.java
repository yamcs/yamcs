package org.yamcs.api.ws;

import org.yamcs.protobuf.Alarms.Alarm;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.StreamData;
import org.yamcs.protobuf.Yamcs.TimeInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.Statistics;

/**
 * TODO Get rid of this ever-growing ugly interface. Use futures.
 */
public interface WebSocketClientCallbackListener {
    void onConnect();
    void onException(Throwable t);
    void onDisconnect();
    void onInvalidIdentification(NamedObjectId id);
    void onParameterData(ParameterData pdata);
    void onCommandHistoryData(CommandHistoryEntry cmdhistData);
    void onClientInfoData(ClientInfo clientInfo);
    void onProcessorInfoData(ProcessorInfo processorInfo);
    void onStatisticsData(Statistics statistics);
    void onAlarm(Alarm alarm);
    void onEvent(Event event);
    void onStreamData(StreamData streamData);
    void onTimeInfo(TimeInfo timeInfo);
}
