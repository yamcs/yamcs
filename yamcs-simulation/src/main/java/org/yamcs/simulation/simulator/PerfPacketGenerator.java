package org.yamcs.simulation.simulator;

import java.nio.ByteBuffer;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.utils.TimeEncoding;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

/**
 * Generates packets for performance testing
 * 
 * @author nm
 *
 */
public class PerfPacketGenerator extends AbstractExecutionThreadService {
    int numPackets;
    int packetSize;
    long interval;
    final Simulator simulator;
    private static final Logger log = LoggerFactory.getLogger(PerfPacketGenerator.class);
    final public static int PERF_TEST_PACKET_ID = 1000; // the packet id of the packets used for performance testing
                                                        // start from here

    public PerfPacketGenerator(Simulator simulator, int numPackets, int packetSize, long interval) {
        this.simulator = simulator;
        this.numPackets = numPackets;
        this.packetSize = packetSize;
        this.interval = interval;
    }

    @Override
    protected void run() throws Exception {
        Random r = new Random();
        log.info("Starting performance data sending thread with {} packets of {} size spaced at {} ms intervals",
                numPackets, packetSize, interval);
        CCSDSPacket[] packets = new CCSDSPacket[numPackets];

        for (int i = 0; i < numPackets; i++) {
            CCSDSPacket packet = new CCSDSPacket(packetSize, PERF_TEST_PACKET_ID + i);
            ByteBuffer bb = packet.getUserDataBuffer();
            while (bb.remaining() > 4) {
                bb.putInt(r.nextInt());
            }
            packets[i] = packet;
        }
        int numParamChanging = packetSize/40; //10% of parameters are changing with each packet
        
        while (isRunning()) {
            while (true) {
                for (int i = 0; i < numPackets; i++) {
                    CCSDSPacket packet = packets[i];
                    ByteBuffer bb = packet.getUserDataBuffer();
                    for (int j = 0; j < numParamChanging; j++) {
                        bb.putInt(r.nextInt(packetSize - 4), r.nextInt());
                    }
                    packet.setTime(TimeEncoding.getWallclockTime());
                    simulator.transmitRealtimeTM(packet);
                }
                Thread.sleep(interval);
            }
        }
    }

}
