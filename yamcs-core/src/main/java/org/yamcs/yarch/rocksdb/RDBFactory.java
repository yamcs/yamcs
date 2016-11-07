package org.yamcs.yarch.rocksdb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.rocksdb.BackupEngine;
import org.rocksdb.BackupableDBOptions;
import org.rocksdb.Env;
import org.rocksdb.FlushOptions;
import org.rocksdb.RestoreOptions;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.cli.Backup;

/**
 * manufacturer of RDB databases. It runs a thread that synchronises them from time to time and closes 
 * those that have not been used in a while
 * @author nm
 *
 */
public class RDBFactory implements Runnable {
    HashMap<String, DbAndAccessTime> databases=new HashMap<String, DbAndAccessTime>();

    static Logger log = LoggerFactory.getLogger(RDBFactory.class.getName());
    static HashMap<String, RDBFactory> instances=new HashMap<String, RDBFactory>(); 
    static int maxOpenDbs = 200;
    ScheduledThreadPoolExecutor scheduler;
    final String instance;
    public static FlushOptions flushOptions = new FlushOptions();

    //use this when the db is open for the backup; if the same db is open with another serializer, then this one will be dropped 
    DummyColumnFamilySerializer dummyCfSerializer = new DummyColumnFamilySerializer();

    public static synchronized RDBFactory getInstance(String instance) {
        RDBFactory rdbFactory = instances.get(instance); 
        if(rdbFactory==null) {
            rdbFactory = new RDBFactory(instance);
            instances.put(instance, rdbFactory);
        }
        return rdbFactory;
    }

    /**
     * Opens or create a database.
     * 
     * 
     * @param absolutePath - absolute path - should be a directory
     * @param cfSerializer - the class that converts between column families and byte arrays
     * @param readonly - open in readonly mode; if the database is open in readwrite mode, it will be returned like that
     * @return the database created or opened
     * @throws IOException
     */
    public YRDB getRdb(String absolutePath, ColumnFamilySerializer cfSerializer, boolean readonly) throws IOException{
        return rdb(absolutePath, cfSerializer, readonly);
    }

    /**
     * use default visibility to be able to create a separate one from the unit test
     */
    RDBFactory(String instance) {
        this.instance = instance;
        flushOptions.setWaitForFlush(false);
        scheduler = new ScheduledThreadPoolExecutor(1,new ThreadFactory() {//the default thread factory creates non daemon threads 
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

    private synchronized YRDB rdb(String absolutePath, ColumnFamilySerializer cfSerializer, boolean readonly) throws IOException {
        DbAndAccessTime daat = databases.get(absolutePath);
        if(daat==null) {
            if(databases.size()>=maxOpenDbs) { //close the db with the oldest timestamp
                long min=Long.MAX_VALUE;
                String minFile=null;
                for(Entry<String, DbAndAccessTime> entry:databases.entrySet()) {
                    DbAndAccessTime daat1 = entry.getValue();
                    if((daat1.refcount==0)&&(daat1.lastAccess<min)) {
                        min = daat1.lastAccess;
                        minFile = entry.getKey();
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
                db = new YRDB(absolutePath, cfSerializer);
            } catch (RocksDBException e) {
                throw new IOException(e);
            }


            daat = new DbAndAccessTime(db, absolutePath, readonly);
            databases.put(absolutePath, daat);
        }
        daat.lastAccess = System.currentTimeMillis();
        daat.refcount++;
        if((daat.db.getColumnFamilySerializer() == dummyCfSerializer) && (cfSerializer!= dummyCfSerializer)) {
            daat.db.setColumnFamilySerializer(cfSerializer);
        }
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
        Iterator<Map.Entry<String, DbAndAccessTime>>it = databases.entrySet().iterator();
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
        Iterator<Map.Entry<String, DbAndAccessTime>>it = databases.entrySet().iterator();
        while(it.hasNext()) {
            Map.Entry<String, DbAndAccessTime> entry = it.next();
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
        DbAndAccessTime daat = databases.get(rdb.getPath());
        daat.lastAccess = System.currentTimeMillis();
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

    /**
     * Get the database which is already open or null if it is not open
     * @param absolutePath the absoulte path of the database to be returned
     * @return the database object
     */
    public synchronized YRDB getOpenRdb(String absolutePath) {
        DbAndAccessTime daat = databases.get(absolutePath);
        if(daat==null) return null;
        daat.lastAccess = System.currentTimeMillis();
        daat.refcount++;
        return daat.db;
    }

    public synchronized List<String> getOpenDbPaths() {
        List<String> l = new ArrayList<String>(databases.keySet());
        return l;
    }

    /**
     * immediately closes the database
     * 
     * @param yrdb
     */
    public synchronized void close(YRDB yrdb) {
        databases.remove(yrdb.getPath());
        yrdb.getDb().close();
    }

    /**
     * Performs a backup of the database to the given directory
     * 
     * @param dbpath
     * @param backupDir
     * @return a future that can be used to know when the backup has finished and if there was any error
     */
    public CompletableFuture<Void> doBackup(String dbpath, String backupDir) {
        CompletableFuture<Void> cf = new CompletableFuture<Void>();
        scheduler.execute(()->{
            YRDB db = null;
            try {
                Backup.verifyBackupDirectory(backupDir, false);
                BackupableDBOptions opt = new BackupableDBOptions(backupDir);
                BackupEngine backupEngine = BackupEngine.open(Env.getDefault(), opt);
                db = getRdb(dbpath, dummyCfSerializer, false);
                backupEngine.createNewBackup(db.getDb());

                backupEngine.close();
                opt.close();
                cf.complete(null);
            } catch (Exception e) {
                cf.completeExceptionally(e);
            } finally { 
                if(db!=null) {
                    dispose(db);
                }
            }
        });

        return cf;
    }


    public CompletableFuture<Void> restoreBackup(String backupDir, String dbPath) {
        CompletableFuture<Void> cf = new CompletableFuture<Void>();
        scheduler.execute(()->{
            try {
                BackupableDBOptions opt = new BackupableDBOptions(backupDir);
                BackupEngine backupEngine = BackupEngine.open(Env.getDefault(), opt);
                RestoreOptions restoreOpt = new RestoreOptions(false);
                backupEngine.restoreDbFromLatestBackup(dbPath, dbPath, restoreOpt);

                backupEngine.close();
                opt.close();
                restoreOpt.close();
                cf.complete(null);
            } catch (Exception e) {
                cf.completeExceptionally(e);
            } finally { 
            }
        });

        return cf;
    }
    
    public CompletableFuture<Void> restoreBackup(int backupId, String backupDir, String dbPath) {
        CompletableFuture<Void> cf = new CompletableFuture<Void>();
        scheduler.execute(()->{
            
            try {
                BackupableDBOptions opt = new BackupableDBOptions(backupDir);
                BackupEngine backupEngine = BackupEngine.open(Env.getDefault(), opt);
                RestoreOptions restoreOpt = new RestoreOptions(false);
                if(backupId==-1) {
                    backupEngine.restoreDbFromLatestBackup(dbPath, dbPath, restoreOpt);
                } else {
                    backupEngine.restoreDbFromBackup(backupId, dbPath, dbPath, restoreOpt);
                }

                backupEngine.close();
                opt.close();
                restoreOpt.close();
                cf.complete(null);
            } catch (Exception e) {
                cf.completeExceptionally(e);
            } finally { 
            }
        });

        return cf;
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
