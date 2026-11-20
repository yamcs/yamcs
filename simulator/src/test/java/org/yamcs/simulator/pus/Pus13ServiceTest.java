package org.yamcs.simulator.pus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Pus13ServiceTest {
    @Test
    void rejectsReassembledCommandWithInvalidChecksum(@TempDir Path dataDir) {
        List<PusTmPacket> reports = new ArrayList<>();
        var simulator = new PusSimulator(dataDir.toFile()) {
            @Override
            void transmitRealtimeTM(PusTmPacket packet) {
                reports.add(packet);
            }
        };
        simulator.executor = new ScheduledThreadPoolExecutor(1);
        try {
            int firstPartSize = Pus13Service.PART_SIZE;
            var embedded = new PusTcPacket(1, firstPartSize - 16, 7, 17, 1);
            embedded.fillChecksum();
            byte[] data = embedded.getBytes();
            data[data.length - 1] ^= 1;

            var first = new PusTcPacket(1, 4 + firstPartSize, 7, 13, 9);
            first.getUserDataBuffer().putShort((short) 1).putShort((short) 1).put(data, 0, firstPartSize);
            simulator.pus13Service.executeTc(first);
            reports.clear();

            var last = new PusTcPacket(1, 6, 7, 13, 11);
            last.getUserDataBuffer().putShort((short) 1).putShort((short) 2).put((byte) 1).put(data[firstPartSize]);
            simulator.pus13Service.executeTc(last);

            assertEquals(List.of(3, 8, 16), reports.stream().map(PusTmPacket::getSubtype).toList());
            assertEquals(Pus13Service.COMPL_ERR_RECONSTRUCTION_FAILED, reports.get(1).getUserDataBuffer().get(4));
            assertTrue(simulator.pendingCommands.isEmpty());
        } finally {
            simulator.executor.shutdownNow();
        }
    }
}
