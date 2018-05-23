package org.yamcs.simulator;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.simulator.ui.SimWindow;

public class Simulator extends Thread {

    protected BlockingQueue<CCSDSPacket> pendingCommands = new ArrayBlockingQueue<>(100); // no more than 100 pending
    // commands

    private int DEFAULT_MAX_LENGTH = 65542;
    private int maxLength = DEFAULT_MAX_LENGTH;

    private SimulationConfiguration simConfig;
    private TelemetryLink tmLink;

    private boolean isLos = false;
    private LosStore losStore;

    private SimWindow simWindow;

    private static final Logger log = LoggerFactory.getLogger(Simulator.class);

    public Simulator(SimulationConfiguration simConfig) {
        this.simConfig = simConfig;
        tmLink = new TelemetryLink(this, simConfig);
        losStore = new LosStore(this, simConfig);
    }

    @Override
    public void run() {
        for (ServerConnection serverConnection : simConfig.getServerConnections()) {
            tmLink.yamcsServerConnect(serverConnection);

            // start the TC reception thread;
            new Thread(() -> {
                while (true) {
                    try {
                        // read commands
                        CCSDSPacket packet = readPacket(
                                new DataInputStream(serverConnection.getTcSocket().getInputStream()));
                        if (packet != null)
                            pendingCommands.put(packet);

                    } catch (IOException e) {
                        serverConnection.setConnected(false);
                        tmLink.yamcsServerConnect(serverConnection);
                    } catch (InterruptedException e) {
                        log.warn("Read packets interrupted.", e);
                        Thread.currentThread().interrupt();
                    }
                }
            }).start();

            // start the TM transmission thread
            log.debug("Start TM thread");
            (new Thread(() -> tmLink.packetSend(serverConnection))).start();
        }
    }

    /**
     * this runs in a separate thread but pushes commands to the main TM thread
     */
    protected CCSDSPacket readPacket(DataInputStream dIn) {
        try {
            byte hdr[] = new byte[6];
            dIn.readFully(hdr);
            int remaining = ((hdr[4] & 0xFF) << 8) + (hdr[5] & 0xFF) + 1;
            if (remaining > maxLength - 6)
                throw new IOException("Remaining packet length too big: " + remaining + " maximum allowed is " + (maxLength - 6));
            byte[] b = new byte[6 + remaining];
            System.arraycopy(hdr, 0, b, 0, 6);
            dIn.readFully(b, 6, remaining);
            CCSDSPacket packet = new CCSDSPacket(ByteBuffer.wrap(b));
            tmLink.ackPacketSend(ackPacket(packet, 0, 0));
            return packet;

        } catch (IOException e) {
            log.error("Connection lost:" + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error reading command " + e.getMessage(), e);
        }
        return null;

    }

    public SimulationConfiguration getSimulationConfiguration() {
        return simConfig;
    }

    public LosStore getLosStore() {
        return losStore;
    }

    public boolean isLOS() {
        return isLos;
    }

    public void setLOS(boolean isLos) {
        this.isLos = isLos;
    }

    public TelemetryLink getTMLink() {
        return tmLink;
    }

    protected void transmitTM(CCSDSPacket packet) {
        packet.fillChecksum();
        tmLink.tmTransmit(packet);
    }

    public void dumpLosDataFile(String filename) {
        // read data from los storage
        if (filename == null) {
            filename = losStore.getCurrentFileName();
        }
        DataInputStream dataStream = losStore.readLosFile(filename);
        if (dataStream == null)
            return;
        try {
            while (dataStream.available() > 0) {
                CCSDSPacket packet = readPacket(dataStream);
                if (packet != null) {
                    for (ServerConnection serverConnection : simConfig.getServerConnections())
                        serverConnection.addTmDumpPacket(packet);
                }
            }

            // add packet notifying that the file has been downloaded entirely
            CCSDSPacket confirmationPacket = buildLosTransmittedRecordingPacket(filename);
            for (ServerConnection serverConnection : simConfig.getServerConnections()) {
                serverConnection.addTmDumpPacket(confirmationPacket);
            }
            
            dataStream.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static CCSDSPacket buildLosTransmittedRecordingPacket(String transmittedRecordName) {
        CCSDSPacket packet = new CCSDSPacket(0, 2, 10, false);
        packet.appendUserDataBuffer(transmittedRecordName.getBytes());
        packet.appendUserDataBuffer(new byte[1]);

        return packet;
    }

    public void deleteLosDataFile(String filename) {
        losStore.deleteFile(filename);
        // add packet notifying that the file has been deleted
        CCSDSPacket confirmationPacket = buildLosDeletedRecordingPacket(filename);
        for (ServerConnection serverConnection : simConfig.getServerConnections()) {
            try {
                serverConnection.addTmDumpPacket(confirmationPacket);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    private static CCSDSPacket buildLosDeletedRecordingPacket(String deletedRecordName) {
        CCSDSPacket packet = new CCSDSPacket(0, 2, 11, false);
        packet.appendUserDataBuffer(deletedRecordName.getBytes());
        packet.appendUserDataBuffer(new byte[1]);

        return packet;
    }

    public SimWindow getSimWindow() {
        return simWindow;
    }

    public void setSimWindow(SimWindow simWindow) {
        this.simWindow = simWindow;
    }

    public void startTriggeringLos() {
        losStore.startTriggeringLos();
    }

    public void stopTriggeringLos() {
        losStore.stopTriggeringLos();
    }

    protected CCSDSPacket ackPacket(CCSDSPacket commandPacket, int stage, int result) {
        CCSDSPacket ackPacket = new CCSDSPacket(0, commandPacket.getPacketType(), 2000, false);
        ackPacket.setApid(101);
        int batNum = commandPacket.getPacketId();

        ByteBuffer bb = ByteBuffer.allocate(10);

        bb.putInt(0, batNum);
        bb.putInt(4, commandPacket.getSeq());
        bb.put(8, (byte) stage);
        bb.put(9, (byte) result);

        ackPacket.appendUserDataBuffer(bb.array());

        return ackPacket;

    }
}
