package org.yamcs.cfdp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.zip.CRC32C;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yamcs.cfdp.pdu.FileDataPacket;
import org.yamcs.cfdp.pdu.SegmentRequest;

public class DiskDataFileTest {
    @TempDir
    Path dir;

    Random random = new Random(42);
    Path dataPath;
    Path journalPath;
    int savedCompactMin;

    @BeforeEach
    public void before() {
        dataPath = dir.resolve("1.data");
        journalPath = dir.resolve("1.journal");
        savedCompactMin = DiskDataFile.compactMinRecords;
    }

    @AfterEach
    public void after() {
        DiskDataFile.compactMinRecords = savedCompactMin;
    }

    @Test
    public void testCrcCombine() {
        byte[] a = new byte[1000];
        random.nextBytes(a);
        for (int split : new int[] { 0, 1, 3, 4, 500, 999, 1000 }) {
            int c1 = crc(a, 0, split);
            int c2 = crc(a, split, a.length - split);
            assertEquals(crc(a, 0, a.length), Crc32cUtils.combine(c1, c2, a.length - split));
        }
    }

    @Test
    public void testModularChecksumOffsets() {
        byte[] a = new byte[103];
        random.nextBytes(a);
        long full = ModularChecksumCalculator.calculateChecksum(a);
        for (int split = 0; split <= a.length; split++) {
            long s1 = ModularChecksumCalculator.calculateChecksum(a, 0, 0, split);
            long s2 = ModularChecksumCalculator.calculateChecksum(a, split, split, a.length - split);
            assertEquals(full, (s1 + s2) & 0xFFFFFFFFL);
            var bb = java.nio.ByteBuffer.wrap(a, split, a.length - split);
            assertEquals(s2, ModularChecksumCalculator.calculateChecksum(bb.slice(), split));
        }
        // compare with the old byte-by-byte implementation
        assertEquals(oldChecksum(a, 5, 50), ModularChecksumCalculator.calculateChecksum(a, 0, 5, 50));
        assertEquals(oldChecksum(a, 6, 3), ModularChecksumCalculator.calculateChecksum(a, 0, 6, 3));
    }

    @Test
    public void testRandomSegments() throws IOException {
        int size = 100_000;
        byte[] content = randomContent(size);
        DataFile mem = new DataFile(size);
        DiskDataFile disk = DiskDataFile.create(dataPath, journalPath);
        disk.setSize(size);

        // random, overlapping, out of order segments covering about 70% of the file
        for (int i = 0; i < 300; i++) {
            int off = random.nextInt(size);
            int len = Math.min(size - off, 1 + random.nextInt(700));
            FileDataPacket fdp = fdp(content, off, len);
            mem.addSegment(fdp);
            disk.addSegment(fdp);
        }
        assertEquals(mem.getMissingChunks().toString(), disk.getMissingChunks().toString());
        assertEquals(mem.getReceivedSize(), disk.getReceivedSize());
        assertEquals(expectedChecksum(content, disk), disk.getModularChecksum());
        assertEquals(mem.getModularChecksum(), disk.getModularChecksum());
        checkSegmentCrcs(content, disk);
        checkContent(content, disk);

        // reopen
        disk.sync();
        disk.close();
        DiskDataFile disk2 = DiskDataFile.open(dataPath, journalPath, size);
        disk2.setSize(size);
        assertEquals(0, disk2.getDroppedRecords());
        assertEquals(mem.getMissingChunks().toString(), disk2.getMissingChunks().toString());
        assertEquals(mem.getModularChecksum(), disk2.getModularChecksum());
        // after reopen the journal is compacted
        assertEquals(disk2.segments.count(), disk2.getJournalRecords());
        checkSegmentCrcs(content, disk2);

        // complete the file
        for (SegmentRequest sr : disk2.getMissingChunks()) {
            disk2.addSegment(fdp(content, (int) sr.getSegmentStart(),
                    (int) (sr.getSegmentEnd() - sr.getSegmentStart())));
        }
        assertTrue(disk2.isComplete());
        assertArrayEquals(content, disk2.getData());
        assertEquals(ModularChecksumCalculator.calculateChecksum(content), disk2.getModularChecksum());
        disk2.delete();
        assertFalse(Files.exists(dataPath));
        assertFalse(Files.exists(journalPath));
    }

