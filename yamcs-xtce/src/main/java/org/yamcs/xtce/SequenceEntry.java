package org.yamcs.xtce;

import java.io.Serializable;

/**
 * An abstract type used by sequence containers.  
 * An entry contains a location in the container.  The location may be either fixed or dynamic, 
 * absolute (to the start or end of the enclosing container, or relative (to either the previous or subsequent entry).
   Entries may also repeat.
 * @author nm
 *
 */
public abstract class SequenceEntry implements Serializable, Comparable<SequenceEntry> {
	private static final long serialVersionUID=200805131551L;
	
	//this is either SequenceContainer or MetaCommandContainer
	protected Container container;
	

	/**
	 * if the referenceLocation is containerStart, then this is number of bits from the start
	 * for previousEntry, this is the number of bits from where the previous entry ends
	 * 
	 */
	protected int locationInContainerInBits = 0;
	
	
	/**	this is the index in the ArrayList of the Container from which this sequence entry is part
	 *  is used for sorting and for finding the parameter before or after this one.
	 */
	int index; 
	
	/**
	 * The location may be relative to the start of the container (containerStart), 
	 * or relative to the end of the previous entry (previousEntry)
	 */
	public enum ReferenceLocationType {containerStart, previousEntry};
	ReferenceLocationType referenceLocation = ReferenceLocationType.previousEntry;
	/**
	 * May be used when this entry repeats itself in the sequence container.  
	 * If null, the entry does not repeat.
	 */
	Repeat repeatEntry=null;
	
	public SequenceEntry(int position, Container container, int locationInContainerInBits, ReferenceLocationType location) {
		this.container=container;
		this.locationInContainerInBits=locationInContainerInBits;
		this.referenceLocation=location;
		this.index=position;
	}

	public void setSequenceContainer(SequenceContainer container) {
	    this.container = container;
	}
	
	public void setIndex(int index) {
	    this.index = index;
	}
	
	public Container getContainer() {
		return container;
	}

	public SequenceContainer getSequenceContainer() {
		if(container instanceof SequenceContainer) {
			return (SequenceContainer) container;
		} else {
			return null;
		}
	}
	
	public void setLocationInContainerInBits( int locationInBits ) {
	    locationInContainerInBits = locationInBits;
	}

    public int getLocationInContainerInBits() {
        return locationInContainerInBits;
    }

	public void setReferenceLocation(ReferenceLocationType type) {
	    this.referenceLocation = type;
	}
	
	/**
	 * @param se 
	 * @return the difference in indexes
	 */
	@Override
    public int compareTo(SequenceEntry se) {
		return index-se.index;
	}
	
    public ReferenceLocationType getReferenceLocation() {
        return referenceLocation;
    }

    public int getIndex() {
        return index;
    }

    public Repeat getRepeatEntry() {
        return repeatEntry;
    }

    public void setRepeatEntry(Repeat repeat) {
        repeatEntry=repeat;
    }
}
