package org.yamcs.replication;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Random;

import org.junit.Test;

import com.google.common.io.Files;
import static org.yamcs.replication.MessageType.*;

public class ReplicationFileTest {
    static Random random = new Random();


    @Test
    public void test1() throws Exception {
        File dir = Files.createTempDir();
        dir.deleteOnExit();

        ReplicationFile rf = ReplicationFile.newFile("test", dir.getAbsolutePath(), 12, 1, 1, 200);

        assertNull(rf.tail(13));
        assertEquals(0, rf.tail(12).buf.remaining());
        
        long txid = rf.writeData(new MyTransaction(STREAM_INFO, 10));
        assertEquals(12, txid);
        assertEquals(1, rf.numTx());

        assertEquals(26, rf.tail(12).buf.remaining());
        
        long txid1 = rf.writeData(new MyTransaction(DATA, 200));
        assertEquals(-1, txid1);
        assertEquals(1, rf.numTx());
   
        
        rf.close();
    }
    
    
    @Test
    public void test2() throws Exception {
        File dir = Files.createTempDir();
        dir.deleteOnExit();

        ReplicationFile rf = ReplicationFile.newFile("test",dir.getAbsolutePath(), 12, 10, 17, 200);
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
        
        assertEquals(48, rf.tail(12).buf.remaining());
        ByteBuffer bb = rf.tail(13).buf;
        assertEquals(26, bb.remaining());
        assertEquals(22, bb.getInt()&0xFFFFFF);
        assertEquals(13, bb.getLong());
        

        verifyMetadata(rf, meta1);
        long txid1 = rf.writeData(new MyTransaction(DATA, 200));
        assertEquals(-1, txid1);// end of file

        verifyMetadata(rf, meta1);

        rf.close();

        ReplicationFile rf1 = ReplicationFile.openReadOnly("test",dir.getAbsolutePath(), 12);
        assertEquals(2, rf1.numTx());

        verifyMetadata(rf1, meta1);

        rf1.close();
        
        ReplicationFile rf2 = ReplicationFile.openReadWrite("test",dir.getAbsolutePath(), 12, 300);
        assertEquals(2, rf2.numTx());

        verifyMetadata(rf2, meta1);

        
        MyTransaction meta2 = new MyTransaction(STREAM_INFO,10);
        meta2.txid = rf2.writeData(meta2);
        assertEquals(14, meta2.txid);
        assertEquals(3, rf2.numTx());

        verifyMetadata(rf2, meta1, meta2);
        
        rf2.close();
    }

    @Test
    public void test3() throws Exception {
        File dir = Files.createTempDir();
        dir.deleteOnExit();
        MyTransaction[] mm = new MyTransaction[50];
        
        ReplicationFile rf = ReplicationFile.newFile("test",dir.getAbsolutePath(), 0, 13, 17, 10000);

        mm[0] = new MyTransaction(STREAM_INFO, 10);
        mm[0].txid = rf.writeData(mm[0]);
        assertEquals(0, mm[0].txid);
        assertEquals(1, rf.numTx());
        

        mm[1] = new MyTransaction(STREAM_INFO, 10);
        mm[1].txid = rf.writeData(mm[1]);
        assertEquals(1, mm[1].txid);
        assertEquals(2, rf.numTx());
        
        for(int i=2; i<30; i++) {
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
        
        ReplicationFile rf1 = ReplicationFile.openReadWrite("test",dir.getAbsolutePath(), 0, 10000);
        assertEquals(31, rf1.numTx());
        verifyMetadata(rf1, mm[0], mm[1], mm[30]);
        rf1.close();
    }
    
    
    @Test
    public void test4() throws Exception {
        File dir = Files.createTempDir();
        dir.deleteOnExit();

        ReplicationFile rf = ReplicationFile.newFile("test",dir.getAbsolutePath(), 0, 10, 17, 20000);
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
        assertEquals(22, rt.buf.remaining());
        for(int i=2; i<170; i++) {
            long txId = rf.writeData(new MyTransaction(DATA, 10));
            assertEquals(i, txId);
        }
        long txId = rf.writeData(new MyTransaction(DATA, 10));
        assertEquals(-1, txId);
        assertTrue(rf.isFull());
        
        rf.getNewData(rt);
        assertEquals(true, rt.eof);
        assertEquals(170, rt.nextTxId);
        assertEquals(22*169, rt.buf.remaining());
        rf.close();
        
        ReplicationFile rf1 = ReplicationFile.openReadOnly("test",dir.getAbsolutePath(), 0);
        ReplicationTail rt1 = rf1.tail(0);
        assertEquals(true, rt1.eof);
        assertEquals(170, rt1.nextTxId);
        assertEquals(22*170, rt1.buf.remaining());
        assertEquals(0x05000012, rt1.buf.getInt());
        
        rf1.close();
    }
    void verifyMetadata(ReplicationFile rf, MyTransaction... metaRecords) {
        Iterator<ByteBuffer> it = rf.metadataIterator();

        for (int i = 0; i < metaRecords.length; i++) {
            MyTransaction meta = metaRecords[i];
            assertTrue(it.hasNext());
            ByteBuffer buf1 = it.next();
            
            int typesize = buf1.getInt();
            assertEquals(typesize&0xFFFFFF, buf1.remaining());
            
            long txid = buf1.getLong();
           
            
            assertEquals(meta.txid, txid);
            
            assertEquals(meta.type, typesize >> 24);
            assertEquals(meta.b.length + 12, typesize & 0xFFFF);
            assertEquals(meta.b.length + 4, buf1.remaining());
            buf1.getInt();//skip next metadata pointer
            
            byte[] b1 = new byte[meta.b.length];
            buf1.get(b1);
            assertArrayEquals(meta.b, b1);
        }
    }

    class MyTransaction implements Transaction {
        byte[] b;
        long txid;
        byte type;

        MyTransaction(byte type, int size) {
            this.type = type;
            this.b = new byte[size];
            random.nextBytes(b);
        }

        @Override
        public void marshall(ByteBuffer buf) {
            buf.put(b);
        }

        @Override
        public byte getType() {
            return type;
        }
    }

}
