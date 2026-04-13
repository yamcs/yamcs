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
import org.yamcs.cfdp.pdu.CfdpHeader;
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

/**
 * PUS (Packet Utilisation Standard) simulator.
 *
 * APIDs are loaded from the MDB (jTYU.xml):
 *   APID 0   = Time
 *   APID 16  = FSW  (Flight Software)
 *   APID 32  = AOCS (Attitude Control)
 *   APID 48  = COM  (Communications)
 *   APID 64  = PRP  (Propulsion)
 *   APID 80  = SYS  (System)
 *   APID 96  = THM  (Thermal)
 *   APID 112 = EPS  (Electrical Power System)
 *
 * Supports services:
 *   ST[01] - request verification
 *   ST[03] - housekeeping
 *   ST[05] - event reporting (events loaded from MDB)
 *   ST[09] - time management
 *   ST[11] - time based schedule
 *   ST[17] - test (ping)
 */
public class PusSimulator extends AbstractSimulator {

    // -------------------------------------------------------------------------
    // APIDs from the MDB (jtyu_mdb.xml)
    // -------------------------------------------------------------------------
    private static final String MDB_SPEC = "/jtyu_mdb.xml";

    // Defaults match the current jtyu_mdb.xml, but we override them at runtime by parsing the MDB.
    private static final int DEFAULT_APID_TIME = 0;    // Time packets
    private static final int DEFAULT_APID_FSW  = 16;   // Flight Software
    private static final int DEFAULT_APID_AOCS = 32;   // Attitude & Orbit Control
    private static final int DEFAULT_APID_COM  = 48;   // Communications
    private static final int DEFAULT_APID_PRP  = 64;   // Propulsion
    private static final int DEFAULT_APID_SYS  = 80;   // System
    private static final int DEFAULT_APID_THM  = 96;   // Thermal
    private static final int DEFAULT_APID_EPS  = 112;  // Electrical Power System

    private int apidTime = DEFAULT_APID_TIME;
    private int apidFsw = DEFAULT_APID_FSW;
    private int apidAocs = DEFAULT_APID_AOCS;
    private int apidCom = DEFAULT_APID_COM;
    private int apidPrp = DEFAULT_APID_PRP;
    private int apidSys = DEFAULT_APID_SYS;
    private int apidThm = DEFAULT_APID_THM;
    private int apidEps = DEFAULT_APID_EPS;

    // Main APID used for PUS service packets (FSW handles most PUS services)
    private int mainApid = DEFAULT_APID_FSW;

    // -------------------------------------------------------------------------
    // PUS Service and Subtype constants
    // -------------------------------------------------------------------------
    static final int PUS_TYPE_ACK   = 1;
    static final int PUS_TYPE_HK    = 3;
    static final int PUS_TYPE_EVENT = 5;

    static final int PUS_SUBTYPE_ACK_ACCEPTANCE  = 1;
    static final int PUS_SUBTYPE_NACK_ACCEPTANCE = 2;
    static final int PUS_SUBTYPE_ACK_START       = 3;
    static final int PUS_SUBTYPE_NACK_START      = 4;
    static final int PUS_SUBTYPE_ACK_COMPLETION  = 7;
    static final int PUS_SUBTYPE_NACK_COMPLETION = 8;

    static final int START_FAILURE_INVALID_VOLTAGE_NUM = 100;

    private static final Logger log = LoggerFactory.getLogger(PusSimulator.class);

    final Random random = new Random();

    ScheduledThreadPoolExecutor executor;
    TcpTmTcLink tmLink;

    // Data handlers (CSV replay)
    FlightDataHandler flightDataHandler;
    DHSHandler dhsHandler;
    PowerHandler powerDataHandler;
    RCSHandler rcsHandler;
    EpsLvpduHandler epslvpduHandler;

    // CFDP
    CfdpReceiver cfdpReceiver;

    // PUS Service handlers
    Pus5Service pus5Service;
    Pus11Service pus11Service;
    Pus17Service pus17Service;

    // TC queue — TCs are accepted immediately, executed on the next scheduler tick
    protected BlockingQueue<PusTcPacket> pendingCommands = new ArrayBlockingQueue<>(100);

