package org.yamcs.yarch.rocksdb;
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
import org.yamcs.utils.FileUtils;
import org.yamcs.utils.TimeEncoding;


public class HistogramDbTest {
    byte[] grp1="aaaaaaa".getBytes();
    byte[] grp2="b".getBytes();


    private void assertRecEquals(byte[] columnv, long start, long stop, int num, HistogramRecord r) {
	assertTrue(Arrays.equals(columnv, r.getColumnv()));
	assertEquals(start, r.getStart());
	assertEquals(stop, r.getStop());
	assertEquals(num, r.getNumTuples());
    }

    @Before
    public void setUp() {
	TimeEncoding.setUp();
    }



    @Test
    public void testPpIndex() throws IOException {
	long g=3600*1000;
	String path="/tmp/ppindex-test";
	String colName = "a";
	File f= new File(path);
	if(f.exists()) {
	    FileUtils.deleteRecursively(new File(path).toPath());
	}
	(new File(path)).delete();
	YarchDatabase ydb=YarchDatabase.getInstance(this.getClass().toString());
	RdbHistogramDb db=new RdbHistogramDb(ydb, path, false);
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

	//db.printDb(colName, new TimeInterval(), -1);
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
