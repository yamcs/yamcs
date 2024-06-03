package org.yamcs.yarch.rocksdb;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.rocksdb.BackupEngine;
import org.rocksdb.BackupEngineOptions;
import org.rocksdb.Env;
import org.rocksdb.FlushOptions;
import org.rocksdb.RestoreOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.BackupUtils;

/**
 * manufacturer of RDB databases residing under a sub-directory which is normally the
 * {@link org.yamcs.yarch.rocksdb.Tablespace#getDataDir()}.
 * 
 * It runs a thread that synchronises them from time to time and closes those that have not been used in a while
 * 
 * @author nm
 *
 */
public class RDBFactory implements Runnable {
    HashMap<String, YRDB> databases = new HashMap<>();

    static Logger log = LoggerFactory.getLogger(RDBFactory.class.getName());
    static HashMap<String, RDBFactory> instances = new HashMap<>();
    static int maxOpenDbs = 200;
    ScheduledThreadPoolExecutor executor;
    final String dataDir;
    public static FlushOptions flushOptions = new FlushOptions();
    static boolean registerShutdownHooks = true;

    /**
     * Opens or create a database at a given relative path
     * 
     * 
     * @param relativePath
     *            Relative path to the dataDir. Should be a directory.
     * @param readonly
     *            Open in readonly mode; if the database is open in readwrite mode, it will be returned like that
     * @return the database created or opened
     * @throws IOException
     */
    public YRDB getRdb(String relativePath, boolean readonly) throws IOException {
        return rdb(relativePath, readonly);
    }

    /**
     * Opens or creates a database at the root dataDir
     */
    public YRDB getRdb(boolean readonly) throws IOException {
        return rdb("", readonly);
    }

