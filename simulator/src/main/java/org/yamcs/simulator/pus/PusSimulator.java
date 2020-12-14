package org.yamcs.simulator.pus;

import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.simulator.AbstractSimulator;
import org.yamcs.simulator.CfdpReceiver;
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
 * Supports services:
 * <ul>
 * <li>ST[03] -housekeeping</li>
 * <li>ST[05] - event reporting - TODO</li>
 * <li>ST[06] - memory management - TODO</li>
 * <li>ST[09] - time management - only sending the time packet</li>
 * <li>ST[11] - time based schedule - TODO</li>
 * <li>ST[12] - on-board monitoring - TODO</li>
 * <li>ST[13] - large packet transfer - TODO</li>
 * <li>ST[15] - on-board storage and retrieval - TODO</li>
 * <li>ST[23] - file management - TODO</li>
 * 
 * <li>
 * 
 * </ul>
 * 
 * @author nm
 *
 */
public class PusSimulator extends AbstractSimulator {
    ScheduledThreadPoolExecutor executor;
    TcpTmTcLink tmLink;

    FlightDataHandler flightDataHandler;
    DHSHandler dhsHandler;
    PowerHandler powerDataHandler;
    RCSHandler rcsHandler;
    EpsLvpduHandler epslvpduHandler;
    CfdpReceiver cfdpReceiver;

    static final int MAIN_APID = 1;
    static final int PUS_TYPE_HK = 3;

    public PusSimulator() {
        powerDataHandler = new PowerHandler();
        rcsHandler = new RCSHandler();
        epslvpduHandler = new EpsLvpduHandler();
        flightDataHandler = new FlightDataHandler();
        dhsHandler = new DHSHandler();
        cfdpReceiver = new CfdpReceiver(this);
    }

    @Override
    protected void doStart() {
        executor = new ScheduledThreadPoolExecutor(1);
        executor.scheduleAtFixedRate(() -> sendTimePacket(), 0, 4, TimeUnit.SECONDS);

        executor.scheduleAtFixedRate(() -> sendFlightPacket(), 0, 200, TimeUnit.MILLISECONDS);
        executor.scheduleAtFixedRate(() -> sendHkTm(), 0, 1000, TimeUnit.MILLISECONDS);
        // executor.scheduleAtFixedRate(() -> sendCfdp(), 0, 1000, TimeUnit.MILLISECONDS);
        executor.scheduleAtFixedRate(() -> executePendingCommands(), 0, 200, TimeUnit.MILLISECONDS);
    }

    private void sendFlightPacket() {
        PusTmPacket packet = new PusTmPacket(MAIN_APID, 4 + flightDataHandler.dataSize(), PUS_TYPE_HK, 25);
        ByteBuffer buffer = packet.getUserDataBuffer();
        buffer.putInt(0);
        flightDataHandler.fillPacket(buffer.slice());
        transmitRealtimeTM(packet);
    }

    private void sendHkTm() {
        try {
            PusTmPacket packet = new PusTmPacket(MAIN_APID, 4 + powerDataHandler.dataSize(), PUS_TYPE_HK, 25);
            ByteBuffer buffer = packet.getUserDataBuffer();
            buffer.putInt(1);
            powerDataHandler.fillPacket(buffer.slice());
            transmitRealtimeTM(packet);

            packet = new PusTmPacket(MAIN_APID, 4 + dhsHandler.dataSize(), PUS_TYPE_HK, 25);
            buffer = packet.getUserDataBuffer();
            buffer.putInt(2);
            dhsHandler.fillPacket(buffer.slice());
            transmitRealtimeTM(packet);

            packet = new PusTmPacket(MAIN_APID, 4 + rcsHandler.dataSize(), PUS_TYPE_HK, 25);
            buffer = packet.getUserDataBuffer();
            buffer.putInt(3);
            rcsHandler.fillPacket(buffer.slice());
            transmitRealtimeTM(packet);

            packet = new PusTmPacket(MAIN_APID, 4 + epslvpduHandler.dataSize(), PUS_TYPE_HK, 25);
            buffer = packet.getUserDataBuffer();
            buffer.putInt(4);
            epslvpduHandler.fillPacket(buffer.slice());
            transmitRealtimeTM(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void transmitRealtimeTM(PusTmPacket packet) {
        packet.fillChecksum();
        tmLink.sendPacket(packet.getBytes());
    }

    private void sendTimePacket() {
        tmLink.sendImmediate(new PusTmTimePacket());
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
    protected void processTc(SimulatorCcsdsPacket tc) {
        // TODO Auto-generated method stub

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

    private void executePendingCommands() {
        // TODO Auto-generated method stub
    }

}
