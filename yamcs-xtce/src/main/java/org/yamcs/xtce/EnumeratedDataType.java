package org.yamcs.xtce;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;

public class EnumeratedDataType extends BaseDataType {
    private static final long serialVersionUID = 2L;
    String initialValue;

    protected HashMap<Long, ValueEnumeration> enumeration = new HashMap<>();

    protected List<ValueEnumeration> enumerationList = new ArrayList<>();
    protected List<ValueEnumerationRange> ranges = new ArrayList<>();

    EnumeratedDataType(Builder<?> builder) {
        super(builder);
        this.enumerationList = builder.enumerationList;
        this.ranges = builder.ranges;

        if (builder.baseType instanceof EnumeratedDataType) {
            EnumeratedDataType baseType = (EnumeratedDataType) builder.baseType;
            if (builder.enumerationList.isEmpty()) {
                enumerationList.addAll(baseType.enumerationList);
            }
            if (builder.ranges.isEmpty()) {
                ranges.addAll(baseType.ranges);
            }
        }

        for (ValueEnumeration ve : enumerationList) {
            enumeration.put(ve.value, ve);
        }
        setInitialValue(builder);
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
        this.initialValue = t.initialValue;
    }

    @Override
    protected void setInitialValue(Object initialValue) {
        this.initialValue = convertType(initialValue);
    }

    @Override
    public String getInitialValue() {
        return initialValue;
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

    public String calibrate(long raw) {
        ValueEnumeration v = enumeration.get(raw);
        if (v != null) {
            return v.label;
        }

        if (ranges != null) {
            for (ValueEnumerationRange range : ranges) {
                if (range.isValueInRange(raw)) {
                    return range.label;
                }
            }
        }
        return "UNDEF";
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

    @Override
    public String convertType(Object value) {
        if (value instanceof String) {
            return (String) value;
        } else if (value instanceof Number) {
            ValueEnumeration e = enumValue(((Number) value).longValue());
            if (e != null) {
                return e.label;
            } else {
                throw new IllegalArgumentException("Cannot find enumeration for number '" + value + "'");
            }
        } else {
            throw new IllegalArgumentException("Cannot convert value of type '" + value.getClass() + "'");
        }
    }

    @Override
    public Type getValueType() {
        return Value.Type.ENUMERATED;
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

        public T addEnumerationValue(long value, String label) {
            ValueEnumeration valEnum = new ValueEnumeration(value, label);
            enumerationList.add(valEnum);
            return self();
        }

        public T addEnumerationValue(ValueEnumeration ve) {
            enumerationList.add(ve);
            return self();
        }

        public T addEnumerationRange(ValueEnumerationRange range) {
            ranges.add(range);
            return self();
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

        public List<ValueEnumeration> getValueEnumerationList() {
            return Collections.unmodifiableList(enumerationList);
        }

        public List<ValueEnumerationRange> getValueEnumerationRangeList() {
            return Collections.unmodifiableList(ranges);
        }
    }
}