    /**
     * use default visibility to be able to create a separate one from the unit test
     *
     * @param executor
     */
    RDBFactory(String dataDir, ScheduledThreadPoolExecutor executor) {
        this.dataDir = dataDir;
        this.executor = executor;
        flushOptions.setWaitForFlush(false);

        executor.scheduleAtFixedRate(this, 1, 1, TimeUnit.MINUTES);
        if (registerShutdownHooks) {
            Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownHook()));
        }
    }

    private synchronized YRDB rdb(String relativePath, boolean readonly) throws IOException {
        YRDB db = databases.get(relativePath);
        if (db == null) {
            if (databases.size() >= maxOpenDbs) { // close the db with the oldest timestamp
                long min = Long.MAX_VALUE;
                String minFile = null;
                for (Entry<String, YRDB> entry : databases.entrySet()) {
                    YRDB rdb1 = entry.getValue();
                    if ((rdb1.refcount == 0) && (rdb1.lastAccessTime < min)) {
                        min = rdb1.lastAccessTime;
                        minFile = entry.getKey();
                    }
                }
                if (minFile != null) {
                    log.debug("Closing the database: '{}' to not have more than {} open databases", minFile,
                            maxOpenDbs);
                    YRDB rdb = databases.remove(minFile);
                    rdb.close();
                }
            }
            String absolutePath = dataDir;
            if (!relativePath.isEmpty()) {
                absolutePath += File.separator + relativePath;
            }
            log.debug("Opening RDB {} (top dir has {} open already)", absolutePath, databases.size());
            try {
                db = new YRDB(absolutePath, readonly);
                log.debug("Opened {} with ~{} records", absolutePath, db.getApproxNumRecords());
            } catch (RocksDBException e) {
                throw new IOException(e);
            }

            databases.put(relativePath, db);
        }
        db.lastAccessTime = System.currentTimeMillis();
        db.refcount++;
        return db;
    }

    public void delete(String file) {
        del(file);
    }

    private synchronized void del(String dir) {
        YRDB db = databases.remove(dir);
        if (db != null) {
            db.close();
        }
    }

    @Override
    public synchronized void run() {
        // remove all the databases not accessed in the last 5 min and sync the others
        long time = System.currentTimeMillis();
        Iterator<Map.Entry<String, YRDB>> it = databases.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, YRDB> entry = it.next();
            YRDB db = entry.getValue();
            if ((db.refcount == 0) && (time - db.lastAccessTime > 300000)) {
                log.debug("Closing the database: {}", entry.getKey());
                db.close();
                it.remove();
            }
        }
    }

    synchronized void shutdown() {
        log.debug("Shutting down. Closing {} databases under {}: {}", databases.size(), dataDir, databases.keySet());
        Iterator<Map.Entry<String, YRDB>> it = databases.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, YRDB> entry = it.next();
            entry.getValue().close();
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
        rdb.lastAccessTime = System.currentTimeMillis();
        rdb.refcount--;
    }

    /**
     * Close the DB if open (called when dropping the table)
     * 
     * @param relativePath
     */
    public synchronized void closeIfOpen(String relativePath) {
        YRDB db = databases.remove(relativePath);
        if (db != null) {
            db.close();
        }
    }

    /**
     * Set whether shutdown hooks are registered on each created {@link RDBFactory}. By default this is enabled.
     */
    public static void setRegisterShutdownHooks(boolean registerShutdownHooks) {
        RDBFactory.registerShutdownHooks = registerShutdownHooks;
    }

    /**
     * Get the root database if it's open, otherwise return null
     * 
     */
    public YRDB getOpenRdb() {
        return getOpenRdb("");
    }

    /**
     * Get the database which is already open or null if it is not open
     * 
     * @param relativePath
     *            path of the database to be returned
     * @return the database object
     */
    public synchronized YRDB getOpenRdb(String relativePath) {
        YRDB db = databases.get(relativePath);
        if (db == null) {
            return null;
        }
        db.lastAccessTime = System.currentTimeMillis();
        db.refcount++;
        return db;
    }

    public synchronized List<String> getOpenDbPaths() {
        return new ArrayList<>(databases.keySet());
    }

    /**
     * Get the list of all open databases increasing the reference count but optionally not updating the last access
     * time
     */
    public synchronized List<YRDB> getOpenDbs(boolean updateAccessTime) {
        List<YRDB> l = new ArrayList<>();
        var now = System.currentTimeMillis();
        for (YRDB db : databases.values()) {
            db.refcount++;
            if (updateAccessTime) {
                db.lastAccessTime = now;
            }
            l.add(db);
        }
        return l;
    }

    /**
     * immediately closes the database
     * 
     * @param yrdb
     */
    public synchronized void close(YRDB yrdb) {
        databases.values().remove(yrdb);
        yrdb.close();
    }

    /**
     * Performs backup of the root database to a given directory
     * 
     * @param backupDir
     * @return a future that can be used to know when the backup has finished and if there was any error
     */
    public CompletableFuture<Void> doBackup(String backupDir) {
        return doBackup("", backupDir);
    }

    /**
     * Performs a backup of the database to the given directory
     * 
     * @param relativePath
     * @param backupDir
     * @return a future that can be used to know when the backup has finished and if there was any error
     */
    public CompletableFuture<Void> doBackup(String relativePath, String backupDir) {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        executor.execute(() -> {
            YRDB db = null;
            try {
                BackupUtils.verifyBackupDirectory(backupDir, false);
            } catch (IOException e) {
                log.warn("Invalid backup directory: {} ", e.toString());
                cf.completeExceptionally(e);
                return;
            }
            try (BackupEngineOptions opt = new BackupEngineOptions(backupDir);
                    BackupEngine backupEngine = BackupEngine.open(Env.getDefault(), opt);) {
                db = getRdb(relativePath, false);
                backupEngine.createNewBackup(db.getDb());
                cf.complete(null);
            } catch (Exception e) {
                log.warn("Got error when creating the backup: {} ", e.getMessage());
                cf.completeExceptionally(e);
            } finally {
                if (db != null) {
                    dispose(db);
                }
            }
        });

        return cf;
    }

    public CompletableFuture<Void> restoreBackup(String backupDir, String relativePath) {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        executor.execute(() -> {
            try (BackupEngineOptions opt = new BackupEngineOptions(backupDir);
                    BackupEngine backupEngine = BackupEngine.open(Env.getDefault(), opt);
                    RestoreOptions restoreOpt = new RestoreOptions(false);) {
                String absolutePath = getAbsolutePath(relativePath);
                backupEngine.restoreDbFromLatestBackup(absolutePath, absolutePath, restoreOpt);

                cf.complete(null);
            } catch (Exception e) {
                cf.completeExceptionally(e);
            } finally {
            }
        });

        return cf;
    }

    private String getAbsolutePath(String relativePath) {
        return dataDir + "/" + relativePath;
    }

    public CompletableFuture<Void> restoreBackup(int backupId, String backupDir, String relativePath) {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        executor.execute(() -> {
            try (BackupEngineOptions opt = new BackupEngineOptions(backupDir);
                    BackupEngine backupEngine = BackupEngine.open(Env.getDefault(), opt);
                    RestoreOptions restoreOpt = new RestoreOptions(false)) {

                String absolutePath = getAbsolutePath(relativePath);
                if (backupId == -1) {
                    backupEngine.restoreDbFromLatestBackup(absolutePath, absolutePath, restoreOpt);
                } else {
                    backupEngine.restoreDbFromBackup(backupId, absolutePath, absolutePath, restoreOpt);
                }

                cf.complete(null);
            } catch (Exception e) {
                cf.completeExceptionally(e);
            } finally {
            }
        });

        return cf;
    }

    public List<RocksDB> getOpenRdbs() {
        return databases.values().stream().map(yrdb -> yrdb.getDb()).collect(Collectors.toList());
    }

    /**
     * Called from Unit tests to cleanup before the next test
     */
    public static void shutdownAll() {
        for (RDBFactory r : instances.values()) {
            r.shutdown();
        }
        instances.clear();
    }

}
