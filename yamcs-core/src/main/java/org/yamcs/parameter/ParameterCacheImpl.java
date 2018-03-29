package org.yamcs.parameter;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;

/**
 * 
 * 
 * Used by the {@link org.yamcs.parameter.ParameterRequestManager} to cache last values of parameters.
 * 
 * The cache will contain the parameters for a predefined time period but can exceed that period if space is available
 * in the CacheEntry.
 * 
 * A CacheEntry also has a maximum size to prevent it accumulating parameters ad infinitum (e.g. if there are bogus
 * parameters with the timestamp never changing)
 * 
 * 
 * We keep delivery consisting of lists of parameter values together such that
 * if two parameters have been acquired in the same delivery, they will be given from the same delivery to the clients.
 * 
 * if cacheAll is enabled, all parameters will be cached as they are received.
 * if cacheAll is disabled:
 * - a parameter will be put on the list to cache only if somebody has requested it.
 * - obviously first time when the parameter is requested, no value will be returned.
 * - however this will greatly reduce the cache size since only a few parameters are monitored in the displays
 * 
 * @author nm
 *
 */
public class ParameterCacheImpl implements ParameterCache {
    final ConcurrentHashMap<Parameter, CacheEntry> cache = new ConcurrentHashMap<>();
    // which parameters to cache
    final ConcurrentHashMap<Parameter, Boolean> parametersToCache;
    final long timeToCache;
    final int maxNumEntries;
    boolean cacheAll;
    final ParameterCacheConfig cacheConfig;

