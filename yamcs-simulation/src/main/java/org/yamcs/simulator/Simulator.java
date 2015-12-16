package org.yamcs.simulator;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.simulator.ui.SimWindow;


public class Simulator extends Thread {
    
    private static final Logger log = LoggerFactory.getLogger(Simulator.class);
    
    private int DEFAULT_MAX_LENGTH=65542;
    private int maxLength = DEFAULT_MAX_LENGTH;

    private SimulationConfiguration simConfig;
    private TelemetryLink tmLink;
    
    protected Queue<CCSDSPacket> pendingCommands = new ArrayBlockingQueue<>(100); //no more than 100 pending commands
    
    private boolean isLos = false;
    private LosStore losStore;
    
    private SimWindow simWindow;
    
    public Simulator(SimulationConfiguration simConfig) {
        this.simConfig = simConfig;
        tmLink = new TelemetryLink(this, simConfig);
        losStore = new LosStore(this, simConfig);
    }
    
    @Override
    public void run() {
        for(ServerConnection serverConnection : simConfig.getServerConnections()) {
            tmLink.yamcsServerConnect(serverConnection);

            //start the TC reception thread;
            new Thread(() -> {
                while(true) {
                    try {
                        // read commands
                        pendingCommands.addAll(readPackets(new DataInputStream(serverConnection.getTcSocket().getInputStream())));
                        Thread.sleep(4000);
                    } catch (IOException e) {
                        serverConnection.setConnected(false);
                        tmLink.yamcsServerConnect(serverConnection);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
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
    protected Queue<CCSDSPacket> readPackets(DataInputStream dIn) {
        Queue<CCSDSPacket> packetQueue = new ArrayBlockingQueue<>(1000);
        try {
            while(dIn.available() > 0) {
                //READ IN PACKET
                byte hdr[] = new byte[6];
                dIn.readFully(hdr);
                int remaining=((hdr[4]&0xFF)<<8)+(hdr[5]&0xFF)+1;
                if(remaining>maxLength-6) throw new IOException("Remaining packet length too big: "+remaining+" maximum allowed is "+(maxLength-6));
                byte[] b = new byte[6+remaining];
                System.arraycopy(hdr, 0, b, 0, 6);
                dIn.readFully(b, 6, remaining);
                CCSDSPacket packet = new CCSDSPacket(ByteBuffer.wrap(b));
                packetQueue.add(packet);
            }
            //System.out.println("Packets Stored & Sent = "+ losStored + " : "+  losSent);
        } catch(IOException e) {
            System.err.println("Connection lost : " + e);
        } catch(Exception e) {
            System.err.println("Error reading command " + e);
            e.printStackTrace();
        }
        return packetQueue;
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
        tmLink.tmTransmit(packet);
    }

    public void dumpLosDataFile(String filename) {
        // read data from los storage
        if(filename == null) {
            filename = losStore.getCurrentFileName();
        }
        DataInputStream dataStream = losStore.readLosFile(filename);
        if(dataStream == null)
            return;

        // extract packets from the data stream and put them in queue for downlink
        Queue<CCSDSPacket> qLosData = readPackets(dataStream);
        for(CCSDSPacket ccsdsPacket : qLosData) {
            for (ServerConnection serverConnection : simConfig.getServerConnections())
                serverConnection.setTmDumpPacket(ccsdsPacket);
        }

        // add packet notifying that the file has been downloaded entirely
        CCSDSPacket confirmationPacket = buildLosTransmittedRecordingPacket(filename);
        for(ServerConnection serverConnection : simConfig.getServerConnections())
            serverConnection.setTmDumpPacket(confirmationPacket);
    }
    
    private static CCSDSPacket buildLosTransmittedRecordingPacket(String transmittedRecordName) {
        CCSDSPacket packet = new CCSDSPacket(0, 2, 10);
        packet.appendUserDataBuffer(transmittedRecordName.getBytes());
        packet.appendUserDataBuffer(new byte[1]);

        return packet;
    }

    public void deleteLosDataFile(String filename) {
        losStore.deleteFile(filename);
        // add packet notifying that the file has been deleted
        CCSDSPacket confirmationPacket = buildLosDeletedRecordingPacket(filename);
        for(ServerConnection serverConnection : simConfig.getServerConnections())
            serverConnection.setTmDumpPacket(confirmationPacket);
    }
    
    private static CCSDSPacket buildLosDeletedRecordingPacket(String deletedRecordName) {
        CCSDSPacket packet = new CCSDSPacket(0, 2, 11);
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
}
