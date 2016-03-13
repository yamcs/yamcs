package org.yamcs.parameter;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.yamcs.parameter.ParameterValue;
import org.yamcs.xtce.Parameter;

/**
 * 
 * 
 * Used by the {@link org.yamcs.parameter.ParameterRequestManagerImpl} to cache last values of parameters.
 * 
 * The cache will contain the parameters for a predefined time period but can exceed that period if space is available in the CacheEntry.
 * 
 * A CacheEntry also has a maximum size to prevent it accumulating parameters ad infinitum (e.g. if there are bogus parameters with the timestamp never changing)
 * 
 * 
 * We keep delivery consisting of lists of parameter values together such that
 *  if two parameters have been acquired in the same delivery, they will be given from the same delivery to the clients.
 * 
 * @author nm
 *
 */
public class ParameterCache {
    final ConcurrentHashMap<Parameter, CacheEntry> cache = new ConcurrentHashMap<Parameter, CacheEntry>();
    final long timeToCache;
    final int maxNumEntries;
    
    public ParameterCache(ParameterCacheConfig cacheConfig) {
        this.timeToCache = cacheConfig.maxDuration;
        this.maxNumEntries = cacheConfig.maxNumEntries;
    }
    /**
     * update the parameters in the cache

     * @param pvs - parameter value list
     */
    public void update(Collection<ParameterValue> pvs) {
        //System.out.println("ParameterCache ------- updated with "+pvs);
        ParameterValueList  pvlist = new ParameterValueList(pvs);
        for (ParameterValue pv:pvs) {
            Parameter p = pv.getParameter();
            CacheEntry ce = cache.get(p);
            if(ce==null) {
                ce = new CacheEntry(p, timeToCache, maxNumEntries);
                cache.put(p, ce);
            }
            ce.add(pvlist);
        }
    }


    /**
     * Returns cached value for parameter or an empty list if there is no value in the cache
     * 
     * 
     * @param plist
     * @return
     */
    public List<ParameterValue> getValues(List<Parameter> plist) {
        //use a bitset to clear out the parameters that have already been found
        BitSet bs = new BitSet(plist.size());
        List<ParameterValue> result = new ArrayList<ParameterValue>(plist.size());
        bs.set(0, plist.size(), true);

        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i+1)) {
            Parameter p = plist.get(i);
            CacheEntry ce = cache.get(p);
            if(ce!=null) { //last delivery where this parameter appears
                ParameterValueList pvlist = ce.getLast();
                ParameterValue pv = pvlist.getLastInserted(p);
                result.add(pv);
                bs.clear(i);
                //find all the other parameters that are in this delivery
                for (int j = bs.nextSetBit(i); j >= 0; j = bs.nextSetBit(j+1)) {
                    Parameter p1 = plist.get(j);
                    pv = pvlist.getLastInserted(p1);
                    if(pv!=null) {
                        result.add(pv);
                        bs.clear(j);
                    }
                }
            } else { //no value for this parameter
                bs.clear(i);
            }
        }

        return result;
    }


    /**
     * Returns last cached value for parameter or null if there is no value in the cache
     * @param p - parameter for which the last value is returned
     * @return
     */
    public ParameterValue getLastValue(Parameter p) {
        CacheEntry ce = cache.get(p);
        if(ce==null) return null;
        ParameterValueList pvlist = ce.getLast();
        return pvlist.getLastInserted(p);
    }

    /**
     * Returns all values from the cache for the parameter or null if there is no value cached
     * 
     * The parameter are returned in descending order (newest parameter is returned first)
     * @param p - parameter for which all values are returned
     * @return
     */
    public List<ParameterValue> getAllValues(Parameter p) {
        CacheEntry ce = cache.get(p);
        if(ce==null) return null;

        return ce.getAll();
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
        private  ParameterValueList[] elements;
        int tail = 0;
        static final int INITIAL_CAPACITY = 128;
        final long timeToCache;
        final int maxNumEntries;
        ReadWriteLock lock = new ReentrantReadWriteLock();
        
        public CacheEntry(Parameter p, long timeToCache, int maxNumEntries) {
            this.parameter = p;
            this.timeToCache = timeToCache;
            this.maxNumEntries = maxNumEntries;
            elements = new ParameterValueList[INITIAL_CAPACITY];
        }



        public  List<ParameterValue> getAll() {
            lock.readLock().lock();
            try {
                List<ParameterValue> plist = new ArrayList<>();
                int _tail = tail;
                int n = elements.length;
                int t = _tail; 
                do {
                    t = (t-1)&(n-1);
                    ParameterValueList pvl = elements[t];
                    if(pvl==null) break;
                    pvl.forEach(parameter, (ParameterValue pv) -> plist.add(pv));
                    
                } while (t!=_tail);
                    
                return plist;
            } finally {
                lock.readLock().unlock();
            }
        }



        ParameterValueList getLast() {
            lock.readLock().lock();
            try {
                return elements[(tail-1)&(elements.length-1)];
            } finally {
                lock.readLock().unlock();
            } 
        }

        public void add(ParameterValueList pvlist) {
            lock.writeLock().lock();
            try {
                ParameterValueList pv1 = elements[tail];
                if(pv1!=null) {
                    ParameterValue oldpv = pv1.getFirstInserted(parameter);
                    ParameterValue newpv = pvlist.getFirstInserted(parameter);
                    if((oldpv==null) || (newpv==null)) return; // shouldn't happen
                    if(newpv.getGenerationTime() < oldpv.getGenerationTime()) {
                        // parameter older than the last one in the queue -> ignore
                        return;
                    }
                    
                    if(newpv.getGenerationTime()-oldpv.getGenerationTime()<timeToCache) {
                        doubleCapacity();
                    }
                }
                elements[tail] = pvlist;
                tail = (tail+1) & (elements.length-1);
            } finally {
                lock.writeLock().unlock();
            }
        }

        private void doubleCapacity() {
            int capacity = elements.length;
            if(capacity>=maxNumEntries) return;

            int newCapacity = 2*capacity;

            ParameterValueList[]  newElements = new ParameterValueList[newCapacity];
            System.arraycopy(elements, 0, newElements, 0, tail);
            System.arraycopy(elements, tail, newElements, tail+capacity, capacity-tail);
            elements = newElements;
        }
    }
}
