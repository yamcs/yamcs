package org.yamcs.yarch;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.Test;


/**
 * This test case creates one table, three input streams which push data starting from a time0 but two of them going in 
 * the future and interleaving packets while the other one going in the past.
 *  
 * After a while a reader stream is created and reads the whole table in parallel with the writing streams.
 *  we want to check that the reader will get all the data from some point on and doesn't get past data
 * 
 *  
 * @author nm
 *
 */
public class ConcurrencyTest extends YarchTestCase {
    String cmd;
    int n=100*24*60; //x days with one packet per minute
    //int n=10;

    static final String engine="rocksdb"; 
    //static final String engine="tokyocabinet";


    class InputStreamFeeder implements Runnable {
	volatile int psent;
	volatile boolean finished;
	volatile boolean quitting = false;
	Stream stream1, stream2, stream3;

	InputStreamFeeder() throws Exception {
	    ydb.execute("create table testcrw (gentime timestamp, apidSeqCount int, packet binary, primary key(gentime,apidSeqCount)) engine "+engine+" partition by time(gentime('YYYY/MM'))");

	    ydb.execute("create stream testcrw_in1(gentime timestamp, apidSeqCount int, packet binary)");
	    ydb.execute("insert into testcrw select * from testcrw_in1");
	    stream1=ydb.getStream("testcrw_in1");

	    ydb.execute("create stream testcrw_in2(gentime timestamp, apidSeqCount int, packet binary)");
	    ydb.execute("insert into testcrw select * from testcrw_in2");
	    stream2=ydb.getStream("testcrw_in2");

	    ydb.execute("create stream testcrw_in3(gentime timestamp, apidSeqCount int, packet binary)");
	    ydb.execute("insert into testcrw select * from testcrw_in3");
	    stream3=ydb.getStream("testcrw_in3");
	}

	void send(Stream s, long time, int apidSeqCount, byte[] packet) {
	    Tuple t=new Tuple(s.getDefinition(), new Object[] {time, apidSeqCount, packet});
	    s.emitTuple(t);
	}

	@Override
	public void run() {
	    try {
		Calendar cal=Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		cal.set(2003,11,25,0,0,0);
		cal.set(Calendar.MILLISECOND, 0);
		long time0=cal.getTimeInMillis();

		for (psent=0; psent<n; psent++) {
		    //if(psent%1000==0) System.out.println("ConcurrencyTest write tuples: "+psent*2+" out of "+n*2);
		    cal.add(Calendar.MINUTE,1);
		    int apidSeqCount=psent;
		    long time=time0+psent*60L*1000;
		    //			System.err.println(i+" writing time "+new Date(time));
		    ByteBuffer bb=ByteBuffer.allocate(2000);
		    while(bb.remaining()>0) bb.putInt(psent);

		    if((psent&1)==0) {
			send(stream1, time, apidSeqCount, bb.array());
		    } else {
			send(stream2, time, apidSeqCount, bb.array());
		    }


		    time=time0-psent*60L*1000;
		    bb=ByteBuffer.allocate(2000);
		    while(bb.remaining()>0) bb.putInt(-psent);
		    send(stream3, time, -psent, bb.array());

		    if(quitting) break;
		}

	    } catch (Exception e) {
		System.err.println("got exception in the InputStreamFeeder: "+e);
	    }
	    finished=true;
	}
    }

    @Test
    public void testConcurrentReadAndWrite() throws Exception {
	InputStreamFeeder isf=new InputStreamFeeder();
	(new Thread(isf)).start();
	Thread.sleep(10000);//give some lead time to the writer streams

	Calendar cal=Calendar.getInstance(TimeZone.getTimeZone("UTC"));
	cal.set(2003,11,25,0,0,0);
	cal.set(Calendar.MILLISECOND, 0);
	final long time0=cal.getTimeInMillis();


	ydb.execute("create stream testcwr_out as select * from testcrw");

	Stream s=ydb.getStream("testcwr_out");
	final Semaphore semaphore=new Semaphore(0);
	s.addSubscriber(new StreamSubscriber() {
	    int i=0;
	    long starttime;

	    @Override
	    public void streamClosed(Stream stream) {
		assertTrue(i>10);                
		semaphore.release();
	    }

	    @Override
	    public void onTuple(Stream stream, Tuple tuple) {
		//if(i%1000==0) System.out.println("ConcurrencyTest read tuples: "+i);

		long time=(Long)tuple.getColumn(0);
		int apidSeqCount=(Integer) tuple.getColumn(1);
		byte[] b= (byte[]) tuple.getColumn(2);
		if(i==0) {
		    starttime=time;
		} else {
		    assertEquals(starttime+i*60L*1000L,time);
		    assertEquals((time-time0)/(60L*1000L),apidSeqCount);
		    assertEquals(2000, b.length);
		    ByteBuffer bb=ByteBuffer.wrap(b);
		    while(bb.remaining()>0) {
			int k=bb.getInt();
			assertEquals((time-time0)/(60L*1000L),k);
		    }
		}
		i++;
	    }
	});
	s.start();
	assertTrue(semaphore.tryAcquire(10, TimeUnit.SECONDS));

	isf.quitting=true;
	Thread.sleep(1000);
	//	assertEquals(time0+(n-1)*60L*1000L,time);
	execute("close stream testcrw_in1");
	execute("close stream testcrw_in2");
	execute("close stream testcrw_in3");
	execute("drop table testcrw");
    }
}
