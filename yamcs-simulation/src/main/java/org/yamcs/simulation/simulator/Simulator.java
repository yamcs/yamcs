package org.yamcs.simulation.simulator;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

public class Simulator extends AbstractExecutionThreadService {

    private static final Logger log = LoggerFactory.getLogger(Simulator.class);

    // no more than 100 pending commands
    protected BlockingQueue<CCSDSPacket> pendingCommands = new ArrayBlockingQueue<>(100);

    static int DEFAULT_MAX_LENGTH = 65542;
    int maxLength = DEFAULT_MAX_LENGTH;
    private TmTcLink tmLink;
    private TmTcLink losLink;

    private boolean los;
    private Date lastLosStart;
    private Date lastLosStop;
    private LosRecorder losRecorder;

    FlightDataHandler flightDataHandler;
    DHSHandler dhsHandler;
    PowerHandler powerDataHandler;
    RCSHandler rcsHandler;
    EpsLvpduHandler epslvpduHandler;

    private boolean engageHoldOneCycle = false;
    private boolean unengageHoldOneCycle = false;
    private int waitToEngage;
    private int waitToUnengage;
    private boolean engaged = false;
    private boolean unengaged = true;
    private boolean exeTransmitted = true;

    private BatteryCommand batteryCommand;
    int tmCycle = 0;     
    
    public Simulator(File dataDir, int tmPort, int tcPort, int losPort) {
        losRecorder = new LosRecorder(dataDir);
        powerDataHandler = new PowerHandler();
        rcsHandler = new RCSHandler();
        epslvpduHandler = new EpsLvpduHandler();
        flightDataHandler = new FlightDataHandler();
        dhsHandler = new DHSHandler();
    }

