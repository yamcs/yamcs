package org.yamcs.xtce;

import java.io.Serializable;

/**
 * 
 * An abstract type used by sequence containers.
 * <p>
 * An entry contains a location in the container. The location may be either fixed or dynamic,
 * absolute (to the start or end of the enclosing container, or relative (to either the previous or subsequent entry).
 * <p>
 * Entries may also repeat.
 * <p>
 * These entries have an index which is defining the order of the entry in the container.
 * 
 * @author nm
 *
 */
public abstract class SequenceEntry implements Serializable, Comparable<SequenceEntry> {
    private static final long serialVersionUID = 3L;

    // this is either SequenceContainer or MetaCommandContainer
    protected Container container;

    /**
     * if the referenceLocation is containerStart, then this is number of bits from the start
     * for previousEntry, this is the number of bits from where the previous entry ends
     * 
     */
    protected int locationInContainerInBits = 0;

    /**
     * this is the index in the ArrayList of the Container from which this sequence entry is part
     * is used for sorting and for finding the parameter before or after this one.
     */
    int index;

    /**
     * The location may be relative to the start of the container (containerStart),
     * or relative to the end of the previous entry (previousEntry)
     */
    public enum ReferenceLocationType {
        CONTAINER_START("containerStart"), PREVIOUS_ENTRY("previousEntry");
        final String xtceName;
        ReferenceLocationType(String xtceName) {
            this.xtceName = xtceName;
        }

        public String xtceName() {
            return xtceName;
        }
    };

    ReferenceLocationType referenceLocation = ReferenceLocationType.PREVIOUS_ENTRY;
    /**
     * May be used when this entry repeats itself in the sequence container.
     * If null, the entry does not repeat.
     */
    Repeat repeatEntry = null;
    
    /**
     * This entry will only be included in the sequence when this condition is true. 
     * If no IncludeCondition is given, then it is will be included. A parameter that 
     * is not included will be treated as if it did not exist in the sequence at all.
     */
    private MatchCriteria includeCondition = null;

    public SequenceEntry() {
        
    }
    public SequenceEntry(int locationInContainerInBits, ReferenceLocationType location) {
        this.locationInContainerInBits = locationInContainerInBits;
        this.referenceLocation = location;
    }

    void setContainer(Container container) {
        this.container = container;
    }

    void setIndex(int index) {
        this.index = index;
    }

    public Container getContainer() {
        return container;
    }

    public SequenceContainer getSequenceContainer() {
        if (container instanceof SequenceContainer) {
            return (SequenceContainer) container;
        } else {
            return null;
        }
    }

    public void setLocationInContainerInBits(int locationInBits) {
        locationInContainerInBits = locationInBits;
    }

    public int getLocationInContainerInBits() {
        return locationInContainerInBits;
    }

    public void setReferenceLocation(ReferenceLocationType type) {
        this.referenceLocation = type;
    }

    
    /**
     * Set the location of this entry in the container. 
     * 
     * @param type - where to count the bits from
     * @param locationInBits - number of bits to count
     */
    public void setLocation(ReferenceLocationType type, int locationInBits) {
        this.referenceLocation = type;
        this.locationInContainerInBits = locationInBits;
    }
    /**
     * @param se
     * @return the difference in indexes
     */
    @Override
    public int compareTo(SequenceEntry se) {
        return index - se.index;
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
        repeatEntry = repeat;
    }
    
    public MatchCriteria getIncludeCondition() {
        return includeCondition;
    }
    public void setIncludeCondition(MatchCriteria includeCondition) {
        this.includeCondition = includeCondition;
    }
}
