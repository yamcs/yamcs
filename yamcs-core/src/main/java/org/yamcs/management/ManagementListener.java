package org.yamcs.management;

import org.yamcs.YProcessorListener;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;

/**
 * Used by ManagementService to distribute updates wrt processor & clients
 */
public interface ManagementListener extends YProcessorListener {

    public void registerClient(ClientInfo ci);

    public void unregisterClient(ClientInfo ci);

    public void clientInfoChanged(ClientInfo ci);
}