    public ParameterCacheImpl(ParameterCacheConfig cacheConfig) {
        this.timeToCache = cacheConfig.maxDuration;
        this.maxNumEntries = cacheConfig.maxNumEntries;
        this.cacheConfig = cacheConfig;
        this.cacheAll = cacheConfig.cacheAll;
        parametersToCache = cacheAll ? null : new ConcurrentHashMap<>();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.yamcs.parameter.ParameterCache#update(java.util.Collection)
     */
    @Override
    public void update(Collection<ParameterValue> pvs) {
        ParameterValueList pvlist = new ParameterValueList(pvs);
        for (ParameterValue pv : pvs) {
            Parameter p = pv.getParameter();
            CacheEntry ce = cache.get(p);
            if (ce == null) {
                if (cacheAll || parametersToCache.containsKey(p)) {
                    ce = new CacheEntry(p, timeToCache, maxNumEntries);
                    cache.put(p, ce);
                    ce.add(pvlist);
                }
            } else {
                ce.add(pvlist);
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.yamcs.parameter.ParameterCache#getValues(java.util.List)
     */
    @Override
    public List<ParameterValue> getValues(List<Parameter> plist) {
        long now = TimeEncoding.getWallclockTime();

        // use a bitset to clear out the parameters that have already been found
        BitSet bs = new BitSet(plist.size());
        List<ParameterValue> result = new ArrayList<>(plist.size());
        bs.set(0, plist.size(), true);

        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
            Parameter p = plist.get(i);
            CacheEntry ce = cache.get(p);
            if (ce != null) { // last delivery where this parameter appears
                ParameterValueList pvlist = ce.getLast();
                ParameterValue pv = pvlist.getLastInserted(p);
                // take the opportunity to check for expiration
                if ((pv.getAcquisitionStatus() == AcquisitionStatus.ACQUIRED) && pv.isExpired(now)) {
                    pv.setAcquisitionStatus(AcquisitionStatus.EXPIRED);
                }
                result.add(pv);
                bs.clear(i);
                // find all the other parameters that are in this delivery
                for (int j = bs.nextSetBit(i); j >= 0; j = bs.nextSetBit(j + 1)) {
                    Parameter p1 = plist.get(j);
                    pv = pvlist.getLastInserted(p1);
                    if (pv != null) {
                        if ((pv.getAcquisitionStatus() == AcquisitionStatus.ACQUIRED) && pv.isExpired(now)) {
                            pv.setAcquisitionStatus(AcquisitionStatus.EXPIRED);
                        }
                        result.add(pv);
                        bs.clear(j);
                    }
                }
            } else { // no value for this parameter
                bs.clear(i);
                if (!cacheAll) {
                    parametersToCache.put(p, Boolean.TRUE);
                }
            }
        }

        return result;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.yamcs.parameter.ParameterCache#getLastValue(org.yamcs.xtce.Parameter)
     */
    @Override
    public ParameterValue getLastValue(Parameter p) {
        CacheEntry ce = cache.get(p);
        if (ce == null) {
            if (!cacheAll) {
                parametersToCache.put(p, Boolean.TRUE);
            }
            return null;
        }
        ParameterValueList pvlist = ce.getLast();
        return pvlist.getLastInserted(p);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.yamcs.parameter.ParameterCache#getAllValues(org.yamcs.xtce.Parameter)
     */
    @Override
    public List<ParameterValue> getAllValues(Parameter p) {
       return getAllValues(p, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    @Override
    public List<ParameterValue> getAllValues(Parameter p, long start, long stop) {
        CacheEntry ce = cache.get(p);
        if (ce == null) {
            if (!cacheAll) {
                parametersToCache.put(p, Boolean.TRUE);
            }
            return null;
        }

        return ce.getAll(start, stop);
    }

    /**
     * Stores a cache for one parameter as an array of the ParameterValueList in which it is part of.
     * 
     * It ensure minimum
     * 
     * @author nm
     *
     */
    static final class CacheEntry {
        final Parameter parameter;
        private ParameterValueList[] elements;
        int tail = 0;
        static final int INITIAL_CAPACITY = 128;
        final long timeToCache;
        final int maxNumEntries;
        ReadWriteLock lock = new ReentrantReadWriteLock();

        public CacheEntry(Parameter p, long timeToCache, int maxNumEntries) {
            this.parameter = p;
            this.timeToCache = timeToCache;
            this.maxNumEntries = maxNumEntries;
            int initialCapacity = Math.min(INITIAL_CAPACITY, maxNumEntries);
            if (initialCapacity > 1) { // make sure it's power of 2
                initialCapacity = Integer.highestOneBit(initialCapacity - 1) << 1;
            }
            elements = new ParameterValueList[initialCapacity];
        }

        public List<ParameterValue> getAll(long start, long stop) {
            lock.readLock().lock();
            try {
                List<ParameterValue> plist = new ArrayList<>();
                int _tail = tail;
                int n = elements.length;
                int t = _tail;
                do {
                    t = (t - 1) & (n - 1);
                    ParameterValueList pvl = elements[t];
                    if (pvl == null) {
                        break;
                    }
                    pvl.forEach(parameter, (ParameterValue pv) -> {
                        long time = pv.getGenerationTime();
                        if (time > start && time <= stop) {
                            plist.add(pv);
                        }

                    });

                } while (t != _tail);
                if(plist.isEmpty()) {
                    return null;
                }
                return plist;
            } finally {
                lock.readLock().unlock();
            }
        }

        ParameterValueList getLast() {
            lock.readLock().lock();
            try {
                return elements[(tail - 1) & (elements.length - 1)];
            } finally {
                lock.readLock().unlock();
            }
        }

        public void add(ParameterValueList pvlist) {
            lock.writeLock().lock();
            try {
                ParameterValueList pv1 = elements[tail];
                if (pv1 != null) {
                    ParameterValue oldpv = pv1.getFirstInserted(parameter);
                    ParameterValue newpv = pvlist.getFirstInserted(parameter);
                    if ((oldpv == null) || (newpv == null)) {
                        return; // shouldn't happen
                    }
                    if (newpv.getGenerationTime() < oldpv.getGenerationTime()) {
                        // parameter older than the last one in the queue -> ignore
                        return;
                    }

                    if (newpv.getGenerationTime() - oldpv.getGenerationTime() < timeToCache) {
                        doubleCapacity();
                    }
                }
                elements[tail] = pvlist;
                tail = (tail + 1) & (elements.length - 1);
            } finally {
                lock.writeLock().unlock();
            }
        }

        private void doubleCapacity() {
            int capacity = elements.length;
            if (capacity >= maxNumEntries) {
                return;
            }

            int newCapacity = 2 * capacity;

            ParameterValueList[] newElements = new ParameterValueList[newCapacity];
            System.arraycopy(elements, 0, newElements, 0, tail);
            System.arraycopy(elements, tail, newElements, tail + capacity, capacity - tail);
            elements = newElements;
        }
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
