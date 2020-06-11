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
    protected Object initialValue;

    BaseDataType(String name) {
        super(name);
    }

    BaseDataType(Builder<?> builder) {
        super(builder);
        this.unitSet = builder.unitSet;
        this.encoding = builder.encoding;
        //we don't do the initialValue here because it is being converted in sub classes
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
        this.initialValue = t.initialValue;
    }

    public DataEncoding getEncoding() {
        return encoding;
    }

    public List<UnitType> getUnitSet() {
        return unitSet;
    }

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
        protected String initialValue;

        public Builder() {
        }
        
        public Builder(BaseDataType baseType) {
            super(baseType);
            this.unitSet = baseType.unitSet;
            this.encoding = baseType.encoding;
            if(baseType.initialValue != null) {
                this.initialValue = baseType.initialValue.toString();
            }
        }
        
        
        public void setInitialValue(String initialValue) {
            this.initialValue = initialValue;
        }

        public void setEncoding(DataEncoding dataEncoding) {
            this.encoding = dataEncoding;
        }

        public void addAllUnits(Collection<UnitType> units) {
            unitSet.addAll(units);
        }

        public void addUnit(UnitType unit) {
            unitSet.add(unit);
        }

        public DataEncoding getEncoding() {
            return encoding;
        }
        
        public boolean isResolved() {
            return true;
        }
    }
}
