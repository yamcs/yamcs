package org.yamcs.simulator;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

/**
 * Created by msc on 29/05/15.
 */
public class TelemetryLink {

    Simulator simulator;
    List<ServerConnection> serversConnections;

    public TelemetryLink(Simulator simulator, List<ServerConnection> serversConnections) {
        this.simulator = simulator;
        this.serversConnections = serversConnections;
    }

    public void tmTransmit(CCSDSPacket packet) {
        for (ServerConnection s : serversConnections) {
            s.setTmPacket(packet);
        }
    }

    public void packetSend(ServerConnection sI) {
        while (true) {
            if (!simulator.isLos) {
                //System.out.print("packet Send");
                tmPacketSend(sI);
                tmPacketDump(sI);
            } else {
                //System.out.print("packet store");
                tmPacketStore(sI);
            }
            try {
                Thread.sleep(1000 / 20); // 20 Hz
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void tmPacketSend(ServerConnection sI) {

        if (sI.isConnected() && !sI.checkTmQueue()) {

            try {

                sI.getTmPacket().send(sI.getTmSocket().getOutputStream());

            } catch (IOException e1) {
                System.out.println(e1);
                yamcsServerConnect(sI);
                // e1.printStackTrace();
            }

        }
    }

    private void tmPacketDump(ServerConnection sI) {

        if (sI.isConnected() && !sI.checkTmDumpQueue()) {

            try {

                sI.getTmDumpPacket().send(sI.getLosSocket().getOutputStream());

            } catch (IOException e1) {
                System.out.println(e1);
                yamcsServerConnect(sI);
                // e1.printStackTrace();
            }

        }
    }

    private void tmPacketStore(ServerConnection sI) {
        if (sI.isConnected() && !sI.checkTmQueue()) {
            // Not the best solution, the if condition stop the LOS file from having double instances
            // Might rework the logic at a later date
            if (sI.getId() == 0) {
                CCSDSPacket packet = sI.getTmPacket();
                simulator.losStore.tmPacketStore(packet);
            }
        }
    }


    public static void yamcsServerConnect(ServerConnection sI) {

        //Check for previous connection, used for loss of server.
        if (sI.getTmSocket() != null) {

            try {
                sI.setConnected(false);
                sI.getTmSocket().close();
                sI.getTmServerSocket().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (sI.getTcSocket() != null) {

            try {
                sI.setConnected(false);
                sI.getTcSocket().close();
                sI.getTcServerSocket().close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        if (sI.getLosSocket() != null) {

            try {
                sI.setConnected(false);
                sI.getLosSocket().close();
                sI.getLosServerSocket().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        if(Simulator.simWindow != null)
            Simulator.simWindow.setServerStatus(sI.getId(), ServerConnection.ConnectionStatus.CONNECTING);
        try {
            System.out.println("Waiting for connection from server " + sI.getId());
            sI.setTmServerSocket(new ServerSocket(sI.getTmPort()));

            sI.setTmSocket(sI.getTmServerSocket().accept());
            logMessage(sI.getId(), "Connected TM: "
                    + sI.getTmSocket().getInetAddress() + ":"
                    + sI.getTmSocket().getPort());
        } catch (Exception e) {

            e.printStackTrace();
        }

        try {
            sI.setTcServerSocket(new ServerSocket(sI.getTcPort()));
            sI.setTcSocket(sI.getTcServerSocket().accept());

            logMessage(sI.getId(), "Connected TC: "
                    + sI.getTcSocket().getInetAddress() + ":"
                    + sI.getTcSocket().getPort());

        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            sI.setLosServerSocket(new ServerSocket(sI.getLosPort()));
            sI.setLosSocket(sI.getLosServerSocket().accept());

            logMessage(sI.getId(), "Connected TM DUMP: "
                    + sI.getLosSocket().getInetAddress() + ":"
                    + sI.getLosSocket().getPort());

        } catch (Exception e) {
            e.printStackTrace();
        }

        if (sI.getTcSocket() != null && sI.getTmSocket() != null && sI.getLosSocket() != null) {

            sI.setConnected(true);
            if(Simulator.simWindow != null)
                Simulator.simWindow.setServerStatus(sI.getId(), ServerConnection.ConnectionStatus.CONNECTED);
        }
    }

    private static void logMessage(int serverId, String message)
    {
        System.out.println(message);
        if(Simulator.simWindow != null)
            Simulator.simWindow.addLog(serverId, message + "\n");
    }
}
