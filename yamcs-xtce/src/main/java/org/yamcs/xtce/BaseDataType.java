package org.yamcs.xtce;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class BaseDataType extends NameDescription {
	private static final long serialVersionUID = 200805131551L;
	List<UnitType> unitSet = new ArrayList<UnitType>();
	DataEncoding encoding;
	
	BaseDataType(String name){
		super(name);
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
}
