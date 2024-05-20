package org.yamcs.cfdp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.python.bouncycastle.util.Arrays;
import org.yamcs.cfdp.pdu.FileDataPacket;
import org.yamcs.cfdp.pdu.SegmentRequest;

public class DataFileTest {
    static int n = 100;
    static byte[] data = new byte[n];

    @BeforeAll
    static public void beforeClass() {
        for (int i = 0; i < n; i++) {
            data[i] = (byte) i;
        }
    }

    @Test
    public void test1() {
        DataFile df = new DataFile(n);
        df.addSegment(getSegment(0, 3));

        List<SegmentRequest> lmissing = df.getMissingChunks();
        assertEquals(1, lmissing.size());
        verifyEquals(3, n, lmissing.get(0));

        df.addSegment(getSegment(5, 5));

        lmissing = df.getMissingChunks();
        assertEquals(2, lmissing.size());
        verifyEquals(3, 5, lmissing.get(0));
        verifyEquals(10, n, lmissing.get(1));

        assertFalse(df.isComplete());
        assertEquals(8, df.getReceivedSize());

        df.addSegment(getSegment(3, 2));
        df.addSegment(getSegment(10, n - 10));

        assertTrue(df.isComplete());
        lmissing = df.getMissingChunks();
        assertEquals(0, lmissing.size());

        assertArrayEquals(data, df.getData());
    }

    @Test
    public void test2() {
        DataFile df = new DataFile(n);
        df.addSegment(getSegment(0, 3));
        df.addSegment(getSegment(1, 3));
        assertEquals(1, df.dataFileSegments.size());
        
        List<SegmentRequest> lmissing = df.getMissingChunks();
        assertEquals(1, lmissing.size());
        verifyEquals(4, n, lmissing.get(0));

        verify(df);
    }

    @Test
    public void test3() {
        DataFile df = new DataFile(n);
        df.addSegment(getSegment(0, 3));
        df.addSegment(getSegment(0, 3));
        df.addSegment(getSegment(0, 4));
        List<SegmentRequest> lmissing = df.getMissingChunks();
        assertEquals(1, lmissing.size());
        verifyEquals(4, n, lmissing.get(0));

        verify(df);
    }

    @Test
    public void test4() {
        DataFile df = new DataFile(n);
        df.addSegment(getSegment(10, 10));
        List<SegmentRequest> lmissing = df.getMissingChunks();
        assertEquals(2, lmissing.size());
        verifyEquals(0, 10, lmissing.get(0));
        verifyEquals(20, n, lmissing.get(1));

        df.addSegment(getSegment(0, 10));
        lmissing = df.getMissingChunks();
        assertEquals(1, lmissing.size());
        verify(df);
    }

    @Test
    public void test5() {
        DataFile df = new DataFile(n);
        df.addSegment(getSegment(10, 10));
        df.addSegment(getSegment(0, 12));
        List<SegmentRequest> lmissing = df.getMissingChunks();
        assertEquals(1, lmissing.size());
        verifyEquals(20, n, lmissing.get(0));
        verify(df);
    }

    @Test
    public void test6() {
        DataFile df = new DataFile(n);
        df.addSegment(getSegment(0, 10));
        df.addSegment(getSegment(20, 10));
        df.addSegment(getSegment(10, 10));
        List<SegmentRequest> lmissing = df.getMissingChunks();
        assertEquals(1, lmissing.size());
        verifyEquals(30, n, lmissing.get(0));
        verify(df);
    }

    @Test
    public void test7() {
        DataFile df = new DataFile(n);
        df.addSegment(getSegment(0, 10));
        df.addSegment(getSegment(20, 10));
        df.addSegment(getSegment(5, 15));
        List<SegmentRequest> lmissing = df.getMissingChunks();
        assertEquals(1, lmissing.size());
        verifyEquals(30, n, lmissing.get(0));
        verify(df);
    }

    @Test
    public void test8() {
        DataFile df = new DataFile(n);
        df.addSegment(getSegment(0, 10));
        df.addSegment(getSegment(20, 10));
        df.addSegment(getSegment(5, 20));
        List<SegmentRequest> lmissing = df.getMissingChunks();
        assertEquals(1, lmissing.size());
        verifyEquals(30, n, lmissing.get(0));
        verify(df);
    }

    private FileDataPacket getSegment(int offset, int length) {
        return new FileDataPacket(Arrays.copyOfRange(data, offset, offset + length), offset, null);
    }

    private void verifyEquals(long expectedStart, long expectedEnd, SegmentRequest sr) {
        assertEquals(expectedStart, sr.getSegmentStart());
        assertEquals(expectedEnd, sr.getSegmentEnd());
    }

    private void verify(DataFile df) {
        byte[] data1 = df.getData();

        for (DataFile.Segment dfs : df.dataFileSegments) {
            for (int i = (int) dfs.start; i < dfs.end; i++) {
                assertEquals(data[i], data1[i]);
            }
        }
    }
}
