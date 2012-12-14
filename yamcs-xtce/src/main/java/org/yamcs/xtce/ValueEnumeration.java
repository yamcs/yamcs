package org.yamcs.xtce;

import java.io.Serializable;

public class ValueEnumeration implements Serializable {
	private static final long serialVersionUID = 2011023231432L;
	long value;
	
	String label;
	public ValueEnumeration(long value, String label) {
		this.value=value;
		this.label=label;
	}
	
	public long getValue() {
		return value;
	}
	public String getLabel() {
		return label;
	}
	
	@Override
    public String toString(){
		return "("+value+"="+label+")"; 
	}
}