package org.yamcs.tctm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.utils.TimeEncoding;

public class FilePollingTmDataLinkTest {
    int headerSize = 103;

    @BeforeAll
    static public void beforeClass() {
        TimeEncoding.setUp();
        EventProducerFactory.setMockup(false);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void test1(boolean gzipped) throws Exception {
        File incomingDir = Files.createTempDirectory("FilePollingTmDataLinkTest").toFile();
        File f1 = new File(incomingDir, "f1");

        CcsdsPacket p1 = new CcsdsPacket(new byte[50]);
        p1.setHeader(100, 1, 0, 3, 1000);
        CcsdsPacket p2 = new CcsdsPacket(new byte[50]);
        p2.setHeader(100, 1, 0, 3, 1001);

        try (OutputStream out = gzipped ? new BufferedOutputStream(new FileOutputStream(f1))
                : new GZIPOutputStream(new FileOutputStream(f1))) {
            out.write(new byte[headerSize]);
            out.write(p1.getBytes());
            out.write(p2.getBytes());
        }
        Map<String, Object> conf = new HashMap<>();
        conf.put("incomingDir", incomingDir.getAbsolutePath());
        conf.put("headerSize", headerSize);
        conf.put("deleteAfterImport", true);
        conf.put("packetInputStreamClassName", CcsdsPacketInputStream.class.getName());
        FilePollingTmDataLink fileLink = new FilePollingTmDataLink();

        fileLink.init("test", "test", YConfiguration.wrap(conf));
        Semaphore semaphore = new Semaphore(0);
        List<TmPacket> tmPackets = new ArrayList<>();
        fileLink.setTmSink(new TmSink() {
            int count = 0;

            @Override
            public void processPacket(TmPacket tmPacket) {
                tmPackets.add(tmPacket);
                count++;
                if (count == 2) {
                    semaphore.release();
                }
            }
        });
        fileLink.startAsync().awaitRunning();
        assertTrue(semaphore.tryAcquire(10, TimeUnit.SECONDS));

        fileLink.stopAsync().awaitTerminated();
        assertEquals(2, tmPackets.size());
        assertArrayEquals(p1.getBytes(), tmPackets.get(0).getPacket());
        assertArrayEquals(p2.getBytes(), tmPackets.get(1).getPacket());
        assertFalse(f1.exists());
        assertTrue(incomingDir.delete());
    }
}
