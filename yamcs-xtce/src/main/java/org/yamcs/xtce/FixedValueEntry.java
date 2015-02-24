package org.yamcs.xtce;

/**
 * 
 * Holds an optional attributes name, bitOrder, byteOrderList, 
 * required attributes binaryValue, sizeInBits and optional LocationInContainerInBits, RepeatEntry and IncludeCondition.
 * 
 * @author nm
 */
public class FixedValueEntry extends SequenceEntry {
	private static final long serialVersionUID = 1L;
	
	// An optional name, this name cannot be NameReferenced and is only a form of documentation. 
	final String name;
	
	//The fixed value
	final byte[] binaryValue;
	//the size in bits of the value - this should be not more than the length in bits of the binaryValue
	final int sizeInBits;
	
	public FixedValueEntry(int position, SequenceContainer container, int locationInContainerInBits, ReferenceLocationType location, String name, byte[] binaryValue, int sizeInBits) {
		super(position, container, locationInContainerInBits, location);
		this.name = name;
		this.binaryValue = binaryValue;
		this.sizeInBits = sizeInBits;
	}
	
    
    @Override
    public String toString() {
        return "ParameterEntry position:"+getIndex()+", container:"+container.getName()+
            " locationInContainer:"+getLocationInContainerInBits()+" from:"+getReferenceLocation()+
            ((getRepeatEntry()!=null)?", repeatEntry: ("+getRepeatEntry()+")":"");
    }
}