    @Override
    public void run() {
        while (isRunning()) {
            try {
                while(!pendingCommands.isEmpty()) { 
                    executePendingCommands();
                }
            } catch (InterruptedException e) {
                log.warn("Execute pending commands interrupted.", e);
                Thread.currentThread().interrupt();
            }

            try {
                sendTm();
                Thread.sleep(4000 / 20);
            } catch (InterruptedException e) {
                log.warn("Send TM interrupted.", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * this runs in a separate thread but pushes commands to the main TM thread
     */

    public LosRecorder getLosDataRecorder() {
        return losRecorder;
    }

    public boolean isLOS() {
        return los;
    }

    public Date getLastLosStart() {
        return lastLosStart;
    }

    public Date getLastLosStop() {
        return lastLosStop;
    }

    public void setAOS() {
        if (los) {
            los = false;
            lastLosStop = new Date();
            losRecorder.stopRecording();
        }
    }

    public void setLOS() {
        if (!los) {
            los = true;
            lastLosStart = new Date();
            losRecorder.startRecording(lastLosStart);
        }
    }

    protected void transmitTM(CCSDSPacket packet) {
        packet.fillChecksum();
        if(isLOS()) {
           losRecorder.record(packet);
        } else {
            tmLink.sendPacket(packet);
        }
    }

    public void dumpLosDataFile(String filename) {
        // read data from los storage
        if (filename == null) {
            filename = losRecorder.getCurrentRecordingName();
            if (filename == null) {
                return;
            }
        }

        try (DataInputStream dataStream = new DataInputStream(losRecorder.getInputStream(filename))) {
            while (dataStream.available() > 0) {
                CCSDSPacket packet = readLosPacket(dataStream);
                if (packet != null) {
                    losLink.sendPacket(packet);
                }
            }

            // add packet notifying that the file has been downloaded entirely
            CCSDSPacket confirmationPacket = buildLosTransmittedRecordingPacket(filename);
            tmLink.sendPacket(confirmationPacket);
        } catch (IOException e) {
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
        losRecorder.deleteDump(filename);
        // add packet notifying that the file has been deleted
        CCSDSPacket confirmationPacket = buildLosDeletedRecordingPacket(filename);
        tmLink.sendPacket(confirmationPacket);
    }

    private static CCSDSPacket buildLosDeletedRecordingPacket(String deletedRecordName) {
        CCSDSPacket packet = new CCSDSPacket(0, 2, 11, false);
        packet.appendUserDataBuffer(deletedRecordName.getBytes());
        packet.appendUserDataBuffer(new byte[1]);
        return packet;
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

    private void sendTm() {
        CCSDSPacket flightpacket = new CCSDSPacket(60, 33);
        flightDataHandler.fillPacket(flightpacket);
        transmitTM(flightpacket);
        
        if (tmCycle < 30) {
            ++tmCycle;
        } else {
            if (waitToEngage == 2 || engaged) {
                engaged = true;
                // unengaged = false;
                CCSDSPacket powerpacket = new CCSDSPacket(16, 1);

                powerDataHandler.fillPacket(powerpacket);
                if (batteryCommand.batteryOn) {
                    if (!exeTransmitted) {
                        CCSDSPacket exeCompPacket = new CCSDSPacket(3, 2, 8);
                        transmitTM(exeCompPacket);
                        exeTransmitted = true;
                    }
                } else {
                    powerDataHandler.setBattOneOff(powerpacket);
                    if (!exeTransmitted) {
                        CCSDSPacket exeCompPacket = new CCSDSPacket(3, 2, 8);
                        transmitTM(exeCompPacket);
                        exeTransmitted = true;
                    }
                }

                transmitTM(powerpacket);

                engageHoldOneCycle = false;
                waitToEngage = 0;

            } else if (waitToUnengage == 2 || unengaged) {
                CCSDSPacket powerpacket = new CCSDSPacket(16, 1);
                powerDataHandler.fillPacket(powerpacket);
                transmitTM(powerpacket);
                unengaged = true;
                // engaged = false;

                unengageHoldOneCycle = false;
                waitToUnengage = 0;
            }

            CCSDSPacket packet = new CCSDSPacket(9, 2);
            dhsHandler.fillPacket(packet);
            transmitTM(packet);

            packet = new CCSDSPacket(36, 3);
            rcsHandler.fillPacket(packet);
            transmitTM(packet);

            packet = new CCSDSPacket(6, 4);
            epslvpduHandler.fillPacket(packet);
            transmitTM(packet);

            if (engageHoldOneCycle) { // hold the command for 1 cycle after the command Ack received
                waitToEngage = waitToEngage + 1;
                log.debug("Value : {}", waitToEngage);
            }

            if (unengageHoldOneCycle) {
                waitToUnengage = waitToUnengage + 1;
            }

            tmCycle = 0;
        }
    }

    /**
     * runs in the main TM thread, executes commands from the queue (if any)
     */
    private void executePendingCommands() throws InterruptedException {
        CCSDSPacket commandPacket = pendingCommands.take();
        if (commandPacket.getPacketType() == 10) {
            log.info("BATT COMMAND: " + commandPacket.getPacketId());

            switch (commandPacket.getPacketId()) {
            case 1:
                switchBatteryOn(commandPacket);
                break;
            case 2:
                switchBatteryOff(commandPacket);
                break;
            case 5:
                listRecordings(commandPacket);
                break;
            case 6:
                dumpRecording(commandPacket);
                break;
            case 7:
                deleteRecording(commandPacket);
                break;
            default:
                log.error("Invalid command packet id: {}", commandPacket.getPacketId());
            }
        } else {
            log.warn("Unknown command type "+commandPacket.getPacketType());
        }
    }

    private void switchBatteryOn(CCSDSPacket commandPacket) {
        tmLink.ackPacketSend(ackPacket(commandPacket, 1, 0));
        commandPacket.setPacketId(1);
        int batNum = commandPacket.getUserDataBuffer().get(0);
        switch (batNum) {
        case 1:
            unengageHoldOneCycle = true;
            // engaged = false;
            exeTransmitted = false;
            batteryCommand = BatteryCommand.BATTERY1_ON;
            break;
        case 2:
            unengageHoldOneCycle = true;
            // engaged = false;
            exeTransmitted = false;
            batteryCommand = BatteryCommand.BATTERY2_ON;
            break;
        case 3:
            unengageHoldOneCycle = true;
            // engaged = false;
            exeTransmitted = false;
            batteryCommand = BatteryCommand.BATTERY3_ON;
        }
        tmLink.ackPacketSend(ackPacket(commandPacket, 2, 0));
    }

    private void switchBatteryOff(CCSDSPacket commandPacket) {
        tmLink.ackPacketSend(ackPacket(commandPacket, 1, 0));
        commandPacket.setPacketId(2);
        int batNum = commandPacket.getUserDataBuffer().get(0);
        ByteBuffer buffer;
        CCSDSPacket ackPacket;
        switch (batNum) {
        case 1:
            engageHoldOneCycle = true;
            exeTransmitted = false;
            batteryCommand = BatteryCommand.BATTERY1_OFF;
            ackPacket = new CCSDSPacket(1, 2, 7);
            buffer = ackPacket.getUserDataBuffer();
            buffer.position(0);
            buffer.put((byte) 1);
            break;
        case 2:
            engageHoldOneCycle = true;
            exeTransmitted = false;
            batteryCommand = BatteryCommand.BATTERY2_OFF;
            ackPacket = new CCSDSPacket(1, 2, 7);
            buffer = ackPacket.getUserDataBuffer();
            buffer.position(0);
            buffer.put((byte) 1);
            break;
        case 3:
            engageHoldOneCycle = true;
            exeTransmitted = false;
            batteryCommand = BatteryCommand.BATTERY3_OFF;
            ackPacket = new CCSDSPacket(1, 2, 7);
            buffer = ackPacket.getUserDataBuffer();
            buffer.position(0);
            buffer.put((byte) 1);
        }
        tmLink.ackPacketSend(ackPacket(commandPacket, 2, 0));
    }

    private void listRecordings(CCSDSPacket commandPacket) {
        tmLink.ackPacketSend(ackPacket(commandPacket, 1, 0));

        CCSDSPacket packet = new CCSDSPacket(0, 2, 9, false);
        String[] dumps = losRecorder.listRecordings();
        log.info("LOS dump count: {}", dumps.length);

        String joined = String.join(" ", dumps);
        packet.appendUserDataBuffer(joined.getBytes());
        packet.appendUserDataBuffer(new byte[1]); // terminate with \0

        transmitTM(packet);
        tmLink.ackPacketSend(ackPacket(commandPacket, 2, 0));
    }

    private void dumpRecording(CCSDSPacket commandPacket) {
        tmLink.ackPacketSend(ackPacket(commandPacket, 1, 0));
        byte[] fileNameArray = commandPacket.getUserDataBuffer().array();
        int indexStartOfString = 16;
        int indexEndOfString = indexStartOfString;
        for (int i = indexStartOfString; i < fileNameArray.length; i++) {
            if (fileNameArray[i] == 0) {
                indexEndOfString = i;
                break;
            }
        }
        String fileName1 = new String(fileNameArray, indexStartOfString, indexEndOfString - indexStartOfString);
        log.info("Command DUMP_RECORDING for file {}", fileName1);
        dumpLosDataFile(fileName1);
        tmLink.ackPacketSend(ackPacket(commandPacket, 2, 0));
    }

    private void deleteRecording(CCSDSPacket commandPacket) {
        tmLink.ackPacketSend(ackPacket(commandPacket, 1, 0));
        byte[] fileNameArray = commandPacket.getUserDataBuffer().array();
        String fileName = new String(fileNameArray, 16, fileNameArray.length - 22);
        log.info("Command DELETE_RECORDING for file {}", fileName);
        deleteLosDataFile(fileName);
        tmLink.ackPacketSend(ackPacket(commandPacket, 2, 0));
    }

    public void setTmLink(TmTcLink tmLink) {
        this.tmLink = tmLink;
    }

    public void processTc(CCSDSPacket tc) {
        tmLink.ackPacketSend(ackPacket(tc, 0, 0));
        try {
            pendingCommands.put(tc);
        } catch (InterruptedException e) {
           Thread.currentThread().interrupt();
        }
    }

    protected CCSDSPacket readLosPacket(DataInputStream dIn) {
        try {
            byte hdr[] = new byte[6];
            dIn.readFully(hdr);
            int remaining = ((hdr[4] & 0xFF) << 8) + (hdr[5] & 0xFF) + 1;
            if (remaining > maxLength - 6) {
                throw new IOException(
                        "Remaining packet length too big: " + remaining + " maximum allowed is " + (maxLength - 6));
            }
            byte[] b = new byte[6 + remaining];
            System.arraycopy(hdr, 0, b, 0, 6);
            dIn.readFully(b, 6, remaining);
            return new CCSDSPacket(ByteBuffer.wrap(b));
        } catch (Exception e) {
            log.error("Error reading LOS packet from file " + e.getMessage(), e);
        }
        return null;
    }

    public void setLosLink(TmTcLink losLink) {
       this.losLink = losLink;
    }
}
