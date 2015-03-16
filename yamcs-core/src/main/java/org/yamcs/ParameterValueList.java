package org.yamcs;

import java.util.Collection;

import org.yamcs.xtce.Parameter;

/**
 * 
 * Stores a collection of ParameterValue indexed on Parameter
 * 
 * Works like a Hashtable but stores duplicates. 
 * 
 * 
 * @author nm
 *
 */
public class ParameterValueList {
	Entry[] table;
	int size=0;
	
	/**
	 * Newest values are assumed to be at the end of the collection
	 * (because that's how XtceTmExtractor adds them)
	 * @param pvs
	 */
	public ParameterValueList(Collection<ParameterValue> pvs) {
		int len = roundUpToPowerOfTwo(pvs.size());
		table = new Entry[len];
		for(ParameterValue pv:pvs) {
			add(pv);
		}
	}

	/**
	 * add a parameter to the hashtable, to the beginning of the other value for the same parameter
	 * 
	 * @param pv
	 */
	int count =0;
	private void add(ParameterValue pv) {
		Entry newEntry = new Entry(pv);
		
		int hash = getHash(pv.getParameter());
		int index = hash & (table.length - 1);
		Entry e = table[index];
		table[index] = newEntry;
		newEntry.next = e;
		if(e!=null) {
			count++;			
		}
		size++;
	}
	
	private int getHash(Parameter p) {
		return p.hashCode();
	}
	
	public int getSize() {
		return size;
	}
	/**
	 * Returns the first value for Parameter p or null if there is no value
	 * @param p
	 * @return
	 */
	public ParameterValue getNewest(Parameter p) {
		int index =  getHash(p) & (table.length - 1);
		for(Entry e = table[index] ; e!=null; e=e.next) {
			if(e.pv.getParameter()==p) {
				return e.pv;
			}
		}
		return null;
	}
	
	
	/**
	 * this is copied from http://graphics.stanford.edu/~seander/bithacks.html#RoundUpPowerOf2
	 * 
	 * 
	 */
	static int roundUpToPowerOfTwo(int v){
		v--;
		v |= v >> 1;
		v |= v >> 2;
		v |= v >> 4;
		v |= v >> 8;
		v |= v >> 16;
		v++;
		return v;
	}
	
	
	
	static class Entry {
		final ParameterValue pv;
		Entry next;
		Entry(ParameterValue pv) {
			this.pv = pv;
		}
	}
}