    @Test
    public void testOverlapWritesOnlyNewData() throws IOException {
        byte[] content = randomContent(100);
        DiskDataFile disk = DiskDataFile.create(dataPath, journalPath);
        disk.addSegment(fdp(content, 10, 10));
        // same range with different data: the first data is kept
        byte[] other = content.clone();
        Arrays.fill(other, (byte) 0x55);
        disk.addSegment(fdp(other, 5, 30));
        disk.setSize(100);
        byte[] d = disk.getData();
        assertEquals(content[10], d[10]);
        assertEquals((byte) 0x55, d[5]);
        assertEquals((byte) 0x55, d[25]);
        // 3 records: [10,20), [5,10), [20,35)
        assertEquals(3, disk.getJournalRecords());
        assertEquals(30, disk.getReceivedSize());
        disk.delete();
    }

    @Test
    public void testCorruptedData() throws IOException {
        int size = 10_000;
        byte[] content = randomContent(size);
        DiskDataFile disk = DiskDataFile.create(dataPath, journalPath);
        for (int off = 0; off < size; off += 1000) {
            disk.addSegment(fdp(content, off, 1000));
        }
        disk.sync();
        disk.close();

        // corrupt the third segment (simulates data not flushed before a crash)
        try (RandomAccessFile raf = new RandomAccessFile(dataPath.toFile(), "rw")) {
            raf.seek(2500);
            raf.write(content[2500] ^ 0xFF);
        }

        DiskDataFile disk2 = DiskDataFile.open(dataPath, journalPath, size);
        disk2.setSize(size);
        assertEquals(1, disk2.getDroppedRecords());
        assertEquals(1000, disk2.getDroppedBytes());
        List<SegmentRequest> missing = disk2.getMissingChunks();
        assertEquals(1, missing.size());
        assertEquals(2000, missing.get(0).getSegmentStart());
        assertEquals(3000, missing.get(0).getSegmentEnd());
        assertEquals(expectedChecksum(content, disk2), disk2.getModularChecksum());

        // re-receive the missing segment
        disk2.addSegment(fdp(content, 2000, 1000));
        assertTrue(disk2.isComplete());
        assertArrayEquals(content, disk2.getData());
        assertEquals(ModularChecksumCalculator.calculateChecksum(content), disk2.getModularChecksum());
        disk2.sync();
        disk2.close();

        // and once more, everything should be fine now
        DiskDataFile disk3 = DiskDataFile.open(dataPath, journalPath, size);
        disk3.setSize(size);
        assertEquals(0, disk3.getDroppedRecords());
        assertTrue(disk3.isComplete());
        assertEquals(ModularChecksumCalculator.calculateChecksum(content), disk3.getModularChecksum());
        disk3.close();
    }

    @Test
    public void testTruncatedJournalAndData() throws IOException {
        int size = 10_000;
        byte[] content = randomContent(size);
        DiskDataFile disk = DiskDataFile.create(dataPath, journalPath);
        for (int off = 0; off < size; off += 1000) {
            disk.addSegment(fdp(content, off, 1000));
        }
        disk.sync();
        disk.close();

        // truncate the last journal record (torn write) and the data file in the middle of the 9th segment
        long jsize = Files.size(journalPath);
        try (RandomAccessFile raf = new RandomAccessFile(journalPath.toFile(), "rw")) {
            raf.setLength(jsize - 5);
        }
        try (RandomAccessFile raf = new RandomAccessFile(dataPath.toFile(), "rw")) {
            raf.setLength(8500);
        }
        DiskDataFile disk2 = DiskDataFile.open(dataPath, journalPath, size);
        disk2.setSize(size);
        // the truncated record + the 9th segment
        assertEquals(2, disk2.getDroppedRecords());
        List<SegmentRequest> missing = disk2.getMissingChunks();
        assertEquals(1, missing.size());
        assertEquals(8000, missing.get(0).getSegmentStart());
        assertEquals(size, missing.get(0).getSegmentEnd());
        assertEquals(expectedChecksum(content, disk2), disk2.getModularChecksum());
        disk2.close();
    }

