package org.yamcs.simulator.pus;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.simulator.AbstractSimulator;
import org.yamcs.simulator.cfdp.CfdpCcsdsPacket;
import org.yamcs.simulator.cfdp.CfdpReceiver;
import org.yamcs.simulator.DHSHandler;
import org.yamcs.simulator.EpsLvpduHandler;
import org.yamcs.simulator.FlightDataHandler;
import org.yamcs.simulator.PowerHandler;
import org.yamcs.simulator.RCSHandler;
import org.yamcs.simulator.SimulatorCcsdsPacket;
import org.yamcs.simulator.TcpTmTcLink;
import org.yamcs.simulator.UdpTmFrameLink;

/**
 * PUS (Packet Utilisation Standard) simulator.
 * 
 * Supports services:
 * <ul>
 * <li>ST[01] - request verification</li>
 * <li>ST[02] - device access</li>
 * <li>ST[03] - housekeeping</li>
 * <li>ST[05] - event reporting - TODO</li>
 * <li>ST[06] - memory management - TODO</li>
 * <li>ST[09] - time management - only sending the time packet</li>
 * <li>ST[11] - time based schedule</li>
 * <li>ST[12] - on-board monitoring - TODO</li>
 * <li>ST[13] - large packet transfer - TODO</li>
 * <li>ST[15] - on-board storage and retrieval - TODO</li>
 * <li>ST[17] - test</li>
 * <li>ST[23] - file management - TODO</li>
 * 
 * <li>
 * 
 * </ul>
 * 
 */
public class PusSimulator extends AbstractSimulator {
    static final int MAIN_APID = 1;

    static final int PUS_TYPE_ACK = 1;
    static final int PUS_TYPE_HK = 3;
    static final int PUS_TYPE_EVENT = 5;

    static final int PUS_SUBTYPE_ACK_ACCEPTANCE = 1;
    static final int PUS_SUBTYPE_NACK_ACCEPTANCE = 2;
    static final int PUS_SUBTYPE_ACK_START = 3;
    static final int PUS_SUBTYPE_NACK_START = 4;
    static final int PUS_SUBTYPE_ACK_COMPLETION = 7;
    static final int PUS_SUBTYPE_NACK_COMPLETION = 8;

    static final int START_FAILURE_INVALID_VOLTAGE_NUM = 100;
    private static final Logger log = LoggerFactory.getLogger(PusSimulator.class);

    final Random random = new Random();

    ScheduledThreadPoolExecutor executor;
    TcpTmTcLink tmLink;
    UdpTmFrameLink tmFrameLink;
    final PusTimeEncoding timeEncoding;

    FlightDataHandler flightDataHandler;
    DHSHandler dhsHandler;
    PowerHandler powerDataHandler;
    RCSHandler rcsHandler;
    EpsLvpduHandler epslvpduHandler;
    CfdpReceiver cfdpReceiver;
    Pus2Service pus2Service;
    Pus5Service pus5Service;
    Pus11Service pus11Service;
    Pus17Service pus17Service;

    protected BlockingQueue<PusTcPacket> pendingCommands = new ArrayBlockingQueue<>(100);

    public PusSimulator(File dataDir) {
        this(dataDir, PusTimeEncoding.DEFAULT);
    }

    public PusSimulator(File dataDir, PusTimeEncoding timeEncoding) {
        this.timeEncoding = timeEncoding;
        powerDataHandler = new PowerHandler();
        rcsHandler = new RCSHandler();
        epslvpduHandler = new EpsLvpduHandler();
        flightDataHandler = new FlightDataHandler();
        dhsHandler = new DHSHandler();
        cfdpReceiver = new CfdpReceiver(this, dataDir);
        pus2Service = new Pus2Service(this);
        pus5Service = new Pus5Service(this);
        pus11Service = new Pus11Service(this);
        pus17Service = new Pus17Service(this);
    }

