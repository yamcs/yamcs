package org.yamcs.xtce;

public abstract class BaseTimeDataType extends NameDescription {
    private static final long serialVersionUID = 1L;
    
    protected DataEncoding encoding;

    BaseTimeDataType(String name){
	super(name);
    }

    /**
     * creates a shallow copy of t
     * @param t
     */
    protected BaseTimeDataType(BaseTimeDataType t) {
        super(t);    
        this.encoding = t.encoding;
    }
    
    public DataEncoding getEncoding() {
	return encoding;
    }

    public void setEncoding(DataEncoding encoding) {
	this.encoding = encoding;
    }

    public abstract Object parseString(String stringValue);
    
    public Object parseStringForRawValue(String stringValue) {
        return encoding.parseString(stringValue);
    }
}