    @Test
    public void testLostJournalBuffer() throws IOException {
        int size = 10_000;
        byte[] content = randomContent(size);
        DiskDataFile disk = DiskDataFile.create(dataPath, journalPath);
        for (int off = 0; off < 5000; off += 1000) {
            disk.addSegment(fdp(content, off, 1000));
        }
        disk.sync();
        for (int off = 5000; off < size; off += 1000) {
            disk.addSegment(fdp(content, off, 1000));
        }
        // crash: the buffered journal records are lost
        disk.closeWithoutFlush();

        DiskDataFile disk2 = DiskDataFile.open(dataPath, journalPath, size);
        disk2.setSize(size);
        assertEquals(5000, disk2.getReceivedSize());
        assertEquals("[5000-10000]", disk2.getMissingChunks().toString());
        disk2.close();
    }

    @Test
    public void testInvalidJournalHeader() throws IOException {
        byte[] content = randomContent(1000);
        DiskDataFile disk = DiskDataFile.create(dataPath, journalPath);
        disk.addSegment(fdp(content, 0, 1000));
        disk.sync();
        disk.close();
        try (RandomAccessFile raf = new RandomAccessFile(journalPath.toFile(), "rw")) {
            raf.write(new byte[] { 1, 2, 3 });
        }
        DiskDataFile disk2 = DiskDataFile.open(dataPath, journalPath, 1000);
        assertEquals(0, disk2.getReceivedSize());
        assertEquals(0, disk2.getModularChecksum());
        disk2.close();

        // missing journal
        Files.delete(journalPath);
        DiskDataFile disk3 = DiskDataFile.open(dataPath, journalPath, 1000);
        assertEquals(0, disk3.getReceivedSize());
        disk3.close();
    }

    @Test
    public void testMaxEnd() throws IOException {
        byte[] content = randomContent(1000);
        DiskDataFile disk = DiskDataFile.create(dataPath, journalPath);
        disk.addSegment(fdp(content, 0, 500));
        disk.addSegment(fdp(content, 600, 400));
        disk.sync();
        disk.close();
        // the metadata says the file is 900 bytes; the segment beyond is dropped
        DiskDataFile disk2 = DiskDataFile.open(dataPath, journalPath, 900);
        assertEquals(500, disk2.getReceivedSize());
        assertEquals(1, disk2.getDroppedRecords());
        disk2.close();
    }

    @Test
    public void testCompaction() throws IOException {
        DiskDataFile.compactMinRecords = 100;
        int size = 100_000;
        byte[] content = randomContent(size);
        DiskDataFile disk = DiskDataFile.create(dataPath, journalPath);
        disk.setSize(size);
        // two interleaved streams with a gap, then fill
        for (int off = 0; off < 40_000; off += 100) {
            disk.addSegment(fdp(content, off, 100));
            disk.addSegment(fdp(content, 50_000 + off, 100));
        }
        assertEquals(800, disk.getJournalRecords());
        disk.sync();
        assertEquals(2, disk.getJournalRecords());
        assertEquals(16 + 2 * 20, Files.size(journalPath));
        checkSegmentCrcs(content, disk);

        disk.addSegment(fdp(content, 40_000, 10_000));
        disk.addSegment(fdp(content, 90_000, 10_000));
        disk.sync();
        disk.close();

        DiskDataFile disk2 = DiskDataFile.open(dataPath, journalPath, size);
        disk2.setSize(size);
        assertEquals(0, disk2.getDroppedRecords());
        assertTrue(disk2.isComplete());
        assertEquals(1, disk2.getJournalRecords());
        assertEquals(ModularChecksumCalculator.calculateChecksum(content), disk2.getModularChecksum());
        assertArrayEquals(content, disk2.getData());
        disk2.close();
    }

    @Test
    public void testLargeOffsets() throws IOException {
        // sparse file larger than 4GB; only the end is written
        long size = 5L * 1024 * 1024 * 1024 + 3;
        byte[] content = randomContent(1003);
        DiskDataFile disk = DiskDataFile.create(dataPath, journalPath);
        disk.setSize(size);
        long off = size - content.length;
        disk.addSegment(new FileDataPacket(content, off, null));
        assertEquals(List.of(new SegmentRequest(0, off).toString()),
                disk.getMissingChunks().stream().map(Object::toString).toList());
        assertEquals(ModularChecksumCalculator.calculateChecksum(content, 0, off, content.length),
                disk.getModularChecksum());
        disk.sync();
        disk.close();
        DiskDataFile disk2 = DiskDataFile.open(dataPath, journalPath, size);
        assertEquals(content.length, disk2.getReceivedSize());
        assertEquals(disk.getModularChecksum(), disk2.getModularChecksum());
        disk2.delete();
    }

