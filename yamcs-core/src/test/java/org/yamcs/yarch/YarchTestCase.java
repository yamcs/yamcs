package org.yamcs.yarch;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.yamcs.YConfiguration;
import org.yamcs.utils.FileUtils;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.management.JMXService;
import org.junit.Before;
import org.junit.BeforeClass;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;
import org.yamcs.yarch.streamsql.ExecutionContext;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlParser;
import org.yamcs.yarch.streamsql.StreamSqlResult;


public abstract class YarchTestCase {
    protected StreamSqlParser parser;
    protected ExecutionContext context;
    protected YarchDatabase ydb;
    static boolean littleEndian;
    protected String instance;

    @BeforeClass 
    public static void setUpYarch() throws Exception {
	YConfiguration.setup(); //reset the prefix if maven runs multiple tests in the same java 

	YConfiguration config=YConfiguration.getConfiguration("yamcs");
	if(config.containsKey("littleEndian")) {
	    littleEndian=config.getBoolean("littleEndian");
	} else {
	    littleEndian=false;
	}
	JMXService.setup(false);
    }

    @Before
    public void setUp() throws Exception {
	YConfiguration config=YConfiguration.getConfiguration("yamcs");
	String dir = config.getString("dataDir");
	instance = "yarchtest_"+this.getClass().getSimpleName();
	context = new ExecutionContext(instance);

	File ytdir=new File(dir+"/"+instance);               
	
	FileUtils.deleteRecursively(ytdir.toPath());

	if(!ytdir.mkdirs()) throw new IOException("Cannot create directory "+ytdir);
	
	if(YarchDatabase.hasInstance(instance)) {	
	    YarchDatabase ydb = YarchDatabase.getInstance(instance);
	    RdbStorageEngine.removeInstance(ydb);	
	    YarchDatabase.removeInstance(instance);		
	}
	
	ydb = YarchDatabase.getInstance(instance);
    }

    
    
    
    
    protected StreamSqlResult execute(String cmd) throws StreamSqlException, ParseException {
	return ydb.execute(cmd);
    }

    protected List<Tuple> suckAll(String streamName) throws InterruptedException{
	final List<Tuple> tuples=new ArrayList<Tuple>();
	final Semaphore semaphore=new Semaphore(0);
	Stream s=ydb.getStream(streamName);
	s.addSubscriber(new StreamSubscriber() {

	    @Override
	    public void streamClosed(Stream stream) {
		semaphore.release();
	    }

	    @Override
	    public void onTuple(Stream stream, Tuple tuple) {
		tuples.add(tuple);
	    }
	});
	s.start();
	semaphore.acquire();
	return tuples;
    }
}
