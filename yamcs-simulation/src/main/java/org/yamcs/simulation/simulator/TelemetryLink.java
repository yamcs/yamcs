package org.yamcs.simulation.simulator;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by msc on 29/05/15.
 */
public class TelemetryLink {

    private static final Logger log = LoggerFactory.getLogger(TelemetryLink.class);

    private int tmPort;
    private int tcPort;
    private int losPort;

    private Simulator simulator;

    private boolean connected = false;

    private Socket tmSocket;
    private Socket tcSocket;
    private Socket losSocket;

    private ServerSocket tmServerSocket;
    private ServerSocket tcServerSocket;
    private ServerSocket losServerSocket;

    private BlockingQueue<CCSDSPacket> tmQueue = new LinkedBlockingQueue<>(); // no more than 100 pending commands
    private BlockingQueue<CCSDSPacket> tmDumpQueue = new ArrayBlockingQueue<>(1000);

    public TelemetryLink(Simulator simulator, int tmPort, int tcPort, int losPort) {
        this.simulator = simulator;
        this.tmPort = tmPort;
        this.tcPort = tcPort;
        this.losPort = losPort;
    }

    public void tmTransmit(CCSDSPacket packet) {
        try {
            tmQueue.put(packet);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void packetSend() {
        while (true) {
            if (!simulator.isLOS()) {
                tmPacketSend();
                for (int i = 0; i < 5; i++) {
                    if (connected && !tmDumpQueue.isEmpty()) {
                        try {
                            tmDumpQueue.remove().writeTo(losSocket.getOutputStream());
                        } catch (IOException e1) {
                            log.error("Error while sending TM dump packet", e1);
                            yamcsServerConnect();
                        }
                    }
                }
            } else {
                if (connected && !tmQueue.isEmpty()) {
                    CCSDSPacket packet = tmQueue.remove();
                    simulator.getLosDataRecorder().record(packet);
                }
            }
            try {
                Thread.sleep(1000 / 20); // 20 Hz
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void yamcsServerConnect() {

        // Check for previous connection, used for loss of server.
        if (tmSocket != null) {
            try {
                connected = false;
                tmSocket.close();
                tmServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (tcSocket != null) {
            try {
                connected = false;
                tcSocket.close();
                tcServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (losSocket != null) {
            try {
                connected = false;
                losSocket.close();
                losServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            log.info("Waiting for connection from server");
            tmServerSocket = new ServerSocket(tmPort);
            tmSocket = tmServerSocket.accept();

            log.info("Connected TM: {}:{}", tmSocket.getInetAddress(), tmSocket.getPort());
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            tcServerSocket = new ServerSocket(tcPort);
            tcSocket = tcServerSocket.accept();

            log.info("Connected TC: {}:{}", tcSocket.getInetAddress(), tcSocket.getPort());
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            losServerSocket = new ServerSocket(losPort);
            losSocket = losServerSocket.accept();

            log.info("Connected TM DUMP: {}:{}", losSocket.getInetAddress(), losSocket.getPort());
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (tcSocket != null && tmSocket != null && losSocket != null) {
            connected = true;
        }
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public Socket getTcSocket() {
        return tcSocket;
    }

    private void tmPacketSend() {
        if (connected && !tmQueue.isEmpty()) {
            try {
                tmQueue.remove().writeTo(tmSocket.getOutputStream());
            } catch (IOException e1) {
                log.error("Error while sending TM packet", e1);
                yamcsServerConnect();
            }
        }
    }

    public void ackPacketSend(CCSDSPacket packet) {
        if (connected) {
            try {
                packet.writeTo(tmSocket.getOutputStream());
            } catch (IOException e1) {
                log.error("Error while sending TM packet", e1);
                yamcsServerConnect();
            }
        }
    }

    public void addTmDumpPacket(CCSDSPacket packet) throws InterruptedException {
        tmDumpQueue.put(packet);
    }
}
