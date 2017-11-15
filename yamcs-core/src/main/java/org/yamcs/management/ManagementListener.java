package org.yamcs.management;

import org.yamcs.Processor;
import org.yamcs.YamcsServerInstance;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.Statistics;

import com.google.common.util.concurrent.Service;

/**
 * Used by ManagementService to distribute various types of management-related updates
 */
public interface ManagementListener {
    
    default void processorAdded(ProcessorInfo processorInfo) {}

    default void processorClosed(ProcessorInfo processorInfo) {}

    default void processorStateChanged(ProcessorInfo processorInfo) {}

    default void clientRegistered(ClientInfo clientInfo) {}

    default void clientUnregistered(ClientInfo clientInfo) {}

    default void clientInfoChanged(ClientInfo clientInfo) {}
    
    default void clientInfoChanged(ClientInfo oldClientInfo, ClientInfo newClientInfo) {
        clientInfoChanged(newClientInfo);
    }
    /**
     * Called by the {@link ManagementService} when the statistics for the given processor were updated.
     * This usually happens at about 1Hz.
     */
    default void statisticsUpdated(Processor processor, Statistics stats) {}

    /**
     * Called when an instance state changes - for example when it is stopped/started
     * @param ysi
     */
    default void instanceStateChanged(YamcsServerInstance ysi) {}

    default void serviceRegistered(String instance, String serviceName, Service service) {}
    
    default void serviceUnregistered(String instance, String serviceName){}
}
