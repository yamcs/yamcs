package org.yamcs.xtce;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;

public class EnumeratedDataType extends BaseDataType {
    private static final long serialVersionUID = 2L;

    protected HashMap<Long, ValueEnumeration> enumeration = new HashMap<>();

    protected List<ValueEnumeration> enumerationList = new ArrayList<>();
    protected List<ValueEnumerationRange> ranges = new ArrayList<>();

    EnumeratedDataType(Builder<?> builder) {
        super(builder);
        this.enumerationList = builder.enumerationList;
        this.ranges = builder.ranges;

        for (ValueEnumeration ve : enumerationList) {
            enumeration.put(ve.value, ve);
        }
        if (builder.initialValue != null) {
            this.initialValue = parseString(builder.initialValue);
        }
    }

    /**
     * performs a shallow copy of this object into t
     * 
     * @param t
     */
    protected EnumeratedDataType(EnumeratedDataType t) {
        super(t);
        this.enumeration = t.enumeration;
        this.enumerationList = t.enumerationList;
        this.ranges = t.ranges;
    }

    public String getInitialValue() {
        return (String) initialValue;
    }

    public ValueEnumeration enumValue(Long key) {
        if (enumeration.containsKey(key)) {
            return enumeration.get(key);
        } else if (ranges != null) {
            for (ValueEnumerationRange range : ranges) {
                if (range.isValueInRange(key)) {
                    return new ValueEnumeration(key, range.getLabel());
                }
            }
        }
        return null;
    }

    public ValueEnumeration enumValue(String label) {
        for (ValueEnumeration enumeration : enumerationList) {
            if (enumeration.getLabel().equals(label)) {
                return enumeration;
            }
        }
        return null;
    }

    public boolean hasLabel(String label) {
        for (ValueEnumeration enumeration : enumerationList) {
            if (enumeration.getLabel().equals(label)) {
                return true;
            }
        }
        if (ranges != null) {
            for (ValueEnumerationRange range : ranges) {
                if (range.getLabel().equals(label)) {
                    return true;
                }
            }
        }
        return false;
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

    public abstract static class Builder<T extends Builder<T>> extends BaseDataType.Builder<T> {
        protected List<ValueEnumeration> enumerationList = new ArrayList<>();
        protected List<ValueEnumerationRange> ranges = new ArrayList<>();

        public Builder() {
        }
 
        public Builder(EnumeratedDataType dataType) {
            super(dataType);
            this.enumerationList = dataType.enumerationList;
            this.ranges = dataType.ranges;
        }
        
        public void addEnumerationValue(long value, String label) {
            ValueEnumeration valEnum = new ValueEnumeration(value, label);
            enumerationList.add(valEnum);
        }

        public void addEnumerationValue(ValueEnumeration ve) {
            enumerationList.add(ve);
        }

        public void addEnumerationRange(ValueEnumerationRange range) {
            ranges.add(range);
        }

        public boolean hasLabel(String label) {
            for (ValueEnumeration enumeration : enumerationList) {
                if (enumeration.getLabel().equals(label)) {
                    return true;
                }
            }
            for (ValueEnumerationRange range : ranges) {
                if (range.getLabel().equals(label)) {
                    return true;
                }
            }
            return false;
        }

        public ValueEnumeration enumValue(Long key) {
            for (ValueEnumeration ve : enumerationList) {
                if (ve.getValue() == key) {
                    return ve;
                }
            }

            for (ValueEnumerationRange range : ranges) {
                if (range.isValueInRange(key)) {
                    return new ValueEnumeration(key, range.getLabel());
                }
            }
            
            return null;
        }

        public ValueEnumeration enumValue(String label) {
            for (ValueEnumeration enumeration : enumerationList) {
                if (enumeration.getLabel().equals(label)) {
                    return enumeration;
                }
            }
            return null;
        }
    }
}
