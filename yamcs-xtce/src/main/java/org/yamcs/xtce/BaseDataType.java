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

    /**
     * creates a shallow copy of t
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

    public void setEncoding(DataEncoding encoding) {
	this.encoding = encoding;
    }

    public List<UnitType> getUnitSet() {
	return unitSet;
    }

    public void addUnit(UnitType unit) {
	unitSet.add(unit);
    }

    public void addAllUnits(Collection<UnitType> units) {
	unitSet.addAll(units);
    }
    
    public abstract Object parseString(String stringValue);
    
    public Object parseStringForRawValue(String stringValue) {
        return encoding.parseString(stringValue);
    }
}
