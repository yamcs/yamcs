package org.yamcs.xtce;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

public class EnumeratedDataType extends BaseDataType {
    private static final long serialVersionUID = 201002231432L;
    EnumeratedDataType(String name) {
        super(name);
    }

    protected HashMap<Long,ValueEnumeration> enumeration = new HashMap<Long,ValueEnumeration>();
    protected List<ValueEnumeration> enumerationList = new ArrayList<ValueEnumeration>();//this keeps track of the duplicates but is not really used 
    protected Vector<ValueEnumerationRange>  ranges = null;

    protected String initialValue = null;

    /**
     * Set initial value
     * @param initialValue
     */
    public void setInitialValue(String initialValue) {
        this.initialValue = initialValue;
    }

    public String getInitialValue() {
        return initialValue;
    }

    public ValueEnumeration enumValue(Long key) {
        if ( enumeration.containsKey(key) ) {
            return enumeration.get(key);
        } else if ( ranges != null ) {
            for (ValueEnumerationRange range:ranges) {
                if (range.isValueInRange(key)) {
                    return new ValueEnumeration(key, range.getLabel());
                }
            }
        }
        return null;
    }

    public ValueEnumeration enumValue(String label) {
        for(ValueEnumeration enumeration:enumerationList) {
            if(enumeration.getLabel().equals(label)) {
                return enumeration;
            }
        }
        return null;
    }

    /**
     * Add value to enumeration list
     * @param value Integer value
     * @param label Label associated with value
     */
    public void addEnumerationValue(long value, String label) {
        ValueEnumeration valEnum = new ValueEnumeration(value, label);
        enumerationList.add(valEnum);
        enumeration.put(value, valEnum);
    }

    /**
     * Add range to enumeration list
     */
    public void addEnumerationRange(double min, double max, boolean isMinInclusive, boolean isMaxInclusive, String label) {
        assert(min < max);
        ValueEnumerationRange range = new ValueEnumerationRange(min, max, isMinInclusive, isMaxInclusive, label);
        ranges.add(range);
    }

    public void addEnumerationRange(ValueEnumerationRange range) {
        if ( ranges == null ) {
            ranges = new Vector<ValueEnumerationRange>(2);
        }
        ranges.add(range);
    }

    public List<ValueEnumeration> getValueEnumerationList() {
        return Collections.unmodifiableList(enumerationList);
    }

    /**
     * returns stringValue
     */
    @Override
    public Object parseString(String stringValue) {
        return stringValue;
    }
}

