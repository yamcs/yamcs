package org.yamcs.client;

import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.Statistics;

public interface ProcessorListener {

    public void log(String text);

    public void popup(String text);

    public void processorUpdated(ProcessorInfo ci);

    public void processorClosed(ProcessorInfo ci);

    public void clientDisconnected(ClientInfo ci);

    public void clientUpdated(ClientInfo ci);

    public void updateStatistics(Statistics s);
}
