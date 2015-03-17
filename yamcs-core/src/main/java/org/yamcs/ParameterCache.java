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

		//use a bitset to clear out the parameters that have already been found
		BitSet bs = new BitSet(plist.size());
		List<ParameterValue> result = new ArrayList<ParameterValue>(plist.size());

		bs.set(0, plist.size()-1, true);
		
		for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i+1)) {
			Parameter p = plist.get(i);
			CacheEntry ce = cache.get(p);
			if(ce!=null) { //last delivery where this parameter appears
				ParameterValue pv = ce.pvlist.getNewest(p);
				result.add(pv);
				bs.clear(i);
			
				//find all the other parameters that are in this delivery
				for (int j = bs.nextSetBit(i+1); j >= 0; j = bs.nextSetBit(j+1)) {
					p = plist.get(j);
					pv = ce.pvlist.getNewest(p);
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
