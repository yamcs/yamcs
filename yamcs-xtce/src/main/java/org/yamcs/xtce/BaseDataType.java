package org.yamcs.xtce;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Base class for all simple XTCE types - tha is all types except {@link AggregateDataType} and {@link ArrayDataType}
 *
 * @author nm
 *
 */
public abstract class BaseDataType extends NameDescription implements DataType {
    private static final long serialVersionUID = 3L;
    List<UnitType> unitSet = new ArrayList<>();
    protected DataEncoding encoding;

    BaseDataType(String name) {
        super(name);
    }

    BaseDataType(Builder<?> builder) {
        super(builder);
        this.unitSet = builder.unitSet;
        this.encoding = builder.encoding;

        if (builder.baseType != null) {
            BaseDataType baseType = builder.baseType;

            if (this.encoding == null && baseType.encoding != null) {
                this.encoding = baseType.encoding;
            }
            if (this.unitSet == null && baseType.unitSet != null) {
                this.unitSet = baseType.unitSet;
            }
        }
    }

    /**
     * creates a shallow copy of t
     * 
     * @param t
     */
    protected BaseDataType(BaseDataType t) {
        super(t);
        this.unitSet = t.unitSet;
        this.encoding = t.encoding;
    }

    public DataEncoding getEncoding() {
        return encoding;
    }

    public List<UnitType> getUnitSet() {
        return unitSet;
    }

    protected void setInitialValue(Builder<?> builder) {
        if (builder.initialValue != null) {
            setInitialValue(builder.initialValue);
        } else if (builder.baseType != null && builder.baseType.getInitialValue() != null) {
            setInitialValue(builder.baseType.getInitialValue());
        }
    }

    protected abstract void setInitialValue(Object initialValue);

    /**
     * Used to parse string such as an initial value
     * 
     * @param stringValue
     * @return
     */
    public abstract Object parseString(String stringValue);

    public String toString(Object o) {
        return o.toString();
    }

    public Object parseStringForRawValue(String stringValue) {
        return encoding.parseString(stringValue);
    }

    public abstract static class Builder<T extends Builder<T>> extends NameDescription.Builder<T>
            implements DataType.Builder<T> {
        List<UnitType> unitSet = new ArrayList<>();
        private DataEncoding encoding;
        protected Object initialValue;
        protected BaseDataType baseType;

        public Builder() {
        }

        public Builder(BaseDataType baseType) {
            super(baseType);
            this.unitSet = baseType.unitSet;
            this.encoding = baseType.encoding;
            this.initialValue = baseType.getInitialValue();
        }

        public T setInitialValue(byte[] initialValue) {
            this.initialValue = initialValue;
            return self();
        }

        public T setInitialValue(String initialValue) {
            this.initialValue = initialValue;
            return self();
        }

        public T setEncoding(DataEncoding dataEncoding) {
            this.encoding = dataEncoding;
            return self();
        }

        public T addAllUnits(Collection<UnitType> units) {
            unitSet.addAll(units);
            return self();
        }

        public T addUnit(UnitType unit) {
            unitSet.add(unit);
            return self();
        }

        public DataEncoding getEncoding() {
            return encoding;
        }

        public void setBaseType(BaseDataType type) {
            this.baseType = type;
        }
    }
}
