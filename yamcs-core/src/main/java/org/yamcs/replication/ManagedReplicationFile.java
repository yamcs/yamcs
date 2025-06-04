package org.yamcs.replication;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.yamcs.logging.Log;

/**
 * Reference counting implementation and read/write locking around a replication file.
 * <p>
 * A file may be opened or closed. When it is open, the usage is reference counted via the acquire and release
 * functions.
 */
class ManagedReplicationFile {
    final Log log;
    final Path path;
    final long firstTxId;
    final int maxFileSize;
    final String yamcsInstance;

    private int refCount = 0;

    long lastAccess;
    volatile boolean syncRequired;

    boolean deleted = false;

    private ReplicationFile rf;
    ReadWriteLock rwlock = new ReentrantReadWriteLock();

    public ManagedReplicationFile(String yamcsInstance, long firstTxId, Path path, int maxFileSize) {
        this.yamcsInstance = yamcsInstance;
        this.lastAccess = -1;
        this.rf = null;
        this.path = path;
        this.firstTxId = firstTxId;
        this.maxFileSize = maxFileSize;
        log = new Log(this.getClass(), yamcsInstance);
    }

    /**
     * If the file has been deleted, return false;
     * 
     * Otherwise:
     * <p>
     * If the file is not opened, then open it read-only and set the refCount to 1
     * <p>
     * Else just increase the refCount
     * 
     */
    public boolean acquire() {
        log.trace("acquire {} current refCount: {} ", path, refCount);

        rwlock.writeLock().lock();
        try {
            if (deleted) {
                return false;
            }
            if (rf == null) {
                assert (refCount == 0);

                rf = ReplicationFile.openReadOnly(yamcsInstance, path, firstTxId);
            }
            refCount++;
            lastAccess = System.currentTimeMillis();
            return true;
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public void release() {
        log.trace("release {} current refCount: {}", path, refCount);

        rwlock.writeLock().lock();
        try {
            assert (refCount > 0);
            refCount--;
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public boolean isOpen() {
        rwlock.readLock().lock();
        try {
            return rf != null;
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public void createNew(int pageSize, int maxPages) {
        rwlock.writeLock().lock();
        try {
            if (rf != null) {
                throw new IllegalStateException("Replication file already open");
            }
            assert (refCount == 0);
            rf = ReplicationFile.newFile(yamcsInstance, path, firstTxId, pageSize,
                    maxPages, maxFileSize);
        } finally {
            rwlock.writeLock().unlock();
        }

    }

    public void openReadWrite() {
        log.trace("openReadWrite {} current refCount: {}", path, refCount);
        rwlock.writeLock().lock();
        try {
            if (rf != null) {
                throw new IllegalStateException("Replication file already open");
            }
            assert (refCount == 0);
            rf = ReplicationFile.openReadWrite(yamcsInstance, path,
                    firstTxId, maxFileSize);
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public void openReadOnly() {
        log.trace("openReadOnly {} current refCount: {}", path, refCount);
        rwlock.writeLock().lock();
        try {
            if (rf != null) {
                throw new IllegalStateException("Replication file already open");
            }
            assert (refCount == 0);

            rf = ReplicationFile.openReadOnly(yamcsInstance, path, firstTxId);
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public boolean isFull() throws IOException {
        rwlock.readLock().lock();
        try {
            if (rf == null) {
                return Files.size(path) >= maxFileSize;
            } else {
                return rf.isFull();
            }
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public long getNextTxId() {
        rwlock.readLock().lock();
        try {
            return rf.getNextTxId();
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public long getFirstTxId() {
        return firstTxId;
    }

    public long writeData(Transaction tx) {
        rwlock.writeLock().lock();
        try {
            return rf.writeData(tx);
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public void setSyncRequired(boolean syncRequired) {
        this.syncRequired = syncRequired;
    }

    public int numTx() {
        rwlock.readLock().lock();
        try {
            return rf.numTx();
        } finally {
            rwlock.readLock().unlock();
        }
    }

    /**
     * If the file is not opened return without doing anything
     * <p>
     * If the lastAccess is older than t, then close the file and return
     * <p>
     * If the sync bit is set, synchronize the file and unset the sync bit
     */
    public synchronized void syncOrClose(long t) throws IOException {
        rwlock.readLock().lock();
        try {
            if (rf == null) {
                return;
            }

            if (refCount == 0 && lastAccess < t) {
                rf.close();
                rf = null;
                return;
            }
            if (syncRequired) {
                rf.sync();
                syncRequired = false;
            }
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public void sync() throws IOException {
        rwlock.readLock().lock();
        try {
            rf.sync();
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public ReplicationTail tail(long txId) {
        rwlock.readLock().lock();
        try {
            return rf.tail(txId);
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public void getNewData(ReplicationTail rfe) {
        rwlock.readLock().lock();
        try {
            rf.getNewData(rfe);
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public void close() {
        rwlock.writeLock().lock();

        try {
            if (rf == null) {
                return;
            }
            assert (refCount == 0);
            rf.close();
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    /**
     * If the file is not open, delete it and return true
     * <p>
     * If the file is open and refCount is 0, close it, delete it and return true
     * <p>
     * Otherwise return false;
     * <p>
     * If the file has been deleted, the deleted flag is set to true so future calls of acquire will return false
     * 
     * @throws IOException
     */
    public boolean delete() throws IOException {
        rwlock.writeLock().lock();

        try {
            if (rf == null) {
                Files.delete(path);
                deleted = true;
                return true;
            }
            if (refCount == 0) {
                rf.close();
                Files.delete(path);
                deleted = true;
                return true;
            }
            return false;
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public Iterator<ByteBuffer> metadataIterator() {
        rwlock.readLock().lock();
        try {
            return rf.metadataIterator();
        } finally {
            rwlock.readLock().unlock();
        }
    }

}
