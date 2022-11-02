package org.yamcs.replication;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.yamcs.replication.Message.DATA;
import static org.yamcs.replication.Message.STREAM_INFO;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Random;
import java.util.zip.CRC32;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.utils.FileUtils;

public class ReplicationFileTest {
    static Random random = new Random();
    Path dir;

    @BeforeEach
    public void before() throws IOException {
        dir = Files.createTempDirectory("repltest");
    }

    @AfterEach
    public void after() throws IOException {
        FileUtils.deleteRecursivelyIfExists(dir);
    }

    @Test
    public void test1() throws Exception {

        ReplicationFile rf = ReplicationFile.newFile("test", dir.resolve("t1"), 12, 1, 1, 200);

        assertNull(rf.tail(13));
        assertEquals(0, rf.tail(12).buf.remaining());

        MyTransaction tx1 = new MyTransaction(STREAM_INFO, 10);
        long txid = rf.writeData(tx1);
        assertEquals(12, txid);
        assertEquals(1, rf.numTx());

        ReplicationTail tail = rf.tail(12);
        ByteBuffer buf = tail.buf;
        ByteBuffer buf1 = buf.duplicate();

        assertEquals(34, buf.remaining());
        assertEquals(0x0400001E, buf.getInt());// type size
        assertEquals(tx1.getInstanceId(), buf.getInt()); // instanceid
        assertEquals(12l, buf.getLong());// transactionid
        assertEquals(0, buf.getInt()); // next metadata pointer
        byte[] b = new byte[buf.remaining() - 4];
        buf.get(b);
        assertArrayEquals(tx1.b, b); // data

        // verify CRC
        int crc = buf.getInt();
        CRC32 crc32 = new CRC32();
        byte[] b1 = new byte[buf1.remaining() - 4];
        buf1.get(b1);
        crc32.update(b1);
        assertEquals((int) crc32.getValue(), crc);

        long txid1 = rf.writeData(new MyTransaction(DATA, 200));
        assertEquals(-1, txid1);
        assertEquals(1, rf.numTx());

        rf.getNewData(tail);
        assertEquals(0, tail.buf.remaining());
        assertTrue(tail.eof);

        rf.close();
    }

    @Test
    public void test2() throws Exception {
        Path file1 = dir.resolve("t2");

        ReplicationFile rf = ReplicationFile.newFile("test", file1, 12, 10, 17, 200);
        verifyMetadata(rf);

        long txid = rf.writeData(new MyTransaction(DATA, 10));
        assertEquals(12, txid);
        assertEquals(1, rf.numTx());

        Iterator<ByteBuffer> it = rf.metadataIterator();
        assertFalse(it.hasNext());

        MyTransaction meta1 = new MyTransaction(STREAM_INFO, 10);
        meta1.txid = rf.writeData(meta1);
        assertEquals(13, meta1.txid);
        assertEquals(2, rf.numTx());

        assertEquals(64, rf.tail(12).buf.remaining());

        ByteBuffer bb = rf.tail(13).buf;
        assertEquals(34, bb.remaining());
        assertEquals(30, bb.getInt() & 0xFFFFFF);
        assertEquals(meta1.instanceId, bb.getInt());
        assertEquals(13, bb.getLong());

        verifyMetadata(rf, meta1);
        long txid1 = rf.writeData(new MyTransaction(DATA, 200));
        assertEquals(-1, txid1);// end of file

        verifyMetadata(rf, meta1);

        rf.close();

        ReplicationFile rf1 = ReplicationFile.openReadOnly("test", file1, 12);
        assertEquals(2, rf1.numTx());

        verifyMetadata(rf1, meta1);

        rf1.close();

        ReplicationFile rf2 = ReplicationFile.openReadWrite("test", file1, 12, 300);
        assertEquals(2, rf2.numTx());

        verifyMetadata(rf2, meta1);

        MyTransaction meta2 = new MyTransaction(STREAM_INFO, 10);
        meta2.txid = rf2.writeData(meta2);
        assertEquals(14, meta2.txid);
        assertEquals(3, rf2.numTx());

        verifyMetadata(rf2, meta1, meta2);

        rf2.close();
    }

    @Test
    public void test3() throws Exception {
        MyTransaction[] mm = new MyTransaction[50];
        Path file1 = dir.resolve("t3");
        ReplicationFile rf = ReplicationFile.newFile("test", file1, 0, 13, 17, 10000);

        mm[0] = new MyTransaction(STREAM_INFO, 10);
        mm[0].txid = rf.writeData(mm[0]);
        assertEquals(0, mm[0].txid);
        assertEquals(1, rf.numTx());

        mm[1] = new MyTransaction(STREAM_INFO, 10);
        mm[1].txid = rf.writeData(mm[1]);
        assertEquals(1, mm[1].txid);
        assertEquals(2, rf.numTx());

        for (int i = 2; i < 30; i++) {
            mm[i] = new MyTransaction(DATA, 1);
            mm[i].txid = rf.writeData(mm[i]);
            assertEquals(i, mm[i].txid);
        }
        assertEquals(30, rf.numTx());

        mm[30] = new MyTransaction(STREAM_INFO, 10);
        mm[30].txid = rf.writeData(mm[30]);
        assertEquals(30, mm[30].txid);
        assertEquals(31, rf.numTx());

        verifyMetadata(rf, mm[0], mm[1], mm[30]);
        rf.close();

        ReplicationFile rf1 = ReplicationFile.openReadWrite("test", file1, 0, 10000);
        assertEquals(31, rf1.numTx());
        verifyMetadata(rf1, mm[0], mm[1], mm[30]);
        rf1.close();
    }

