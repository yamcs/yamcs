package org.yamcs.yarch.tokyocabinet;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.yamcs.TimeInterval;
import org.yamcs.yarch.HistogramDb.HistogramIterator;
import org.yamcs.yarch.HistogramRecord;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.tokyocabinet.TcHistogramDb;
import org.yamcs.utils.TimeEncoding;


public class HistogramDbTest {
    byte[] grp1="aaaaaaa".getBytes();
    byte[] grp2="b".getBytes();
    
    private void assertRecEquals(byte[] columnv, long start, long stop, int num, HistogramRecord r) {
        assertTrue(Arrays.equals(columnv, r.getColumnv()));
        assertEquals(num, r.getNumTuples());
        assertEquals(start, r.getStart());
        assertEquals(stop, r.getStop());
    
    }

    @Before
    public void setUp() {
        TimeEncoding.setUp();
    }


    @Test
    public void testPpIndex() throws IOException {
        long g=3600*1000;
        String filepath="/tmp/ppindex-test";
        String colName = "a";
        (new File(filepath+"#a.tcb")).delete();
        YarchDatabase ydb=YarchDatabase.getInstance(this.getClass().toString());
        TcHistogramDb db=new TcHistogramDb(ydb, filepath, false);
        HistogramIterator it=db.getIterator(colName, new TimeInterval(), -1);
        assertNull(it.getNextRecord());

        db.addValue(colName, grp1, 1000);


        db.addValue(colName, grp2, 1000);
        long now=System.currentTimeMillis();

        db.addValue(colName, grp1, now);

        //    index.printDb(-1, -1, -1);
        //   index.close();

        db.addValue(colName, grp1, g+1000);
        db.addValue(colName, grp1, g+2000);
        db.addValue(colName, grp2, g+1000);
        db.addValue(colName, grp2, g+2000);
        db.addValue(colName, grp2, g+5000);
        db.addValue(colName, grp2, g+130000);
        db.addValue(colName, grp1, g+4000);
        db.addValue(colName, grp1, g+3000);

      //  db.printDb(colName, new TimeInterval(), -1);
        it=db.getIterator(colName, new TimeInterval(), -1);
        assertRecEquals(grp1, 1000, 1000, 1, it.getNextRecord());
        assertRecEquals(grp2, 1000, 1000, 1, it.getNextRecord());
        assertRecEquals(grp1, g+1000, g+4000, 4, it.getNextRecord());
        assertRecEquals(grp2, g+1000, g+2000, 2, it.getNextRecord());
        assertRecEquals(grp2, g+5000, g+5000, 1, it.getNextRecord());
        assertRecEquals(grp2, g+130000, g+130000, 1, it.getNextRecord());
        assertRecEquals(grp1, now, now, 1, it.getNextRecord());
        assertNull(it.getNextRecord());

        it=db.getIterator(colName, new TimeInterval(), 5000);
        assertRecEquals(grp1, 1000, 1000, 1, it.getNextRecord());
        assertRecEquals(grp2, 1000, 1000, 1, it.getNextRecord());
        assertRecEquals(grp1, g+1000, g+4000, 4, it.getNextRecord());
        assertRecEquals(grp2, g+1000, g+5000, 3, it.getNextRecord());
        assertRecEquals(grp2, g+130000, g+130000, 1, it.getNextRecord());
        assertRecEquals(grp1, now, now, 1, it.getNextRecord());
        assertNull(it.getNextRecord());
    }	
}
