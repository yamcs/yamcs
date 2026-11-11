package org.yamcs.examples.hires;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.yamcs.utils.TimeEncoding;

/**
 * A minimal simulator that generates packets with sub-millisecond (nanosecond)
 * precision timestamps. This demonstrates the YAMCS hires gentime pipeline.
 *
 * <p>
 * Packet format (big-endian):
 * <table>
 * <tr><th>Offset</th><th>Size</th><th>Field</th></tr>
 * <tr><td>0</td><td>2</td><td>Packet length (excludes these 2 bytes)</td></tr>
 * <tr><td>2</td><td>8</td><td>Generation time: milliseconds (TAI epoch)</td></tr>
 * <tr><td>10</td><td>4</td><td>Generation time: sub-ms picoseconds (0–999,999,999)</td></tr>
 * <tr><td>14</td><td>4</td><td>Sequence count</td></tr>
 * <tr><td>18</td><td>4</td><td>SineWave (float)</td></tr>
 * <tr><td>22</td><td>4</td><td>Ramp (float)</td></tr>
 * <tr><td>26</td><td>4</td><td>Random (float)</td></tr>
 * <tr><td>30</td><td>4</td><td>Counter (uint32)</td></tr>
 * </table>
 * Total packet size: 34 bytes (2 length + 32 payload).
 */
public class HiresSimulator {

    private static final Logger log = Logger.getLogger(HiresSimulator.class.getName());
    private static final int HEADER_SIZE = 18; // length(2) + millis(8) + picos(4) + seqcount(4)
    private static final int PARAM_DATA_SIZE = 16; // 4 floats/ints
    private static final int PAYLOAD_SIZE = HEADER_SIZE - 2 + PARAM_DATA_SIZE; // exclude length field itself

    private final int port;
    private volatile boolean running = true;

    public HiresSimulator(int port) {
        this.port = port;
    }

    public void run() {
        while (running) {
            log.info("Waiting for YAMCS connection on port " + port);
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                try (Socket socket = serverSocket.accept()) {
                    log.info("YAMCS connected from " + socket.getRemoteSocketAddress());
                    streamPackets(socket.getOutputStream());
                }
            } catch (IOException e) {
                if (running) {
                    log.log(Level.WARNING, "Connection error, retrying", e);
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    private void streamPackets(OutputStream out) throws IOException {
        int seqCount = 0;
        long startNanos = System.nanoTime();

        while (running) {
            // Get current YAMCS time (TAI millis) and compute sub-ms from nanoTime
            long millis = TimeEncoding.getWallclockTime();
            long elapsedNanos = System.nanoTime() - startNanos;
            // Use the nanosecond remainder within the millisecond
            int subMillisNanos = (int) (elapsedNanos % 1_000_000);
            // Convert nanoseconds to picoseconds (1 ns = 1000 ps)
            int picos = subMillisNanos * 1000;

            // Generate parameter values
            double t = seqCount * 0.1;
            float sineWave = (float) Math.sin(t);
            float ramp = (float) (seqCount % 100) / 100.0f;
            float random = (float) Math.random();
            int counter = seqCount;

            // Build packet
            ByteBuffer bb = ByteBuffer.allocate(2 + PAYLOAD_SIZE);
            bb.putShort((short) PAYLOAD_SIZE);  // packet length (excludes length field)
            bb.putLong(millis);                  // TAI milliseconds
            bb.putInt(picos);                    // sub-ms picoseconds
            bb.putInt(seqCount);                 // sequence count
            bb.putFloat(sineWave);               // parameter data
            bb.putFloat(ramp);
            bb.putFloat(random);
            bb.putInt(counter);

            out.write(bb.array());
            out.flush();

            seqCount++;

            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public static void main(String[] args) {
        TimeEncoding.setUp();

        int port = 10015;
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            }
        }

        HiresSimulator sim = new HiresSimulator(port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> sim.running = false));

        sim.run();
    }
}
