package org.yamcs.yarch.rocksdb2;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.rocksdb.FlushOptions;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * manufacturer of RDB databases. It runs a thread that synchronises them from time to time and closes 
 * those that have not been used in a while
 * @author nm
 *
 */
public class RDBFactory implements Runnable {
    HashMap<String, DbAndAccessTime> databases=new HashMap<String, DbAndAccessTime>();

    static Logger log = LoggerFactory.getLogger(RDBFactory.class.getName());
    static HashMap<String, RDBFactory> instances = new HashMap<String, RDBFactory>(); 
    static int maxOpenDbs=200;
    ScheduledThreadPoolExecutor scheduler;
    final String instance;
    public static FlushOptions flushOptions = new FlushOptions();


    public static synchronized RDBFactory getInstance(String instance) {
        RDBFactory rdbFactory=instances.get(instance); 
        if(rdbFactory==null) {
            rdbFactory=new RDBFactory(instance);
            instances.put(instance, rdbFactory);
        }
        return rdbFactory;
    }

    /**
     * Opens or create a database.
     * 
     * 
     * @param absolutePath - absolute path - should be a directory
     * @param readonly
     * @return
     * @throws IOException
     */
    public YRDB getRdb(String absolutePath,  boolean readonly) throws IOException{
        return rdb(absolutePath, readonly);
    }

    /**
     * use default visibility to be able to create a separate one from the unit test
     */
    RDBFactory(String instance) {
        this.instance = instance;
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

    private synchronized YRDB rdb(String absolutePath, boolean readonly) throws IOException {
        DbAndAccessTime daat = databases.get(absolutePath);
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
            log.debug("Creating or opening RDB "+absolutePath+" total rdb open: "+databases.size());
            YRDB db;
            try {
                db = new YRDB(absolutePath);
            } catch (RocksDBException e) {
                throw new IOException(e);
            }


            daat=new DbAndAccessTime(db, absolutePath, readonly);
            databases.put(absolutePath, daat);
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
    synchronized void shutdown() {
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

    public synchronized void dispose(YRDB rdb) {		
        DbAndAccessTime daat=databases.get(rdb.getPath());
        daat.lastAccess=System.currentTimeMillis();
        daat.refcount--;
    }

    /**
     * Close the DB if open (called when dropping the table)
     * 
     * @param absolutePath
     */
    public synchronized void closeIfOpen(String absolutePath) {
        DbAndAccessTime daat = databases.remove(absolutePath);
        if(daat!=null) {
            daat.db.close();
        }		
    }	
}


class DbAndAccessTime {
    YRDB db;
    long lastAccess;
    int refcount=0;
    boolean readonly;
    String dir;


    public DbAndAccessTime(YRDB db, String dir, boolean readonly) {
        this.db=db;
        this.readonly=readonly;
        this.dir = dir;
    }
}
