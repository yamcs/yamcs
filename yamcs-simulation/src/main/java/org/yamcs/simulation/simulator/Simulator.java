package org.yamcs.simulation.simulator;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.LogManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Simulator extends Thread {

    private static final Logger log = LoggerFactory.getLogger(Simulator.class);

    // no more than 100 pending commands
    protected BlockingQueue<CCSDSPacket> pendingCommands = new ArrayBlockingQueue<>(100);

    private int DEFAULT_MAX_LENGTH = 65542;
    private int maxLength = DEFAULT_MAX_LENGTH;

    private TelemetryLink tmLink;

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

    public Simulator(File dataDir, int tmPort, int tcPort, int losPort) {
        tmLink = new TelemetryLink(this, tmPort, tcPort, losPort);
        losRecorder = new LosRecorder(dataDir);

        powerDataHandler = new PowerHandler();
        rcsHandler = new RCSHandler();
        epslvpduHandler = new EpsLvpduHandler();
        flightDataHandler = new FlightDataHandler();
        dhsHandler = new DHSHandler();
    }

    @Override
    public void run() {
        tmLink.yamcsServerConnect();

        // start the TC reception thread;
        new Thread(() -> {
            while (true) {
                try {
                    // read commands
                    CCSDSPacket packet = readPacket(
                            new DataInputStream(tmLink.getTcSocket().getInputStream()));
                    if (packet != null) {
                        pendingCommands.put(packet);
                    }

                } catch (IOException e) {
                    tmLink.setConnected(false);
                    tmLink.yamcsServerConnect();
                } catch (InterruptedException e) {
                    log.warn("Read packets interrupted.", e);
                    Thread.currentThread().interrupt();
                }
            }
        }).start();

        // start the TM transmission thread
        (new Thread(() -> tmLink.packetSend())).start();

        new Thread(() -> {
            while (true) {
                try {
                    executePendingCommands();
                } catch (InterruptedException e) {
                    log.warn("Execute pending commands interrupted.", e);
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
        CCSDSPacket packet = null;
        try {
            for (int i = 0;;) {
                CCSDSPacket flightpacket = new CCSDSPacket(60, 33);
                flightDataHandler.fillPacket(flightpacket);
                transmitTM(flightpacket);

                if (i < 30) {
                    ++i;
                } else {
                    if (waitToEngage == 2 || engaged) {
                        engaged = true;
                        // unengaged = false;
                        CCSDSPacket powerpacket = new CCSDSPacket(16, 1);

                        powerDataHandler.fillPacket(powerpacket);
                        if (batteryCommand.batteryOn) {
                            if (!exeTransmitted) {
                                CCSDSPacket exeCompPacket = new CCSDSPacket(3, 2, 8);
                                if (1 <= batteryCommand.batteryNumber && batteryCommand.batteryNumber <= 3) {
                                    ByteBuffer buffer = packet.getUserDataBuffer();
                                    buffer.position(batteryCommand.batteryNumber - 1);
                                    buffer.put((byte) 1);
                                }
                                transmitTM(exeCompPacket);
                                exeTransmitted = true;
                            }
                        } else {
                            powerDataHandler.setBattOneOff(powerpacket);
                            if (!exeTransmitted) {
                                CCSDSPacket exeCompPacket = new CCSDSPacket(3, 2, 8);
                                if (1 <= batteryCommand.batteryNumber && batteryCommand.batteryNumber <= 3) {
                                    ByteBuffer buffer = packet.getUserDataBuffer();
                                    buffer.position(batteryCommand.batteryNumber - 1);
                                    buffer.put((byte) 0);
                                }
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

                    packet = new CCSDSPacket(9, 2);
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

                    i = 0;
                }
                Thread.sleep(4000 / 20);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
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
            if (remaining > maxLength - 6) {
                throw new IOException(
                        "Remaining packet length too big: " + remaining + " maximum allowed is " + (maxLength - 6));
            }
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
        tmLink.tmTransmit(packet);
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
                CCSDSPacket packet = readPacket(dataStream);
                if (packet != null) {
                    tmLink.addTmDumpPacket(packet);
                }
            }

            // add packet notifying that the file has been downloaded entirely
            CCSDSPacket confirmationPacket = buildLosTransmittedRecordingPacket(filename);
            tmLink.addTmDumpPacket(confirmationPacket);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
        try {
            tmLink.addTmDumpPacket(confirmationPacket);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

    public static void main(String[] args) throws IOException {
        configureLogging();

        log.info("Yamcs Demo Simulator");

        int tmPort = 10015;
        int tcPort = 10025;
        int dumpPort = 10115;
        File dataDir = new File("losData");
        dataDir.mkdirs();
        Simulator simulator = new Simulator(dataDir, tmPort, tcPort, dumpPort);
        simulator.start();

        int telnetPort = 10023;
        TelnetServer telnetServer = new TelnetServer(simulator);
        telnetServer.setPort(telnetPort);
        telnetServer.startAsync();
    }

    private static void configureLogging() {
        try {
            LogManager logManager = LogManager.getLogManager();
            try (InputStream in = Simulator.class.getResourceAsStream("/simulator-logging.properties")) {
                logManager.readConfiguration(in);
            }
        } catch (IOException e) {
            System.err.println("Failed to set up logging configuration: " + e.getMessage());
        }
    }
}
