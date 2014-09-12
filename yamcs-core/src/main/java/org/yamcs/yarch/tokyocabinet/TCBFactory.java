package org.yamcs.yarch.tokyocabinet;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tokyocabinet.BDB;

/**
 * manufacturer of TCB databases. It runs a thread that synchronizes them from time to time and closes 
 * those that have not been used in a while
 * @author nm
 *
 */
public class TCBFactory implements Runnable {
	HashMap<String, DbAndAccessTime> databases=new HashMap<String, DbAndAccessTime>();
	
	static Logger log=LoggerFactory.getLogger(TCBFactory.class.getName());
	static TCBFactory instance=new TCBFactory(); 
	static int maxOpenDbs=200;
	ScheduledThreadPoolExecutor scheduler;
	
	/**
	 * use default visibility to be able to create a separate one from the unit test
	 */
	TCBFactory() {
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
	
	public static TCBFactory getInstance() {
	    return instance;
	}
	
	public YBDB getTcb(String file, boolean compressed, boolean readonly) throws IOException{
		return tcb(file, compressed, readonly);
	}
	
	
	private synchronized YBDB tcb(String file, boolean compressed, boolean readonly) throws IOException{
		DbAndAccessTime daat=databases.get(file);
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
			YBDB db=new YBDB();
			if(compressed)db.tune(0, 0, 0, 0, 0, BDB.TDEFLATE);
			db.setcache(1,512);
			log.debug("Creating or opening tcb "+file+" total tcb files open: "+databases.size());
			if(readonly) {
			    db.open(file, BDB.OREADER|BDB.ONOLCK);
			} else {
			    db.open(file,BDB.OWRITER|BDB.OCREAT);
			}
			daat=new DbAndAccessTime(db,readonly);
			databases.put(file, daat);
		}
		
		daat.lastAccess=System.currentTimeMillis();
		daat.refcount++;
		return daat.db;
	}
	
	public void delete(String file) {
		del(file);
	}

	private synchronized void del(String file) {
		DbAndAccessTime daat=databases.remove(file);
		if(daat!=null) {
			try {
				daat.db.close();
			} catch (IOException e) {
				log.error("Got exception when closing the database "+file);
			}
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
				try {
					log.debug("Closing the database: "+entry.getKey());
					daat.db.close();
				} catch (IOException e) {
					log.error("Got exception while closing the database "+entry.getKey()+": ", e);
				}
				it.remove();
			} else {
			    if(!daat.readonly) {
			        try {
			            daat.db.sync();
			        } catch (IOException e) {
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
			try {
 				entry.getValue().db.close();
				it.remove();
			} catch (IOException e) {
				log.error("Got exception while closing the database "+entry.getKey()+": ", e);
			}
		}
	}
	
	class ShutdownHook implements Runnable {
		@Override
		public void run() {
			shutdown();
		}
	}

	public synchronized void dispose(YBDB ybdb) {
	  //  System.out.println("here ybdb.path: "+ybdb.path());
	    DbAndAccessTime daat=databases.get(ybdb.path());
	    daat.lastAccess=System.currentTimeMillis();
	    daat.refcount--;
	}
}


class DbAndAccessTime {
	YBDB db;
	long lastAccess;
	int refcount=0;
	boolean readonly;
	public DbAndAccessTime(YBDB db, boolean readonly) {
		this.db=db;
		this.readonly=readonly;
	}
}
