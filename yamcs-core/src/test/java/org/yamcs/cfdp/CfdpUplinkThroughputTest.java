package org.yamcs.cfdp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.protobuf.TransferState;
import org.yamcs.utils.FileUtils;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;

/**
 * Measures the speed of generating File Data PDUs from a file on disk (no delay between PDUs).
 * <p>
 * Not run by default; run with {@code -Dcfdp.perf=true} (and optionally {@code -Dcfdp.perf.size=<MB>},
 * {@code -Dcfdp.perf.pduSize=<bytes>}).
 */
public class CfdpUplinkThroughputTest {
    static final String yamcsInstance = "cfdp-test-inst";

    @BeforeAll
    public static void beforeClass() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("cfdp.perf"), "performance test disabled");
        EventProducerFactory.setMockup(true);
        Path dataDir = Path.of(System.getProperty("java.io.tmpdir"), "yamcs-cfdp-data");
        FileUtils.deleteRecursivelyIfExists(dataDir);
        YConfiguration.setupTest("cfdp");
        YamcsServer.getServer().prepareStart();
        YamcsServer.getServer().start();
    }

    @AfterAll
    public static void afterClass() throws Exception {
        if (Boolean.getBoolean("cfdp.perf")) {
            YamcsServer.getServer().shutDown();
        }
    }

    @Test
    public void testThroughput() throws Exception {
        int sizeMb = Integer.getInteger("cfdp.perf.size", 200);
        int pduSize = Integer.getInteger("cfdp.perf.pduSize", 1024);
        byte[] data = new byte[sizeMb * 1024 * 1024];
        new Random(1).nextBytes(data);

        Path dir = Files.createTempDirectory("cfdp-perf");
        Path path = dir.resolve("1.data");
        try {
            long t0 = System.nanoTime();
            var src = OutgoingFileSource.createFile(path, data);
            long t1 = System.nanoTime();
            report("prepare (write + fsync)", data.length, t1 - t0);
            data = null; // only the file from now on

            var ydb = YarchDatabase.getInstance(yamcsInstance);
            long[] counters = new long[2];
            Stream out = new Stream(ydb, "perf_out", CfdpPacket.CFDP) {
                @Override
                public void doStart() {
                }

                @Override
                protected void doClose() {
                }
            };
            out.addSubscriber(new org.yamcs.yarch.StreamSubscriber() {
                @Override
                public void onTuple(Stream s, org.yamcs.yarch.Tuple tuple) {
                    byte[] pdu = tuple.getColumn("pdu");
                    counters[0]++;
                    counters[1] += pdu.length;
                }

                @Override
                public void streamClosed(Stream s) {
                }
            });

            Map<String, Object> m = new HashMap<>();
            m.put("entityIdLength", 2);
            m.put("sequenceNrLength", 4);
            m.put("maxPduSize", pduSize);
            m.put("localEntities", List.of(Map.of("name", "a", "id", 1)));
            m.put("remoteEntities", List.of(Map.of("name", "b", "id", 2)));
            YConfiguration config = new CfdpService().getSpec().validate(YConfiguration.wrap(m));

            for (int iter = 0; iter < 3; iter++) {
                counters[0] = counters[1] = 0;
                var request = new FilePutRequest(1, 2, "src", "dst", false, false, false, false, null, 0);
                var trsf = new CfdpOutgoingTransfer(yamcsInstance, 1, 1000 + iter, 0,
                        new ScheduledThreadPoolExecutor(1), request, out, config, null, null, null,
                        EventProducerFactory.getEventProducer(yamcsInstance, "perf", 10000), ft -> {
                        }, null);
                var s = OutgoingFileSource.openFile(path);
                t0 = System.nanoTime();
                trsf.setSource(s); // computes the checksum by streaming the file
                t1 = System.nanoTime();
                while (trsf.getTransferState() == TransferState.RUNNING) {
                    trsf.sendPDU();
                }
                long t2 = System.nanoTime();
                assertEquals(TransferState.COMPLETED, trsf.getTransferState(), trsf.getFailuredReason());
                long size = s.size();
                report("iteration " + iter + ": checksum by streaming", size, t1 - t0);
                report("iteration " + iter + ": PDU generation (" + counters[0] + " PDUs of max " + pduSize
                        + " bytes)", size, t2 - t1);
                System.out.printf("    %.2f us/PDU%n", (t2 - t1) / 1000.0 / counters[0]);
                s.close();
            }
            src.close();
        } finally {
            FileUtils.deleteRecursivelyIfExists(dir);
        }
    }

    static void report(String what, long bytes, long nanos) {
        System.out.printf("%s: %d MB in %.1f ms = %.0f MB/s%n", what, bytes >> 20, nanos / 1e6,
                (bytes / 1048576.0) / (nanos / 1e9));
    }
}
