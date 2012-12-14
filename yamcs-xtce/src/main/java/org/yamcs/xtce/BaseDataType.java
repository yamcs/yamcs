package org.yamcs.xtce;

public abstract class BaseDataType extends NameDescription {
	private static final long serialVersionUID = 200805131551L;
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
}
