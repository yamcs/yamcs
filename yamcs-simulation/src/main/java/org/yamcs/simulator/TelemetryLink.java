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

    Simulator simulation;
    List<ServerConnection> serverConnections;

    public TelemetryLink(Simulator simulation, SimulationConfiguration simConfig) {
        this.simulation = simulation;
        serverConnections = simConfig.getServerConnections();
    }

    public void tmTransmit(CCSDSPacket packet) {
        for (ServerConnection s : serverConnections) {
            s.setTmPacket(packet);
        }
    }

    public void packetSend(ServerConnection conn) {
        while (true) {
            if (!simulation.isLOS()) {
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
        if (conn.isConnected() && !conn.checkTmQueue()) {
            try {
                conn.getTmPacket().writeTo(conn.getTmSocket().getOutputStream());
            } catch (IOException e1) {
                log.error("Error while sending TM packet", e1);
                yamcsServerConnect(conn);
            }
        }
    }

    private void tmPacketDump(ServerConnection conn) {
        if (conn.isConnected() && !conn.checkTmDumpQueue()) {
            try {
                conn.getTmDumpPacket().writeTo(conn.getLosSocket().getOutputStream());
            } catch (IOException e1) {
                log.error("Error while sending TM dump packet", e1);
                yamcsServerConnect(conn);
            }
        }
    }

    private void tmPacketStore(ServerConnection conn) {
        if (conn.isConnected() && !conn.checkTmQueue()) {
            // Not the best solution, the if condition stop the LOS file from having double instances
            // Might rework the logic at a later date
            if (conn.getId() == 0) {
                CCSDSPacket packet = conn.getTmPacket();
                simulation.getLosStore().tmPacketStore(packet);
            }
        }
    }

    public static void yamcsServerConnect(ServerConnection conn) {

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

        try {
            log.info("Waiting for connection from server " + conn.getId());
            conn.setTmServerSocket(new ServerSocket(conn.getTmPort()));
            conn.setTmSocket(conn.getTmServerSocket().accept());
            
            Socket tmSocket = conn.getTmSocket();
            log.info("Connected TM {}:{}", tmSocket.getInetAddress(), tmSocket.getPort());
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            conn.setTcServerSocket(new ServerSocket(conn.getTcPort()));
            conn.setTcSocket(conn.getTcServerSocket().accept());

            Socket tcSocket = conn.getTcSocket();
            log.info("Connected TC {}:{}", tcSocket.getInetAddress(), tcSocket.getPort());
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            conn.setLosServerSocket(new ServerSocket(conn.getLosPort()));
            conn.setLosSocket(conn.getLosServerSocket().accept());
            
            Socket losSocket = conn.getLosSocket();
            log.info("Connected TM DUMP {}:{}", losSocket.getInetAddress(), losSocket.getPort());
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (conn.getTcSocket() != null && conn.getTmSocket() != null && conn.getLosSocket() != null) {
            conn.setConnected(true);
        }
    }
}
