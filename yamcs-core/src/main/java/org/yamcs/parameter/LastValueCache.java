package org.yamcs.parameter;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import org.yamcs.xtce.Parameter;

/**
 * Cache for the last known value of each parameter.
 * 
 * 
 * thread safe
 * 
 * @author nm
 *
 */
//NM's notes for whomever will implement a smarter version of this
// - one thing we know about this is that remove is never called, but we may want to allow a clearAll when doing time jumps during replays
// - we will need to allow in limited cases multiple historical values to be kept. This will correspond to the ParameterInstanceRef with instance<0
// - 
public class LastValueCache {
    ConcurrentHashMap<Parameter, ParameterValue> m = new ConcurrentHashMap<>();
    
    /**
     * Returns the latest known value for p or null if there is none.
     * 
     * @param p
     * @return
     */
    public ParameterValue getValue(Parameter p) {
        return m.get(p);
    }
    
    /**
     * Puts a new value in the map.
     * @param p
     * @param pv
     * @return the previous value or null if there was none.
     */
    public ParameterValue put(Parameter p, ParameterValue pv) {
        return m.put(p, pv);
    }

    public void update(Collection<ParameterValue> params) {
        for(ParameterValue pv: params) {
            Parameter p = pv.getParameter();
            if(p!=null) {
                m.put(p, pv);
            }
        }
    }
    
    public int size() {
        return m.size();
    }

    /**
     * returns all the values from the cache
     * @return
     */
    public Collection<ParameterValue> getValues() {
        return m.values();
    }
}
