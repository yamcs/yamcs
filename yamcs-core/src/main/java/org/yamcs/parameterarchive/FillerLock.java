package org.yamcs.parameterarchive;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Handle locking parameter archive intervals in order to avoid different fillers filling up the same interval
 * concurrently.
 * <p>
 * The locking is done per parameter group id
 */
public class FillerLock {
    private final ConcurrentMap<LockKey, Object> locks = new ConcurrentHashMap<>();

    /**
     * try to acquire the filler lock for the given interval and pgid with the given holder.
     * <p>
     * return true if the lock could be acquired and false otherwise.
     * <p>
     * If the holder is the same one that acquired the lock in the first place, then return true;
     */
    public boolean try_lock(long interval, int pgid, Object holder) {
        var existing = locks.putIfAbsent(new LockKey(interval, pgid), holder);
        return existing == null || existing == holder;
    }

    /**
     * unlock the filler lock for the given interval and pgid
     */
    public void unlock(long interval, int pgid) {
        locks.remove(new LockKey(interval, pgid));
    }

    public int lockCount() {
        return locks.size();
    }

    static record LockKey(long interval, int pgid) {
    }

    public String toString() {
        return locks.toString();
    }
}