    public PusSimulator(File dataDir) {
        // Data handlers
        powerDataHandler = new PowerHandler();
        rcsHandler = new RCSHandler();
        epslvpduHandler = new EpsLvpduHandler();
        flightDataHandler = new FlightDataHandler();
        dhsHandler = new DHSHandler();

        // CFDP
        cfdpReceiver = new CfdpReceiver(this, dataDir);

        // PUS services
        pus5Service  = new Pus5Service(this);
        pus11Service = new Pus11Service(this);
        pus17Service = new Pus17Service(this);
    }

    @Override
    protected void doStart() {
        loadApidsFromMdb();
        executor = new ScheduledThreadPoolExecutor(1);

        // ST[09] - Time packet every 4 seconds
        executor.scheduleAtFixedRate(
            () -> sendTimePacket(), 0, 4, TimeUnit.SECONDS);

        // ST[03] - Flight data HK every 200ms
        executor.scheduleAtFixedRate(
            () -> sendFlightPacket(), 0, 200, TimeUnit.MILLISECONDS);

        // ST[03] - Housekeeping TM every 1 second
        executor.scheduleAtFixedRate(
            () -> sendHkTm(), 0, 1000, TimeUnit.MILLISECONDS);

        // TC execution loop every 200ms
        executor.scheduleAtFixedRate(
            () -> executePendingCommands(), 0, 200, TimeUnit.MILLISECONDS);

        // Start PUS services (they set up their own schedules)
        pus5Service.start();   // loads events from MDB, schedules event sending
        pus11Service.start();  // time-based scheduling service
    }

    // -------------------------------------------------------------------------
    // TM Sending
    // -------------------------------------------------------------------------

    /**
     * ST[03,25] - Flight data housekeeping report.
     * Uses APID_AOCS since flight/attitude data belongs to AOCS subsystem.
     */
    private void sendFlightPacket() {
        PusTmPacket packet = newHousekeepingPacket(apidAocs, "AOCS", 0, Integer.BYTES + flightDataHandler.dataSize());
        ByteBuffer buffer = packet.getUserDataBuffer();
        buffer.putInt(0); // structure ID 0 = flight data (XTCE defines structure_id as 32-bit)
        flightDataHandler.fillPacket(buffer.slice());
        padWithZeros(buffer);
        transmitRealtimeTM(packet);
    }

    /**
     * ST[03,25] - Housekeeping reports for all subsystems.
     * Each subsystem uses its correct APID from the MDB.
     */
    private void sendHkTm() {
        try {
            // EPS subsystem - power data (APID_EPS = 112)
            PusTmPacket packet = newHousekeepingPacket(apidEps, "EPS", 1, Integer.BYTES + powerDataHandler.dataSize());
            ByteBuffer buffer = packet.getUserDataBuffer();
            buffer.putInt(1); // structure ID 1 = power
            powerDataHandler.fillPacket(buffer.slice());
            padWithZeros(buffer);
            transmitRealtimeTM(packet);

            // FSW subsystem - DHS data (APID_FSW = 16)
            packet = newHousekeepingPacket(apidFsw, "FSW", 2, Integer.BYTES + dhsHandler.dataSize());
            buffer = packet.getUserDataBuffer();
            buffer.putInt(2); // structure ID 2 = DHS
            dhsHandler.fillPacket(buffer.slice());
            padWithZeros(buffer);
            transmitRealtimeTM(packet);

            // PRP subsystem - RCS/propulsion data (APID_PRP = 64)
            packet = newHousekeepingPacket(apidPrp, "PRP", 3, Integer.BYTES + rcsHandler.dataSize());
            buffer = packet.getUserDataBuffer();
            buffer.putInt(3); // structure ID 3 = RCS
            rcsHandler.fillPacket(buffer.slice());
            padWithZeros(buffer);
            transmitRealtimeTM(packet);

            // EPS subsystem - LVPDU data (APID_EPS = 112)
            packet = newHousekeepingPacket(apidEps, "EPS", 4, Integer.BYTES + epslvpduHandler.dataSize());
            buffer = packet.getUserDataBuffer();
            buffer.putInt(4); // structure ID 4 = EPS LVPDU
            epslvpduHandler.fillPacket(buffer.slice());
            padWithZeros(buffer);
            transmitRealtimeTM(packet);

        } catch (Exception e) {
            log.error("Error sending HK TM", e);
        }
    }

    /**
     * ST[09] - Time packet. Uses APID_TIME = 0.
     * Sent immediately (bypasses queue) to ensure accurate timestamps.
     */
    private void sendTimePacket() {
        tmLink.sendImmediate(new PusTmTimePacket());
    }

