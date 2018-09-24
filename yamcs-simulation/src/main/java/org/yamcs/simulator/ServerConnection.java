package org.yamcs.simulator;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerConnection {
    private static final Logger log = LoggerFactory.getLogger(ServerConnection.class);

    public enum ConnectionStatus {
        NOT_CONNECTED, CONNECTING, CONNECTED, CONNECTION_LOST
    }

    private boolean tmConnected = false;
    private boolean losConnected = false;
    private boolean tcConnected = false;

    private Socket tmSocket;
    private Socket tcSocket;
    private Socket losSocket;

    private ServerSocket tmServerSocket;
    private ServerSocket tcServerSocket;
    private ServerSocket losServerSocket;

    private int tmPort;
    private int tcPort;
    private int losPort;

    private int id;

    BlockingQueue<CCSDSPacket> tmQueue = new LinkedBlockingQueue<>();// no more than 100 pending commands
    BlockingQueue<CCSDSPacket> tmDumpQueue = new ArrayBlockingQueue<>(1000);

    private Simulator simulator;

    DataInputStream tcInputStream;

    public ServerConnection(int id, int tmPort, int tcPort, int losPort) {
        this.id = id;
        this.tmPort = tmPort;
        this.tcPort = tcPort;
        this.losPort = losPort;
    }

    public void setSimulator(Simulator simulator) {
        this.simulator = simulator;
    }

    public Socket getTcSocket() {
        return tcSocket;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void queueTmPacket(CCSDSPacket packet) {
        try {
            this.tmQueue.put(packet);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void addTmDumpPacket(CCSDSPacket packet) throws InterruptedException {
        this.tmDumpQueue.put(packet);
    }

    public boolean isTmDumpQueueEmpty() {
        return tmDumpQueue.isEmpty();
    }

    public void packetSendThread() {
        while (true) {
            try {
                CCSDSPacket packet = tmQueue.take();
                if (!simulator.isLOS()) {
                    if (!tmConnected) {
                        connectTm();
                    }
                    try {
                        packet.writeTo(tmSocket.getOutputStream());
                    } catch (Exception e) {
                        log.info("Error writing to socket: " + e.getMessage());
                        tmConnected = false;
                    }
                } else {
                    if (id == 0) {
                        // Not the best solution, the if condition stop the LOS file from having double instances
                        // Might rework the logic at a later date
                        simulator.getLosStore().tmPacketStore(packet);
                    }
                }

            } catch (InterruptedException e) {
                return;
            }
        }
    }

    public void losSendThread() {
        while (true) {
            if (!losConnected) {
                connectLos();
            }
            try {
                CCSDSPacket packet = tmDumpQueue.take();
                try {
                    packet.writeTo(losSocket.getOutputStream());
                } catch (Exception e) {
                    log.info("Error writing to socket: " + e.getMessage());
                    losConnected = false;
                }
            } catch (InterruptedException e) {
                return;
            }
        }

    }

    public void connectTc() {
        if (tcSocket != null) {
            tcConnected = false;
            try {
                tcSocket.close();
                tcServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            tcServerSocket = new ServerSocket(tcPort);
            tcSocket = tcServerSocket.accept();
            tcInputStream = new DataInputStream(tcSocket.getInputStream());
            tcConnected = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void connectLos() {
        if (losSocket != null) {
            losConnected = false;
            try {
                losSocket.close();
                losServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            log.info("Waiting for LOS connection from server {}", id);
            losServerSocket = new ServerSocket(losPort);
            losSocket = losServerSocket.accept();
            losConnected = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void connectTm() {
        // Check for previous connection, used for loss of server.
        if (tmSocket != null) {
            tmConnected = false;
            try {
                tmSocket.close();
                tmServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            log.info("Waiting for TM connection from server {}", id);
            tmServerSocket = new ServerSocket(tmPort);
            tmSocket = tmServerSocket.accept();
            tmConnected = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public boolean isTcConnected() {
        return tcConnected;
    }

    public void setTcConnected(boolean b) {
        tcConnected = b;
    }

    public DataInputStream getTcInputStream() {
        return tcInputStream;
    }
}
