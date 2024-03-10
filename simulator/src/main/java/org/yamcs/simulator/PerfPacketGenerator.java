package org.yamcs.simulator;

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
    double changePercent;

    volatile boolean paused;
    final ColSimulator simulator;
    private static final Logger log = LoggerFactory.getLogger(PerfPacketGenerator.class);
    final public static int PERF_TEST_PACKET_ID = 1000; // the packet id of the packets used for performance testing
                                                        // start from here

    public PerfPacketGenerator(ColSimulator simulator, int numPackets, int packetSize, long interval,
            double changePercent) {
        this.simulator = simulator;
        this.numPackets = numPackets;
        this.packetSize = packetSize;
        this.interval = interval;
        this.changePercent = changePercent;
    }

    @Override
    protected void run() throws Exception {
        Random r = new Random();
        log.info("Starting performance data sending thread with {} packets of {} size spaced at {} ms intervals",
                numPackets, packetSize, interval);
        byte[][] pktData = new byte[numPackets][];

        for (int i = 0; i < numPackets; i++) {
            byte[] p = new byte[packetSize];
            r.nextBytes(p);
            pktData[i] = p;
        }

        int changeChunk = (int) (400 / changePercent);
        if (changeChunk < 4) {
            changeChunk = 4;
        }

        while (isRunning()) {
            if (!paused) {
                for (int i = 0; i < numPackets; i++) {
                    ColumbusCcsdsPacket packet = new ColumbusCcsdsPacket(ColSimulator.PERF_TEST_APID, packetSize,
                            PERF_TEST_PACKET_ID + i);
                    ByteBuffer bb = packet.getUserDataBuffer();
                    bb.put(pktData[i]);
                    for (int j = 0; j < packetSize - changeChunk; j += changeChunk) {
                        int offset = j + (changeChunk > 4 ? r.nextInt(changeChunk - 4) : 0);
                        bb.putInt(offset, r.nextInt());
                    }
                    packet.setTime(TimeEncoding.getWallclockTime());

                    simulator.transmitRealtimeTM(packet);
                }
                Thread.sleep(interval);
            } else {
                Thread.sleep(1000);
            }
        }
    }

    public void pause() {
        this.paused = true;
    }

    public void resume() {
        this.paused = false;
    }

}
