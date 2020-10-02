package org.yamcs.xtce.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a list of aggregate member names.
 * 
 * <p>
 * This class maintains a cache of aggregate members names in order to be reused for all the aggregate values with the
 * same type.
 * 
 * 
 * 
 * @author nm
 *
 */
public class AggregateMemberNames {
    private final static Map<AggregateMemberNames, AggregateMemberNames> uniqueValues = new HashMap<>();

    private final String[] names;

    private AggregateMemberNames(String[] names) {
        this.names = names;
    }

    /**
     * 
     * @param name
     * @return the index of the name in the list or -1 if it is not part of the list
     * @throws
     */
    public int indexOf(String name) {
        String tmp = name.intern();
        for (int i = 0; i < names.length; i++) {
            if (names[i] == tmp) {
                return i;
            }
        }
        return -1;
    }

    public String get(int idx) {
        return names[idx];
    }

    /**
     * 
     * @return the number of member names in this list
     */
    public int size() {
        return names.length;
    }

    /**
     * Factory method that returns the unique object corresponding to the list of names.
     * 
     * @param names
     *            - ordered list of names for which an object will be created if not already existing and returned
     * @return - the unique object corresponding to the list of names
     */
    public static AggregateMemberNames get(String[] names) {
        AggregateMemberNames amn = new AggregateMemberNames(names);
        AggregateMemberNames amn1 = uniqueValues.get(amn);
        if (amn1 != null) {
            return amn1;
        }
        String[] nnames = new String[names.length];
        for (int i = 0; i < names.length; i++) {
            nnames[i] = names[i].intern();
        }
        amn = new AggregateMemberNames(nnames);
        uniqueValues.put(amn, amn);
        return amn;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(names);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AggregateMemberNames other = (AggregateMemberNames) obj;
        if (!Arrays.equals(names, other.names))
            return false;
        return true;
    }
    
    @Override
    public String toString() {
        return Arrays.deepToString(names);
    }
    
}
