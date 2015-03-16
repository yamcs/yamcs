package org.yamcs;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.yamcs.xtce.Parameter;

/**
 * 
 * 
 * Used by the {@link org.yamcs.ParameterRequestManager} to cache last value of parameters
 * 
 * 
 * We keep delivery consisting of lists of parameter values together such that
 *  if two parameters have been acquired in the same delivery, they will be given from the same delivery to the clients.
 * 
 * @author nm
 *
 */
public class ParameterCache {
	ConcurrentHashMap<Parameter, CacheEntry> cache = new ConcurrentHashMap<Parameter, CacheEntry>();
	
	/**
	 * update the parameters in the cache
	
	 * @param parameterList
	 */
	public void update(Collection<ParameterValue> pvs) {
		ParameterValueList  pvlist = new ParameterValueList(pvs);
		CacheEntry ce = new CacheEntry(System.currentTimeMillis(), pvlist);
		for (ParameterValue pv:pvs) {
			cache.put(pv.getParameter(), ce);
		}
	}
	
	
	/**
	 * Returns cached value for parameter or an empty list if there is no value in the cache
	 * 
	 * 
	 * @param plist
	 * @return
	 */
	List<ParameterValue> getValues(List<Parameter> plist) {
		Set<CacheEntry> ceset = new HashSet<CacheEntry>();
		for(Parameter p:plist) {
			CacheEntry ce = cache.get(p);
			if(ce!=null) ceset.add(ce); 
		}

		//sort CacheEntries such that most recent is in front
		CacheEntry[] cearray =  ceset.toArray(new CacheEntry[ceset.size()]);
		
		Arrays.sort(cearray, new Comparator<CacheEntry>() {
			@Override
			public int compare(CacheEntry o1, CacheEntry o2) {
				return Long.compare(o2.timestamp, o1.timestamp);
			}
		});
		List<ParameterValue> result = new ArrayList<ParameterValue>(plist.size());
		//use bitset to clear out the parameters that have already been found
		BitSet bs = new BitSet(plist.size());
		
		for(CacheEntry ce:cearray) {
			boolean empty = true;
			for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i+1)) {
				empty = false;
				Parameter p = plist.get(i);
				ParameterValue pv = ce.pvlist.getNewest(p);
				if(pv!=null) {
					result.add(pv);
					bs.set(i, false);
				}
			}
			if(empty)break;
		}
		return result;
	}
	
	
	/**
	 * Returns cached value for parameter or null if there is no value in the cache
	 * @param plist
	 * @return
	 */
	ParameterValue getValue(Parameter p) {
		CacheEntry ce = cache.get(p);
		if(ce==null) return null;

		return ce.pvlist.getNewest(p);
	}
	
	
	
	
	static final class CacheEntry {
		final long timestamp;
		final ParameterValueList pvlist;
		
		public CacheEntry(long timestamp, ParameterValueList pvlist) {
			this.timestamp = timestamp;
			this.pvlist = pvlist;
		}
	}
}
