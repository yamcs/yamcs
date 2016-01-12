package org.yamcs.simulator;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by msc on 29/05/15.
 */
public class TelemetryLink {
    
    private static final Logger log = LoggerFactory.getLogger(TelemetryLink.class);

    private Simulator simulator;
    private List<ServerConnection> serverConnections;

    public TelemetryLink(Simulator simulator, SimulationConfiguration simConfig) {
        this.simulator = simulator;
        serverConnections = simConfig.getServerConnections();
    }

    public void tmTransmit(CCSDSPacket packet) {
        for (ServerConnection s : serverConnections) {
            s.queueTmPacket(packet);
        }
    }

    public void packetSend(ServerConnection conn) {
        while (true) {
            if (!simulator.isLOS()) {
                //System.out.print("packet Send");
                tmPacketSend(conn);
                tmPacketDump(conn);
            } else {
                //System.out.print("packet store");
                tmPacketStore(conn);
            }
            try {
                Thread.sleep(1000 / 20); // 20 Hz
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void tmPacketSend(ServerConnection conn) {
        if (conn.isConnected() && !conn.isTmQueueEmpty()) {
            try {
                conn.getTmPacket().writeTo(conn.getTmSocket().getOutputStream());
            } catch (IOException e1) {
                log.error("Error while sending TM packet", e1);
                yamcsServerConnect(conn);
            }
        }
    }

    private void tmPacketDump(ServerConnection conn) {
        if (conn.isConnected() && !conn.isTmDumpQueueEmpty()) {
            try {
                conn.getTmDumpPacket().writeTo(conn.getLosSocket().getOutputStream());
            } catch (IOException e1) {
                log.error("Error while sending TM dump packet", e1);
                yamcsServerConnect(conn);
            }
        }
    }

    private void tmPacketStore(ServerConnection conn) {
        if (conn.isConnected() && !conn.isTmQueueEmpty()) {
            // Not the best solution, the if condition stop the LOS file from having double instances
            // Might rework the logic at a later date
            if (conn.getId() == 0) {
                CCSDSPacket packet = conn.getTmPacket();
                simulator.getLosStore().tmPacketStore(packet);
            }
        }
    }

    public void yamcsServerConnect(ServerConnection conn) {

        //Check for previous connection, used for loss of server.
        if (conn.getTmSocket() != null) {
            try {
                conn.setConnected(false);
                conn.getTmSocket().close();
                conn.getTmServerSocket().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (conn.getTcSocket() != null) {
            try {
                conn.setConnected(false);
                conn.getTcSocket().close();
                conn.getTcServerSocket().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (conn.getLosSocket() != null) {
            try {
                conn.setConnected(false);
                conn.getLosSocket().close();
                conn.getLosServerSocket().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if(simulator.getSimWindow() != null)
            simulator.getSimWindow().setServerStatus(conn.getId(), ServerConnection.ConnectionStatus.CONNECTING);
        try {
            log.info("Waiting for connection from server " + conn.getId());
            conn.setTmServerSocket(new ServerSocket(conn.getTmPort()));
            conn.setTmSocket(conn.getTmServerSocket().accept());
            
            Socket tmSocket = conn.getTmSocket();
            logMessage(conn.getId(), "Connected TM: "
                    + tmSocket.getInetAddress() + ":"
                    + tmSocket.getPort());
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            conn.setTcServerSocket(new ServerSocket(conn.getTcPort()));
            conn.setTcSocket(conn.getTcServerSocket().accept());

            Socket tcSocket = conn.getTcSocket();
            logMessage(conn.getId(), "Connected TC: "
                    + tcSocket.getInetAddress() + ":"
                    + tcSocket.getPort());
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            conn.setLosServerSocket(new ServerSocket(conn.getLosPort()));
            conn.setLosSocket(conn.getLosServerSocket().accept());
            
            Socket losSocket = conn.getLosSocket();
            logMessage(conn.getId(), "Connected TM DUMP: "
                    + losSocket.getInetAddress() + ":"
                    + losSocket.getPort());
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (conn.getTcSocket() != null && conn.getTmSocket() != null && conn.getLosSocket() != null) {
            conn.setConnected(true);
            if(simulator.getSimWindow() != null)
                simulator.getSimWindow().setServerStatus(conn.getId(), ServerConnection.ConnectionStatus.CONNECTED);
        }
    }
    
    private void logMessage(int serverId, String message) {
        log.info(message);
        if(simulator.getSimWindow() != null)
            simulator.getSimWindow().addLog(serverId, message + "\n");
    }
}
