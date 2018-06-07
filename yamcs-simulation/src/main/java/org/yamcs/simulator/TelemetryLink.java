package org.yamcs.simulator;

import java.util.List;

/**
 * Created by msc on 29/05/15.
 */
public class TelemetryLink {
    private List<ServerConnection> serverConnections;

    public TelemetryLink(SimulationConfiguration simConfig) {
        serverConnections = simConfig.getServerConnections();
    }

    public void tmTransmit(CCSDSPacket packet) {
        for (ServerConnection s : serverConnections) {
            s.queueTmPacket(packet);
        }
    }

   
}