    /**
     * Measures the throughput of receiving a 200 MB file in 1 KB PDUs, with a sync every second, and of re-opening
     * (verifying) it.
     */
    @Disabled
    @Test
    public void testThroughput() throws IOException {
        int pduSize = 1024;
        long size = 200L * 1024 * 1024;
        byte[] chunk = randomContent(64 * 1024);
        DiskDataFile disk = DiskDataFile.create(dataPath, journalPath);
        disk.setSize(size);
        // pre-create the PDUs such that we measure only the file writing
        List<FileDataPacket> pdus = new ArrayList<>();
        for (int i = 0; i < chunk.length / pduSize; i++) {
            pdus.add(new FileDataPacket(Arrays.copyOfRange(chunk, i * pduSize, (i + 1) * pduSize), 0, null));
        }
        long t0 = System.nanoTime();
        long lastSync = t0;
        int numPdus = (int) (size / pduSize);
        for (int i = 0; i < numPdus; i++) {
            FileDataPacket p = pdus.get(i % pdus.size());
            disk.addSegment(new FileDataPacket(p.getData(), (long) i * pduSize, null));
            long now = System.nanoTime();
            if (now - lastSync > 1_000_000_000L) {
                disk.sync();
                lastSync = now;
            }
        }
        disk.sync();
        long t1 = System.nanoTime();
        assertTrue(disk.isComplete());
        long checksum = disk.getModularChecksum();
        disk.close();

        long t2 = System.nanoTime();
        DiskDataFile disk2 = DiskDataFile.open(dataPath, journalPath, size);
        long t3 = System.nanoTime();
        disk2.setSize(size);
        assertTrue(disk2.isComplete());
        assertEquals(checksum, disk2.getModularChecksum());

        long t4 = System.nanoTime();
        byte[] data = disk2.getData();
        long t5 = System.nanoTime();
        assertEquals(size, data.length);
        disk2.delete();

        double mb = size / (1024.0 * 1024);
        System.out.printf("DiskDataFile throughput: write %.1f MB/s (%.2f us/PDU, %d PDUs of %d bytes); "
                + "reopen+verify %.1f MB/s; getData %.1f MB/s%n",
                mb / ((t1 - t0) / 1e9), (t1 - t0) / 1e3 / numPdus, numPdus, pduSize,
                mb / ((t3 - t2) / 1e9), mb / ((t5 - t4) / 1e9));
    }

    private void checkSegmentCrcs(byte[] content, DiskDataFile disk) {
        for (var seg : disk.segments.segments()) {
            assertEquals(crc(content, (int) seg.start(), (int) seg.length()), seg.crc(), "segment " + seg);
        }
    }

    private void checkContent(byte[] content, DiskDataFile disk) {
        byte[] d = disk.getData();
        for (var seg : disk.segments.segments()) {
            for (int i = (int) seg.start(); i < seg.end(); i++) {
                assertEquals(content[i], d[i]);
            }
        }
    }

    private long expectedChecksum(byte[] content, AbstractDataFile df) {
        long sum = 0;
        for (var seg : df.segments.segments()) {
            sum += ModularChecksumCalculator.calculateChecksum(content, (int) seg.start(), seg.start(),
                    (int) seg.length());
        }
        return sum & 0xFFFFFFFFL;
    }

    private byte[] randomContent(int size) {
        byte[] b = new byte[size];
        random.nextBytes(b);
        return b;
    }

    private static FileDataPacket fdp(byte[] content, int offset, int length) {
        return new FileDataPacket(Arrays.copyOfRange(content, offset, offset + length), offset, null);
    }

    private static int crc(byte[] b, int off, int len) {
        CRC32C c = new CRC32C();
        c.update(b, off, len);
        return (int) c.getValue();
    }

    // the original implementation, for comparison
    static long oldChecksum(byte[] data, long fileOffset, long length) {
        int k = (int) (fileOffset & 3);
        long checksum = 0;
        int i = 0;
        long x = 0;
        while (i < length) {
            x = (x << 8) + (data[i] & 0xFF);
            i++;
            k++;
            if (k == 4) {
                checksum += x;
                x = 0;
                k = 0;
            }
        }
        x = x << ((4 - k) << 3);
        checksum += x;
        return checksum & 0xFFFFFFFFL;
    }
}
