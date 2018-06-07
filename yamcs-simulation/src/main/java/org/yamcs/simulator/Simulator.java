package org.yamcs.simulator;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.simulator.ui.SimWindow;

public abstract class Simulator extends Thread {

    protected BlockingQueue<CCSDSPacket> pendingCommands = new ArrayBlockingQueue<>(100); // no more than 100 pending
    // commands

    private int DEFAULT_MAX_LENGTH = 65542;
    private int maxTcPacketLength = DEFAULT_MAX_LENGTH;
    private int PERF_TEST_PACKET_ID = 10000;

    private SimulationConfiguration simConfig;
    private TelemetryLink tmLink;

    private boolean isLos = false;
    private LosStore losStore;

    private SimWindow simWindow;

    private static final Logger log = LoggerFactory.getLogger(Simulator.class);

    public Simulator(SimulationConfiguration simConfig) {
        this.simConfig = simConfig;
        tmLink = new TelemetryLink(simConfig);
        losStore = new LosStore(this, simConfig);
    }

    protected void startPerfTestThread() {
        if(simConfig.getPerfTestNumPackets() > 0) {
            new Thread(() -> {
                try {
                   
                    sendPerfPackets();
                } catch (InterruptedException e) {
                    return;
                }
            }).start();
        }
    }
    
    
    //performance testing
    private void sendPerfPackets() throws InterruptedException {
        Random r = new Random();
        int packetNum = simConfig.getPerfTestNumPackets();
        int packetSize = simConfig.getPerfTestPacketSize();
        long interval = simConfig.getPerfTestPacketInterval();
        log.info("Starting performance data sending thread with {} packets of {} size spaced at {} ms intervals",  
                packetNum, packetSize, interval);
        while (true) {
            for(int i=0; i<packetNum; i++) {
                CCSDSPacket packet = new CCSDSPacket(packetSize, PERF_TEST_PACKET_ID+i);
                ByteBuffer bb = packet.getUserDataBuffer();
                while(bb.remaining()>4) {
                    bb.putInt(r.nextInt());
                }
                transmitTM(packet);
                Thread.sleep(interval);
            }
        }
    }
    protected void startConnectionThreads() {
        for (ServerConnection serverConnection : simConfig.getServerConnections()) {
            serverConnection.setSimulator(this);
            // start the TC reception thread;
            new Thread(() -> {
                while (true) {
                    try {
                        if (!serverConnection.isTcConnected()) {
                            serverConnection.connectTc();
                        }
                        // read commands
                        CCSDSPacket packet = readPacket(serverConnection.getTcInputStream());
                        if (packet != null)
                            pendingCommands.put(packet);

                    } catch (IOException e) {
                        serverConnection.setTcConnected(false);
                    } catch (InterruptedException e) {
                        log.warn("Read packets interrupted.", e);
                        Thread.currentThread().interrupt();
                    }
                }
            }).start();

            // start the TM transmission thread
            log.debug("Start TM thread");
            (new Thread(() -> serverConnection.packetSendThread())).start();
            (new Thread(() -> serverConnection.losSendThread())).start();
        }
    }
    protected CCSDSPacket readPacket(DataInputStream dIn) throws IOException {
        byte hdr[] = new byte[6];
        dIn.readFully(hdr);
        int remaining = ((hdr[4] & 0xFF) << 8) + (hdr[5] & 0xFF) + 1;
        if (remaining > maxTcPacketLength - 6)
            throw new IOException(
                    "Remaining packet length too big: " + remaining + " maximum allowed is " + (maxTcPacketLength - 6));
        byte[] b = new byte[6 + remaining];
        System.arraycopy(hdr, 0, b, 0, 6);
        dIn.readFully(b, 6, remaining);
        CCSDSPacket packet = new CCSDSPacket(ByteBuffer.wrap(b));
        tmLink.tmTransmit(ackPacket(packet, 0, 0));
        return packet;
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