    @Override
    protected void doStart() {
        executor = new ScheduledThreadPoolExecutor(1);
        executor.scheduleAtFixedRate(() -> sendTimePacket(), 0, 4, TimeUnit.SECONDS);

        executor.scheduleAtFixedRate(() -> sendFlightPacket(), 0, 200, TimeUnit.MILLISECONDS);
        executor.scheduleAtFixedRate(() -> sendHkTm(), 0, 1000, TimeUnit.MILLISECONDS);
        // executor.scheduleAtFixedRate(() -> sendCfdp(), 0, 1000, TimeUnit.MILLISECONDS);
        executor.scheduleAtFixedRate(() -> executePendingCommands(), 0, 200, TimeUnit.MILLISECONDS);

        pus5Service.start();
        pus11Service.start();
    }

    private void sendFlightPacket() {
        PusTmPacket packet = newPacket(PUS_TYPE_HK, 25, 4 + flightDataHandler.dataSize());
        ByteBuffer buffer = packet.getUserDataBuffer();
        buffer.putInt(0);
        flightDataHandler.fillPacket(buffer.slice());
        transmitRealtimeTM(packet);
    }

    private void sendHkTm() {
        try {
            PusTmPacket packet = newPacket(PUS_TYPE_HK, 25, 4 + powerDataHandler.dataSize());
            ByteBuffer buffer = packet.getUserDataBuffer();
            buffer.putInt(1);
            powerDataHandler.fillPacket(buffer.slice());
            transmitRealtimeTM(packet);

            packet = newPacket(PUS_TYPE_HK, 25, 4 + dhsHandler.dataSize());
            buffer = packet.getUserDataBuffer();
            buffer.putInt(2);
            dhsHandler.fillPacket(buffer.slice());
            transmitRealtimeTM(packet);

            packet = newPacket(PUS_TYPE_HK, 25, 4 + rcsHandler.dataSize());
            buffer = packet.getUserDataBuffer();
            buffer.putInt(3);
            rcsHandler.fillPacket(buffer.slice());
            transmitRealtimeTM(packet);

            packet = newPacket(PUS_TYPE_HK, 25, 4 + epslvpduHandler.dataSize());
            buffer = packet.getUserDataBuffer();
            buffer.putInt(4);
            epslvpduHandler.fillPacket(buffer.slice());
            transmitRealtimeTM(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void transmitRealtimeTM(PusTmPacket packet) {
        packet.fillChecksum();
        sendPacket(packet);
    }

    private void sendTimePacket() {
        sendPacket(new PusTmTimePacket(timeEncoding));
    }

    private void sendPacket(SimulatorCcsdsPacket packet) {
        if (tmLink != null) {
            tmLink.sendPacket(packet.getBytes());
        }
        if (tmFrameLink != null) {
            tmFrameLink.queuePacket(0, packet.getBytes());
        }
    }

    @Override
    protected void doStop() {
        executor.shutdownNow();
    }

    @Override
    public void transmitCfdp(CfdpPacket packet) {
        // TODO Auto-generated method stub

    }

    @Override
    public void processTc(SimulatorCcsdsPacket tc) {
        if (tc instanceof PusTcPacket pustc) {
            transmitRealtimeTM(ack(pustc, 1));
            try {
                pendingCommands.put(pustc);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            if (tc.getAPID() == CfdpCcsdsPacket.APID) {
                byte b0 = tc.getUserDataBuffer().get();
                if ((b0 & 0x08) == 0) { // towards receiver
                    cfdpReceiver.processCfdp(tc.getUserDataBuffer());
                } else {// towards sender
                    /*if (cfdpSender != null) {
                        cfdpSender.processCfdp(tc.getUserDataBuffer());
                    } else {*/
                        log.warn("Received CFDP packet for sender but have no sender");
                    // }
                }
                cfdpReceiver.processCfdp(tc.getUserDataBuffer());
            }
        }
    }

    private void switchBatteryOn(PusTcPacket commandPacket) {
        int batNum = commandPacket.getUserDataBuffer().get(0);
        if (batNum < 1 || batNum > 3) {
            log.info("CMD: BATERRY ON {}, sending NACK start", batNum);
            transmitRealtimeTM(nack(commandPacket, 4, START_FAILURE_INVALID_VOLTAGE_NUM));
            return;
        }
        if (batNum != 2) {
            log.info("CMD: BATERRY ON {} ACK start", batNum);
            transmitRealtimeTM(ack(commandPacket, 3));
        } else {
            log.info("CMD: BATERRY ON {}, skip ACK start", batNum);
        }

        executor.schedule(() -> {
            if (batNum == 3) {
                int returnCode = random.nextInt(5);
                log.info("CMD: BATERRY ON {}, sending failure completion with code {}", batNum, returnCode);
                transmitRealtimeTM(nack(commandPacket, 8, returnCode));
            } else {
                powerDataHandler.setBatteryOn(batNum);
                transmitRealtimeTM(ack(commandPacket, 7));
            }
        }, 1500, TimeUnit.MILLISECONDS);
    }

    private void switchBatteryOff(PusTcPacket commandPacket) {
        transmitRealtimeTM(ack(commandPacket, 3));
        int batNum = commandPacket.getUserDataBuffer().get(0);
        log.info("CMD: BATERRY OFF {}", batNum);
        executor.schedule(() -> {
            powerDataHandler.setBatteryOff(batNum);
            transmitRealtimeTM(ack(commandPacket, 7));
        }, 500, TimeUnit.MILLISECONDS);
    }

    private void executePendingCommands() {
        PusTcPacket commandPacket;
        while ((commandPacket = pendingCommands.poll()) != null) {
            try {
                log.info("Received PUS TC : {} (now: {})", commandPacket, timeEncoding.now());
                switch (commandPacket.getType()) {
                case 2 -> pus2Service.executeTc(commandPacket);
                case 5 -> pus5Service.executeTc(commandPacket);
                case 11 -> pus11Service.executeTc(commandPacket);
                case 17 -> pus17Service.executeTc(commandPacket);
                case 25 -> {
                    switch (commandPacket.getSubtype()) {
                    case 1 -> switchBatteryOn(commandPacket);
                    case 2 -> switchBatteryOff(commandPacket);
                    default -> log.error("Invalid command  subtype {}", commandPacket.getSubtype());
                    }
                }
                default -> log.warn("Unknown command type {}", commandPacket.getType());
                }
            } catch (Exception e) {
                log.warn("Error executing command", e);
            }
        }
    }

    PusTmPacket newPacket(int type, int subtype, int userDataLength) {
        return new PusTmPacket(MAIN_APID, userDataLength, type, subtype, timeEncoding);
    }

    protected PusTmPacket ack(PusTcPacket commandPacket, int subtype) {
        PusTmPacket ackPacket = newPacket(PUS_TYPE_ACK, subtype, 4);

        ByteBuffer bb = ackPacket.getUserDataBuffer();
        bb.put(commandPacket.getBytes(), 0, 4);
        return ackPacket;
    }

    public PusTmPacket nack(PusTcPacket commandPacket, int subtype, int code) {
        PusTmPacket ackPacket = newPacket(PUS_TYPE_ACK, subtype, 8);

        ByteBuffer bb = ackPacket.getUserDataBuffer();
        bb.put(commandPacket.getBytes(), 0, 4);
        bb.putInt(code);
        return ackPacket;
    }

    @Override
    protected void setTmLink(TcpTmTcLink tmLink) {
        this.tmLink = tmLink;
    }

    @Override
    protected void setTm2Link(TcpTmTcLink tm2Link) {
        // ignore only send packets on tmlink
    }

    @Override
    protected void setLosLink(TcpTmTcLink losLink) {
        // ignore only send packets on tmlink
    }

    public void setTmFrameLink(UdpTmFrameLink tmFrameLink) {
        this.tmFrameLink = tmFrameLink;
    }

    @Override
    public int maxTmDataSize() {
        return 1500;
    }
}