    @Test
    public void test4() throws Exception {
        Path file1 = dir.resolve("t4");

        ReplicationFile rf = ReplicationFile.newFile("test", file1, 0, 10, 17, 20000);
        verifyMetadata(rf);

        rf.writeData(new MyTransaction(DATA, 10));
        ReplicationTail rt = rf.tail(0);

        assertEquals(false, rt.eof);
        assertEquals(1, rt.nextTxId);
        rt.buf.position(rt.buf.limit());
        rf.getNewData(rt);
        assertEquals(false, rt.eof);
        assertEquals(1, rt.nextTxId);
        assertEquals(0, rt.buf.remaining());

        rf.writeData(new MyTransaction(DATA, 10));

        rf.getNewData(rt);
        assertEquals(false, rt.eof);
        assertEquals(2, rt.nextTxId);
        assertEquals(30, rt.buf.remaining());
        for (int i = 2; i < 170; i++) {
            long txId = rf.writeData(new MyTransaction(DATA, 10));
            assertEquals(i, txId);
        }
        long txId = rf.writeData(new MyTransaction(DATA, 10));
        assertEquals(-1, txId);
        assertTrue(rf.isFull());

        rf.getNewData(rt);
        assertEquals(true, rt.eof);
        assertEquals(170, rt.nextTxId);
        assertEquals(30 * 169, rt.buf.remaining());
        rf.close();

        ReplicationFile rf1 = ReplicationFile.openReadOnly("test", file1, 0);
        ReplicationTail rt1 = rf1.tail(0);
        assertEquals(true, rt1.eof);
        assertEquals(170, rt1.nextTxId);
        assertEquals(30 * 170, rt1.buf.remaining());
        assertEquals(0x0500001A, rt1.buf.getInt());

        rf1.close();
    }

    @Test
    public void test5() throws Exception {
        Path file1 = dir.resolve("t5");

        ReplicationFile rf = ReplicationFile.newFile("test", file1, 0, 10, 17, 20000);
        rf.writeData(new MyTransaction(DATA, 10));
        rf.writeData(new MyTransaction(DATA, 10));

        ReplicationFile rf1 = ReplicationFile.openReadOnly("test", file1, 0);
        assertEquals(2, rf1.numTx());

        rf1.close();
        rf.close();
    }

    @Test
    public void test6() throws Exception {

        Path file1 = dir.resolve("t5");

        for (int i = 0; i < 10; i++) {
            java.nio.file.Files.deleteIfExists(file1);
            ReplicationFile rf = ReplicationFile.newFile("test", file1, 0, 10, 17,
                    ReplicationFile.headerSize(10, 17) + i + 20);
            long txId = rf.writeData(new MyTransaction(DATA, 10));
            assertEquals(-1, txId);
            assertTrue(rf.isFull());
            rf.close();
        }

        for (int i = 0; i < 30; i++) {
            java.nio.file.Files.deleteIfExists(file1);
            ReplicationFile rf = ReplicationFile.newFile("test", file1, 0, 10, 17,
                    ReplicationFile.headerSize(10, 17) + i + 30);
            long txId = rf.writeData(new MyTransaction(DATA, 10));
            assertEquals(0, txId);
            assertFalse(rf.isFull());

            txId = rf.writeData(new MyTransaction(DATA, 10));
            assertEquals(-1, txId);
            assertTrue(rf.isFull());
            rf.close();
        }

    }

    void verifyMetadata(ReplicationFile rf, MyTransaction... metaRecords) {
        Iterator<ByteBuffer> it = rf.metadataIterator();

        for (int i = 0; i < metaRecords.length; i++) {
            MyTransaction meta = metaRecords[i];
            assertTrue(it.hasNext());
            ByteBuffer buf = it.next();

            // verify CRC
            checkCrc(buf.duplicate());
            int typesize = buf.getInt();
            assertEquals(typesize & 0xFFFFFF, buf.remaining());
            int instanceId = buf.getInt();
            assertEquals(meta.instanceId, instanceId);

            long txid = buf.getLong();

            assertEquals(meta.txid, txid);

            assertEquals(meta.type, typesize >> 24);
            assertEquals(meta.b.length + 20, typesize & 0xFFFF);
            assertEquals(meta.b.length + 8, buf.remaining());
            buf.getInt();// skip next metadata pointer

            byte[] b1 = new byte[meta.b.length];
            buf.get(b1);
            assertArrayEquals(meta.b, b1);
        }
    }

    private void checkCrc(ByteBuffer buf) {
        buf.limit(buf.limit() - 4);
        CRC32 crc = new CRC32();
        crc.update(buf);
        int ccrc = (int) crc.getValue();

        buf.limit(buf.limit() + 4);
        int rcrc = buf.getInt();
        assertEquals(ccrc, rcrc);
    }

    class MyTransaction implements Transaction {
        byte[] b;
        long txid;
        byte type;
        int instanceId;

        MyTransaction(byte type, int size) {
            this.type = type;
            this.b = new byte[size];
            random.nextBytes(b);
            this.instanceId = random.nextInt();
        }

        @Override
        public void marshall(ByteBuffer buf) {
            buf.put(b);
        }

        @Override
        public byte getType() {
            return type;
        }

        @Override
        public int getInstanceId() {
            return instanceId;
        }
    }
}
