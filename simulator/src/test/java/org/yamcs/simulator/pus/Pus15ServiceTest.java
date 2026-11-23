package org.yamcs.simulator.pus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yamcs.utils.BitBuffer;

class Pus15ServiceTest {
    @Test
    void applicationReportPagesKeepTheFullSubtypeSet(@TempDir Path dataDir) {
        List<PusTmPacket> pages = new ArrayList<>();
        var simulator = new PusSimulator(dataDir.toFile()) {
            @Override
            void transmitRealtimeTM(PusTmPacket packet) {
                if (packet.getType() == 15 && packet.getSubtype() == 6) {
                    pages.add(packet);
                }
            }
        };
        var service = simulator.pus15Service;
        PusTcPacket create = tc(20, 5);
        create.getUserDataBuffer().put(new byte[] { 1, 100, 5, 1, 0 });
        service.executeTc(create);
        addSubtypes(service, 0);
        addSubtypes(service, 128);

        PusTcPacket report = tc(5, 1);
        report.getUserDataBuffer().put((byte) 100);
        service.executeTc(report);

        assertEquals(2, pages.size());
        int[] rowCounts = { 255, 1 };
        int nextSubtype = 0;
        for (int i = 0; i < pages.size(); i++) {
            PusTmPacket page = pages.get(i);
            int offset = page.getUserDataOffset();
            assertArrayEquals(new byte[] { 0, (byte) (i + 1), 0, 2 },
                    Arrays.copyOfRange(page.getBytes(), offset - 4, offset));
            BitBuffer data = PusPackedFields.bitBuffer(page.getUserDataBuffer());
            assertEquals(100, data.getBits(8));
            assertEquals(1, data.getBits(8));
            assertEquals(16, data.getBits(11));
            assertEquals(1, data.getBits(8));
            assertEquals(3, data.getBits(8));
            assertEquals(rowCounts[i], data.getBits(8));
            for (int j = 0; j < rowCounts[i]; j++) {
                assertEquals(nextSubtype++, data.getBits(8));
            }
        }
        assertEquals(256, nextSubtype);
    }

    @Test
    void emptyIdentifierListsStillReportTheirApid(@TempDir Path dataDir) {
        List<PusTmPacket> reports = new ArrayList<>();
        var simulator = new PusSimulator(dataDir.toFile()) {
            @Override
            void transmitRealtimeTM(PusTmPacket packet) {
                if (packet.getType() == 15) {
                    reports.add(packet);
                }
            }
        };
        var service = simulator.pus15Service;
        PusTcPacket create = tc(20, 5);
        create.getUserDataBuffer().put(new byte[] { 1, 100, 5, 1, 0 });
        service.executeTc(create);

        for (int subtype : new int[] { 29, 31, 34 }) {
            PusTcPacket add = tc(subtype, 5);
            BitBuffer data = PusPackedFields.bitBuffer(add.getUserDataBuffer());
            data.putBits(100, 8);
            data.putBits(1, 8);
            data.putBits(16, 11);
            data.putBits(0, 8);
            service.executeTc(add);
        }
        for (int subtype : new int[] { 35, 37, 39 }) {
            PusTcPacket report = tc(subtype, 1);
            report.getUserDataBuffer().put((byte) 100);
            service.executeTc(report);
        }

        assertEquals(3, reports.size());
        for (var report : reports) {
            BitBuffer data = PusPackedFields.bitBuffer(report.getUserDataBuffer());
            assertEquals(100, data.getBits(8));
            assertEquals(1, data.getBits(8));
            assertEquals(16, data.getBits(11));
            assertEquals(0, data.getBits(8));
        }
    }

    @Test
    void byteCapacityProfileReportsAndStoresBytes(@TempDir Path dataDir) {
        List<PusTmPacket> reports = new ArrayList<>();
        var time = PusTimeEncoding.DEFAULT;
        var packetConfig = new PusPacketConfig(16, 4, true, true, true, true, true, 1);
        var simulator = new PusSimulator(dataDir.toFile(), time, PusTimeEncoding.STORAGE, packetConfig,
                new Pus11ScheduleConfig(time)) {
            @Override
            void transmitRealtimeTM(PusTmPacket packet) {
                if (packet.getType() == 15) {
                    reports.add(packet);
                }
            }
        };
        var service = simulator.pus15Service;
        var create = tc(20, 5);
        create.getUserDataBuffer().put(new byte[] { 1, 100, 100, 1, 0 });
        service.executeTc(create);

        var enable = tc(1, 1);
        enable.getUserDataBuffer().put((byte) 0);
        service.executeTc(enable);
        var configure = tc(3, 5);
        var data = PusPackedFields.bitBuffer(configure.getUserDataBuffer());
        data.putBits(100, 8);
        data.putBits(1, 8);
        data.putBits(16, 11);
        data.putBits(0, 8);
        service.executeTc(configure);

        var stored = new PusTmPacket(16, 10, 17, 2);
        service.submitToStores(stored);

        service.executeTc(tc(22, 0));
        var summaryRequest = tc(12, 1);
        summaryRequest.getUserDataBuffer().put((byte) 0);
        service.executeTc(summaryRequest);
        assertEquals(2, reports.size());
        var config = reports.get(0);
        assertEquals(23, config.getSubtype());
        assertEquals(100, Byte.toUnsignedInt(config.getUserDataBuffer().get(2)));
        var summary = reports.get(1);
        assertEquals(13, summary.getSubtype());
        assertEquals(stored.getBytes().length,
                Byte.toUnsignedInt(summary.getUserDataBuffer().get(2 + 3 * PusTimeEncoding.STORAGE.getEncodedLength())));
    }

    private static void addSubtypes(Pus15Service service, int first) {
        PusTcPacket packet = tc(3, 135);
        ByteBuffer payload = packet.getUserDataBuffer();
        BitBuffer data = PusPackedFields.bitBuffer(payload);
        data.putBits(100, 8);
        data.putBits(1, 8);
        data.putBits(16, 11);
        data.putBits(1, 8);
        data.putBits(3, 8);
        data.putBits(128, 8);
        for (int i = first; i < first + 128; i++) {
            data.putBits(i, 8);
        }
        service.executeTc(packet);
    }

    private static PusTcPacket tc(int subtype, int length) {
        return new PusTcPacket(16, length, 0, 15, subtype);
    }
}
