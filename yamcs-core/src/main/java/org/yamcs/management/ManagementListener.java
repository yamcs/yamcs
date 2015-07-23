package org.yamcs.management;

import org.yamcs.YProcessor;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.Statistics;

/**
 * Used by ManagementService to distribute various types of management-related updates
 */
public interface ManagementListener {
    
    void processorAdded(ProcessorInfo processorInfo);

    void processorClosed(ProcessorInfo processorInfo);

    void processorStateChanged(ProcessorInfo processorInfo);

    void clientRegistered(ClientInfo clientInfo);

    void clientUnregistered(ClientInfo clientInfo);

    void clientInfoChanged(ClientInfo clientInfo);
    
    /**
     * Called by the {@link ManagementService} when the statistics for the given processor were updated.
     * This usually happens at about 1Hz.
     */
    void statisticsUpdated(YProcessor processor, Statistics stats);
}
