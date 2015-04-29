package org.yamcs.api.ws;

import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;

public interface WebSocketClientCallbackListener {
    void onConnect();
    void onDisconnect();
    void onInvalidIdentification(NamedObjectId id);
    void onParameterData(ParameterData pdata);
    void onCommandHistoryData(CommandHistoryEntry cmdhistData);
    void onClientInfoData(ClientInfo clientInfo);
    void onProcessorInfoData(ProcessorInfo processorInfo);
}