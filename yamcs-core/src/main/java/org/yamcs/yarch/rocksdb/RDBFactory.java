package org.yamcs.yarch.rocksdb;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.rocksdb.FlushOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * manufacturer of TCB databases. It runs a thread that synchronizes them from time to time and closes 
 * those that have not been used in a while
 * @author nm
 *
 */
public class RDBFactory implements Runnable {
	HashMap<String, DbAndAccessTime> databases=new HashMap<String, DbAndAccessTime>();
	
	static Logger log=LoggerFactory.getLogger(RDBFactory.class.getName());
	static HashMap<String, RDBFactory> instances=new HashMap<String, RDBFactory>(); 
	static int maxOpenDbs=200;
	ScheduledThreadPoolExecutor scheduler;
	
	public static FlushOptions flushOptions = new FlushOptions();
	/**
	 * use default visibility to be able to create a separate one from the unit test
	 */
	RDBFactory() {
		flushOptions.setWaitForFlush(false);
		scheduler=new ScheduledThreadPoolExecutor(1,new ThreadFactory() {//the default thread factory creates non daemon threads 
            @Override
            public Thread newThread(Runnable r) {
                Thread t=new Thread(r);
                t.setDaemon(true);
                t.setName("TcbFactory-sync");
                return t;
            }
        });
		scheduler.scheduleAtFixedRate(this, 1, 1, TimeUnit.MINUTES);
		Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownHook()));
	}
	
	public static synchronized RDBFactory getInstance(String instance) {
		RDBFactory rdbFactory=instances.get(instance); 
		if(rdbFactory==null) {
			rdbFactory=new RDBFactory();
			instances.put(instance, rdbFactory);
		}
		return rdbFactory;
	}
	
	public RocksDB getRdb(String dir, boolean compressed, boolean readonly) throws RocksDBException{
		return rdb(dir, compressed, readonly);
	}
	
	
	private synchronized RocksDB rdb(String dir, boolean compressed, boolean readonly) throws RocksDBException{
		DbAndAccessTime daat=databases.get(dir);
		if(daat==null) {
			if(databases.size()>=maxOpenDbs) { //close the db with the oldest timestamp
				long min=Long.MAX_VALUE;
				String minFile=null;
				for(Entry<String, DbAndAccessTime> entry:databases.entrySet()) {
					DbAndAccessTime daat1=entry.getValue();
					if((daat1.refcount==0)&&(daat1.lastAccess<min)) {
						min=daat1.lastAccess;
						minFile=entry.getKey();
					}
				}
				if(minFile!=null) {
					log.debug("Closing the database: "+minFile+" to not have more than "+maxOpenDbs+" open databases");
					daat=databases.remove(minFile);
					daat.db.close();
				}
			}
			RocksDB db = RocksDB.open(dir);
			log.debug("Creating or opening RDB "+dir+" total tcb files open: "+databases.size());
			
			
			daat=new DbAndAccessTime(db, dir, readonly);
			databases.put(dir, daat);
		}
		
		daat.lastAccess=System.currentTimeMillis();
		daat.refcount++;
		return daat.db;
	}
	
	public void delete(String file) {
		del(file);
	}

	private synchronized void del(String dir) {
		DbAndAccessTime daat=databases.remove(dir);
		if(daat!=null) {
			daat.db.close();
		}
	}
	
	@Override
	public synchronized void run() {
		//remove all the databases not accessed in the last 5 min and sync the others
		long time=System.currentTimeMillis();
		Iterator<Map.Entry<String, DbAndAccessTime>>it=databases.entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry<String, DbAndAccessTime> entry=it.next();
			DbAndAccessTime daat=entry.getValue();
			if((daat.refcount==0) && ( time-daat.lastAccess>300000)) {
				log.debug("Closing the database: "+entry.getKey());
				daat.db.close();
				it.remove();
			} else {
			    if(!daat.readonly) {
			        try {
			            daat.db.flush(flushOptions);
			        } catch (RocksDBException e) {
			            log.error("Got exception while closing the database "+entry.getKey()+": ", e);
			        }
			    }
			}
		}
	}
	private synchronized void shutdown() {
		log.debug("shutting down, closing all the databases "+databases.keySet());
		Iterator<Map.Entry<String, DbAndAccessTime>>it=databases.entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry<String, DbAndAccessTime> entry=it.next();
 				entry.getValue().db.close();
				it.remove();
		}
	}
	
	class ShutdownHook implements Runnable {
		@Override
		public void run() {
			shutdown();
		}
	}

	public synchronized void dispose(String dbdir) {
	  //  System.out.println("here ybdb.path: "+ybdb.path());
	    DbAndAccessTime daat=databases.get(dbdir);
	    daat.lastAccess=System.currentTimeMillis();
	    daat.refcount--;
	}
	
}


class DbAndAccessTime {
	RocksDB db;
	long lastAccess;
	int refcount=0;
	boolean readonly;
	String dir;
	
	
	public DbAndAccessTime(RocksDB db, String dir, boolean readonly) {
		this.db=db;
		this.readonly=readonly;
		this.dir = dir;
	}
}
