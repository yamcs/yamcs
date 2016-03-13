package org.yamcs.parameter;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

/**
 * set of subscription ids - represented as sorted array
 * 
 * no duplicate allowed
 * 
 * copy on write
 * @author nm
 *
 */
public class SubscriptionArray {
    private volatile int[] array = new int[0];
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * add subscription id to the array
     * If it's already in the array, the operation has no effect
     * @param subscriptionId
     */
    public void add(int subscriptionId) {
	lock.lock();
	try {
	    int[] a = array;
	    int pos = Arrays.binarySearch(a, subscriptionId);
	    if(pos>=0) return;
	    pos = -pos-1;
	    int[] b = new int[a.length+1];
	    System.arraycopy(a, 0, b, 0, pos);
	    b[pos] = subscriptionId;
	    System.arraycopy(a, pos, b, pos+1, a.length-pos);
	    array = b;
	} finally {
	    lock.unlock();
	}
    }
    /**
     * Remove the subscriptionId from the array 
     *  return true if it has been removed or false if it was not there
     * 
     * @param subscriptionId
     * @return
     */
    public boolean remove(int subscriptionId) {
	lock.lock();
	try {
	    int[] a = array;
	    int pos = Arrays.binarySearch(a, subscriptionId);
	    if(pos<0) return false;

	    int[] b = new int[a.length-1];
	    System.arraycopy(a, 0, b, 0, pos);
	    System.arraycopy(a, pos+1, b, pos, a.length-pos-1);
	    array = b;
	    return true;
	} finally {
	    lock.unlock();
	}
    }

    public boolean isEmpty() {	
	return array.length==0;
    }

    public int[] getArray() {
	return array;
    }
    public int size() {
	return array.length;
    }
    
    public String toString() {
	return Arrays.toString(array);
    }
}
