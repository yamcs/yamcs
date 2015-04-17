package org.yamcs.yarch.tokyocabinet;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.Before;
import org.junit.Test;
import org.yamcs.yarch.tokyocabinet.YBDB;
import org.yamcs.yarch.tokyocabinet.YBDBCUR;

import tokyocabinet.BDB;



public class YBDBTest {

    YBDB db;
    static final int n=1000000;

    @Before
    public void setUp() throws IOException {
	db=new YBDB();
	db.tune(0, 0, 0, 0, 0, BDB.TDEFLATE);
    }

    class Writer implements Runnable {
	public void run() {
	    byte[] key=new byte[4];
	    ByteBuffer bbkey=ByteBuffer.wrap(key);
	    byte[] val=new byte[2000];
	    for(int i=0;i<val.length;i++) {
		val[i]=(byte)i;
	    }
	    for(int i=0;i<n; i++) {
		bbkey.putInt(0, i);
	//	if(i%10000==0)System.err.println("writing "+i);
		try {
		    db.putkeep(key, val);
		} catch (IOException e) {
		    System.err.println("failed to put: "+e);
		}
	    }
	}
    }

    @Test
    public void testOneWriterOneReader() throws Exception{
	String f="/tmp/ybdbtest.tcb";
	(new File(f)).delete();
	db.open(f, BDB.OWRITER|BDB.OCREAT);
	(new Thread(new Writer())).start();
	Thread.sleep(100);
	YBDBCUR cursor=db.openCursor();
	cursor.first();
	int i=0;
	while(true) {
	    ByteBuffer bbk=ByteBuffer.wrap(cursor.key());
	    int k=bbk.getInt();
	    byte[] val=cursor.val();
	    for(int j=0;j<val.length;j++) assertEquals((byte)j,val[j]);
	   // if(k%10000==0) System.err.println("***********************read "+k);
	    assertEquals(i,k);
	    i++;
	    if(!cursor.next()) break;
	}
	assertEquals(n,i);
    }

}
