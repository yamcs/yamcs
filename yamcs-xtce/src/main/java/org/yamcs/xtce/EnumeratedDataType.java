package org.yamcs.xtce;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;

public class EnumeratedDataType extends BaseDataType {
    private static final long serialVersionUID = 2L;

    protected HashMap<Long,ValueEnumeration> enumeration = new HashMap<>();
    protected List<ValueEnumeration> enumerationList = new ArrayList<>();
    protected List<ValueEnumerationRange> ranges = new ArrayList<>();

    protected String initialValue = null;

    
    EnumeratedDataType(String name) {
        super(name);
    }
    /**
     * performs a shallow copy of this object into t
     * @param t
     */
    protected EnumeratedDataType(EnumeratedDataType t) {
        super(t);
        this.enumeration = t.enumeration;
        this.enumerationList = t.enumerationList;
        this.ranges = t.ranges;
    }
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
    
    public boolean hasLabel(String label) {
        for(ValueEnumeration enumeration:enumerationList) {
            if(enumeration.getLabel().equals(label)) {
                return true;
            }
        }
        if ( ranges != null ) {
            for (ValueEnumerationRange range:ranges) {
                if (range.getLabel().equals(label)) {
                    return true;
                }
            }
        }
        return false;
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
    public void addEnumerationValue(ValueEnumeration ve) {
        enumerationList.add(ve);
        enumeration.put(ve.value, ve);
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
            ranges = new ArrayList<>(2);
        }
        ranges.add(range);
    }

    public List<ValueEnumeration> getValueEnumerationList() {
        return Collections.unmodifiableList(enumerationList);
    }
    public List<ValueEnumerationRange> getValueEnumerationRangeList() {
        return Collections.unmodifiableList(ranges);
    }
    

    /**
     * returns stringValue
     */
    @Override
    public Object parseString(String stringValue) {
        return stringValue;
    }
    @Override
    public Type getValueType() {
        return Value.Type.STRING;
    }
    
    @Override
    public String getTypeAsString() {
        return "enumeration";
    }
}