    /**
     * Transmits a TM packet — fills checksum then sends via TCP link.
     */
    void transmitRealtimeTM(PusTmPacket packet) {
        packet.fillChecksum();
        tmLink.sendPacket(packet.getBytes());
    }

    private PusTmPacket newHousekeepingPacket(int apid, String apidLabel, int structureId, int minimumUserDataLength) {
        int expectedBits = MdbLoader.loadHousekeepingPacketSize(MDB_SPEC, apidLabel, structureId);
        int userDataLength = minimumUserDataLength;
        if (expectedBits > 0) {
            // XTCE container size corresponds to the housekeeping payload for this report variant.
            int expectedUserDataLength = Math.max(0, (expectedBits + 7) / 8);
            userDataLength = Math.max(userDataLength, expectedUserDataLength);
        }
        return new PusTmPacket(apid, userDataLength, PUS_TYPE_HK, 25);
    }

    private void padWithZeros(ByteBuffer buffer) {
        while (buffer.hasRemaining()) {
            buffer.put((byte) 0);
        }
    }

    // -------------------------------------------------------------------------
    // TC Handling
    // -------------------------------------------------------------------------

    /**
     * Called by TcpTmTcLink when a TC arrives from YAMCS.
     * Immediately sends ST[01,1] acceptance ACK, then queues for execution.
     */
    @Override
    public void processTc(SimulatorCcsdsPacket tc) {
        PusTcPacket pustc = (PusTcPacket) tc;

        if (tc.getAPID() == CfdpCcsdsPacket.APID) {
            // CFDP packets handled separately
            // cfdpReceiver.processCfdp(tc.getUserDataBuffer());
        } else {
            // Immediately send acceptance ACK ST[01,1]
            transmitRealtimeTM(ack(pustc, PUS_SUBTYPE_ACK_ACCEPTANCE));

            // Queue for deferred execution
            try {
                pendingCommands.put(pustc);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Drains the TC queue and dispatches each command to the right service.
     * Runs every 200ms on the executor thread.
     */
    private void executePendingCommands() {
        PusTcPacket commandPacket;
        while ((commandPacket = pendingCommands.poll()) != null) {
            try {
                log.info("Executing PUS TC: type={} subtype={} (time: {})",
                    commandPacket.getType(), commandPacket.getSubtype(), PusTime.now());

                switch (commandPacket.getType()) {
                    case 5  -> pus5Service.executeTc(commandPacket);
                    case 11 -> pus11Service.executeTc(commandPacket);
                    case 17 -> pus17Service.executeTc(commandPacket);
                    case 25 -> {
                        switch (commandPacket.getSubtype()) {
                            case 1 -> switchBatteryOn(commandPacket);
                            case 2 -> switchBatteryOff(commandPacket);
                            default -> log.error("Unknown subtype {} for type 25",
                                commandPacket.getSubtype());
                        }
                    }
                    default -> log.warn("Unknown TC type {}", commandPacket.getType());
                }
            } catch (Exception e) {
                log.warn("Error executing TC", e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Battery commands (type 25) — kept in PusSimulator directly
    // -------------------------------------------------------------------------

    private void switchBatteryOn(PusTcPacket commandPacket) {
        int batNum = commandPacket.getUserDataBuffer().get(0);
        if (batNum < 1 || batNum > 3) {
            log.info("BATTERY ON {}: invalid number, sending NACK", batNum);
            transmitRealtimeTM(nack(commandPacket, PUS_SUBTYPE_NACK_START,
                START_FAILURE_INVALID_VOLTAGE_NUM));
            return;
        }
        if (batNum != 2) {
            log.info("BATTERY ON {}: sending ACK start", batNum);
            transmitRealtimeTM(ack(commandPacket, PUS_SUBTYPE_ACK_START));
        } else {
            log.info("BATTERY ON {}: skipping ACK start", batNum);
        }
        executor.schedule(() -> {
            if (batNum == 3) {
                int returnCode = random.nextInt(5);
                log.info("BATTERY ON {}: sending failure code={}", batNum, returnCode);
                transmitRealtimeTM(nack(commandPacket, PUS_SUBTYPE_NACK_COMPLETION, returnCode));
            } else {
                powerDataHandler.setBatteryOn(batNum);
                transmitRealtimeTM(ack(commandPacket, PUS_SUBTYPE_ACK_COMPLETION));
            }
        }, 1500, TimeUnit.MILLISECONDS);
    }

    private void switchBatteryOff(PusTcPacket commandPacket) {
        transmitRealtimeTM(ack(commandPacket, PUS_SUBTYPE_ACK_START));
        int batNum = commandPacket.getUserDataBuffer().get(0);
        log.info("BATTERY OFF {}", batNum);
        executor.schedule(() -> {
            powerDataHandler.setBatteryOff(batNum);
            transmitRealtimeTM(ack(commandPacket, PUS_SUBTYPE_ACK_COMPLETION));
        }, 500, TimeUnit.MILLISECONDS);
    }

    // -------------------------------------------------------------------------
    // ST[01] ACK/NACK helper packet builders
    // -------------------------------------------------------------------------

    /**
     * Builds a ST[01,subtype] acknowledgement packet.
     * User data = first 4 bytes of the TC (so YAMCS can correlate it).
     */
    protected PusTmPacket ack(PusTcPacket commandPacket, int subtype) {
        // The jTYU MDB defines successful_acceptance / successful_completion with:
        // - apid_tcack + seq_count_tcack (decoded from the first 4 bytes of the TC primary header)
        // - plus a 1-byte (8-bit) error code field that must be present even on success.
        PusTmPacket ackPacket = new PusTmPacket(mainApid, 5, PUS_TYPE_ACK, subtype);
        ByteBuffer bb = ackPacket.getUserDataBuffer();
        bb.put(commandPacket.getBytes(), 0, 4);
        bb.put((byte) 0); // acceptance_error_code / completion_error_code = 0 on success
        return ackPacket;
    }

    /**
     * Builds a ST[01,subtype] negative acknowledgement packet with error code.
     * User data = first 4 bytes of TC + 4-byte error code.
     */
    public PusTmPacket nack(PusTcPacket commandPacket, int subtype, int code) {
        PusTmPacket ackPacket = new PusTmPacket(mainApid, 8, PUS_TYPE_ACK, subtype);
        ByteBuffer bb = ackPacket.getUserDataBuffer();
        bb.put(commandPacket.getBytes(), 0, 4);
        bb.putInt(code);
        return ackPacket;
    }

    private void loadApidsFromMdb() {
        try {
            var apids = MdbLoader.loadApids(MDB_SPEC);
            if (apids.isEmpty()) {
                log.warn("No APIDs loaded from MDB {}, keeping defaults", MDB_SPEC);
                return;
            }
            apidTime = apids.getOrDefault("Time", apidTime);
            apidFsw = apids.getOrDefault("FSW", apidFsw);
            apidAocs = apids.getOrDefault("AOCS", apidAocs);
            apidCom = apids.getOrDefault("COM", apidCom);
            apidPrp = apids.getOrDefault("PRP", apidPrp);
            apidSys = apids.getOrDefault("SYS", apidSys);
            apidThm = apids.getOrDefault("THM", apidThm);
            apidEps = apids.getOrDefault("EPS", apidEps);

            mainApid = apidFsw;
            log.info("Loaded APIDs from MDB: {}", apids);
        } catch (Exception e) {
            log.warn("Failed to load APIDs from MDB {}, keeping defaults", MDB_SPEC, e);
        }
    }

    int getMainApid() {
        return mainApid;
    }

    // -------------------------------------------------------------------------
    // Link setters (called by SimulatorCommander)
    // -------------------------------------------------------------------------

    @Override
    protected void setTmLink(TcpTmTcLink tmLink) {
        this.tmLink = tmLink;
    }

    @Override
    protected void setTm2Link(TcpTmTcLink tm2Link) {
        // PUS simulator only uses the primary TM link
    }

    @Override
    protected void setLosLink(TcpTmTcLink losLink) {
        // PUS simulator does not support LOS recording
    }

    @Override
    protected void doStop() {
        executor.shutdownNow();
    }

    @Override
    public void transmitCfdp(CfdpPacket packet) {
        CfdpHeader header = packet.getHeader();
        int length = header.getLength() + packet.getDataFieldLength();
        CfdpCcsdsPacket pkt = new CfdpCcsdsPacket(length);
        ByteBuffer buffer = pkt.getUserDataBuffer();
        packet.writeToBuffer(buffer);
        tmLink.sendImmediate(pkt);
    }

    @Override
    public int maxTmDataSize() {
        return 1500;
    }
}
